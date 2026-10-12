package com.example.encryptedvault.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import android.util.Log
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Owns every secret that protects the vault and the only copy of the wrapped master key.
 *
 * Hierarchy:
 * ```
 * recovery phrase (24 words, shown once)  --HKDF-->  master key (AES-256, encrypts all vault data)
 *
 * master key is stored wrapped three ways:
 *   1. PIN wrapper (format v2):  Keystore-AES-GCM( PBKDF2(PIN)-AES-GCM( master ) )
 *        - the inner layer needs the PIN
 *        - the outer layer needs this device's Keystore (StrongBox when present), so a copied
 *          `shared_prefs` file cannot be brute-forced off the device
 *   2. Biometric wrapper: a Keystore key that requires a fresh strong biometric per use
 *   3. Recovery phrase: re-derives the master key directly (never stored)
 * ```
 * Format v1 vaults (PIN layer only, 310k iterations) are upgraded to v2 transparently on the next successful PIN unlock.
 *
 * A second, independent PIN (the duress PIN) is stored only as a salted PBKDF2 verifier; see [isDuressPin].
 *
 * This is one cohesive key-management facade on purpose: the wrap and unwrap order and the migrations from older formats depend
 * on each other, so the functions are kept together rather than spread over several classes.
 */
@Suppress("TooManyFunctions")
class KeyHierarchy(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Wrong-PIN counter and lockout; see [UnlockAttempts]. */
    val attempts = UnlockAttempts(prefs)

    /** True once [setUp] has finished (the PIN wrapper exists). */
    fun isSetUp() = prefs.contains(KEY_WRAPPED_PIN)

    // ---- Setup, unlock, recovery --------------------------------------------------------------

    /**
     * First run: creates a new master key, wraps it under [pin] and returns the recovery words to show ONCE.
     * The phrase itself is never persisted.
     */
    fun setUp(
        pin: CharArray,
        bip39: Bip39,
    ): List<String> {
        val entropy = bip39.newEntropy()
        wrapAll(deriveMaster(entropy), pin)
        return bip39.encode(entropy)
    }

    /**
     * Unlocks with [pin]. Returns the master key, or null when the PIN is wrong (or the wrapper cannot be read).
     * Upgrades a format-v1 vault to v2 after a successful unlock.
     */
    fun unlockWithPin(pin: CharArray): SecretKey? =
        runCatching {
            val version = prefs.getInt(KEY_WRAP_VERSION, 1)
            val salt = b64(prefs.getString(KEY_SALT, null)!!)
            val iterations = prefs.getInt(KEY_ITERATIONS, PinVerifier.LEGACY_ITERATIONS)
            val stored = b64(prefs.getString(KEY_WRAPPED_PIN, null)!!)
            val inner = if (version >= 2) gcmDecrypt(keystoreKey(), stored) else stored
            val master = SecretKeySpec(gcmDecrypt(SecretKeySpec(PinVerifier.derive(pin, salt, iterations), "AES"), inner), "AES")
            if (version < 2 || iterations < PinVerifier.CURRENT_ITERATIONS) wrapAll(master, pin)
            master
        }.getOrNull()

    /** Lockout recovery: re-derives the master key from [phrase] and wraps it under [newPin]. Null when the phrase is invalid. */
    fun recover(
        phrase: List<String>,
        newPin: CharArray,
        bip39: Bip39,
    ): SecretKey? {
        val entropy = bip39.decode(phrase) ?: return null
        val master = deriveMaster(entropy)
        wrapAll(master, newPin)
        return master
    }

    /**
     * Verifies [old] and re-wraps the same master key under [new]. The biometric wrapper is unaffected.
     * Refuses a [new] PIN equal to the duress PIN, so a normal unlock can never be mistaken for a wipe.
     */
    fun changePin(
        old: CharArray,
        new: CharArray,
    ): Boolean {
        if (hasDuressPin() && (new.size != pinLength() || isDuressPin(new))) return false // the duress PIN must keep the same length
        val master = unlockWithPin(old) ?: return false
        wrapAll(master, new)
        return true
    }

    /** Length of the PIN chosen at setup, used for auto-submit on the lock screen; 0 when unknown (legacy vault). */
    fun pinLength(): Int = prefs.getInt(KEY_PIN_LENGTH, 0)

    // ---- Duress PIN ---------------------------------------------------------------------------

    /** True when a duress PIN has been configured. */
    fun hasDuressPin() = prefs.contains(KEY_DURESS_HASH)

    /**
     * Configures the duress PIN. [realPin] must unlock the vault; [duress] must satisfy [PinPolicy.duressProblem].
     * @return null on success, otherwise a message for the user.
     */
    fun setDuressPin(
        realPin: CharArray,
        duress: CharArray,
    ): String? {
        if (unlockWithPin(realPin) == null) return "Your current PIN is wrong"
        PinPolicy.duressProblem(String(realPin), String(duress))?.let { return it }
        val salt = PinVerifier.newSalt()
        val hash = PinVerifier.derive(duress, salt, PinVerifier.CURRENT_ITERATIONS)
        prefs
            .edit()
            .putString(KEY_DURESS_SALT, b64(salt))
            .putString(KEY_DURESS_HASH, b64(hash))
            .putInt(KEY_DURESS_ITERATIONS, PinVerifier.CURRENT_ITERATIONS)
            .commit()
        return null
    }

    /** Removes the duress PIN. Requires the real PIN so a bystander cannot disarm it. */
    fun clearDuressPin(realPin: CharArray): Boolean {
        if (unlockWithPin(realPin) == null) return false
        prefs
            .edit()
            .remove(KEY_DURESS_SALT)
            .remove(KEY_DURESS_HASH)
            .remove(KEY_DURESS_ITERATIONS)
            .commit()
        return true
    }

    /** True when [pin] is the configured duress PIN (constant-time comparison). Always false when none is set. */
    fun isDuressPin(pin: CharArray): Boolean {
        val hash = prefs.getString(KEY_DURESS_HASH, null) ?: return false
        val salt = b64(prefs.getString(KEY_DURESS_SALT, null) ?: return false)
        return PinVerifier.matches(pin, salt, prefs.getInt(KEY_DURESS_ITERATIONS, PinVerifier.CURRENT_ITERATIONS), b64(hash))
    }

    /**
     * Crypto-erase: deletes every wrapper and both Keystore keys. After this returns, all vault ciphertext on
     * disk is permanently unreadable, even before the files themselves are deleted.
     */
    fun destroyAllKeyMaterial() {
        runCatching {
            KeyStore.getInstance(KEYSTORE).apply { load(null) }.apply {
                deleteEntry(KS_ALIAS)
                deleteEntry(BIO_ALIAS)
            }
        }
        prefs.edit().clear().commit()
    }

    // ---- Biometric (fingerprint/face) wrapper -------------------------------------------------
    // A separate Keystore key that requires a fresh strong biometric auth for every use (CryptoObject-bound).
    // A new biometric enrolment invalidates it; we then drop it and fall back to the PIN.

    /** True when a biometric wrapper exists. */
    fun biometricEnabled() = prefs.contains(KEY_WRAPPED_BIO)

    /** Cipher (ENCRYPT) to hand to BiometricPrompt when enabling; finish with [completeEnableBiometric]. */
    fun biometricEnableCipher(): Cipher {
        deleteBioKey()
        return Cipher.getInstance(GCM).apply { init(Cipher.ENCRYPT_MODE, bioKey()) }
    }

    /** Stores the master key encrypted by the biometric-authenticated [authedCipher]. */
    fun completeEnableBiometric(
        authedCipher: Cipher,
        master: SecretKey,
    ) {
        val ct = authedCipher.doFinal(master.encoded)
        prefs.edit().putString(KEY_WRAPPED_BIO, b64(authedCipher.iv + ct)).commit()
    }

    /** Cipher (DECRYPT) for BiometricPrompt on unlock, or null if biometric is off or was invalidated by a new enrolment. */
    fun biometricUnlockCipher(): Cipher? {
        val blob = prefs.getString(KEY_WRAPPED_BIO, null)?.let(::b64) ?: return null
        return try {
            Cipher.getInstance(GCM).apply { init(Cipher.DECRYPT_MODE, bioKey(), GCMParameterSpec(GCM_TAG_BITS, blob, 0, GCM_IV_BYTES)) }
        } catch (e: KeyPermanentlyInvalidatedException) {
            Log.i(TAG, "biometric key invalidated by a new enrolment; biometric unlock turned off (${e.message})")
            disableBiometric()
            null
        } catch (e: GeneralSecurityException) {
            Log.w(TAG, "biometric cipher unavailable: ${e.javaClass.simpleName}")
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "biometric key store not ready: ${e.javaClass.simpleName}")
            null
        }
    }

    /** Finishes a biometric unlock: decrypts the master key with the authenticated [authedCipher]. */
    fun unlockWithBiometric(authedCipher: Cipher): SecretKey? =
        runCatching {
            val blob = b64(prefs.getString(KEY_WRAPPED_BIO, null)!!)
            SecretKeySpec(authedCipher.doFinal(blob, GCM_IV_BYTES, blob.size - GCM_IV_BYTES), "AES")
        }.getOrNull()

    /** Turns biometric unlock off and deletes its Keystore key. */
    fun disableBiometric() {
        prefs.edit().remove(KEY_WRAPPED_BIO).commit()
        deleteBioKey()
    }

    /** Deletes the biometric Keystore key. Errors are ignored: the key may not exist. */
    private fun deleteBioKey() = runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(BIO_ALIAS) }

    /** The auth-gated biometric Keystore key; created on first use (StrongBox first, TEE fallback). */
    private fun bioKey(): SecretKey = keystoreAesKey(BIO_ALIAS, authRequired = true)

    // ---- Internals ----------------------------------------------------------------------------

    /** Writes the v2 PIN wrapper for [master] with a fresh salt and the current iteration count, and drops the obsolete unauthenticated wrapper. */
    private fun wrapAll(
        master: SecretKey,
        pin: CharArray,
    ) {
        val salt = PinVerifier.newSalt()
        val inner = gcmEncrypt(SecretKeySpec(PinVerifier.derive(pin, salt, PinVerifier.CURRENT_ITERATIONS), "AES"), master.encoded)
        prefs
            .edit()
            .putString(KEY_SALT, b64(salt))
            .putString(KEY_WRAPPED_PIN, b64(gcmEncrypt(keystoreKey(), inner)))
            .putInt(KEY_ITERATIONS, PinVerifier.CURRENT_ITERATIONS)
            .putInt(KEY_WRAP_VERSION, 2)
            .putInt(KEY_PIN_LENGTH, pin.size)
            .remove(KEY_WRAPPED_KS_OBSOLETE)
            .commit()
    }

    /** HKDF-SHA256 (extract + one expand block) of the phrase entropy into the 256-bit master key. */
    private fun deriveMaster(entropy: ByteArray): SecretKey {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(ByteArray(AES_KEY_BYTES), HMAC))
        val prk = mac.doFinal(entropy)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        return SecretKeySpec(mac.doFinal("vault-master-v1".toByteArray() + byteArrayOf(1)), "AES")
    }

    /** The device-bound outer wrapping key (no user-auth requirement; its job is to pin PIN guesses to this device). */
    private fun keystoreKey(): SecretKey = keystoreAesKey(KS_ALIAS, authRequired = false)

    /** Returns the AES-256-GCM key stored under [alias], generating it (StrongBox first, graceful TEE fallback) when missing. */
    private fun keystoreAesKey(
        alias: String,
        authRequired: Boolean,
    ): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }

        /** Creates the key in StrongBox when [strongBox] is true, otherwise in the TEE. */
        fun generate(strongBox: Boolean): SecretKey {
            val spec =
                KeyGenParameterSpec
                    .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(AES_KEY_BITS)
                    .setIsStrongBoxBacked(strongBox)
                    .apply { if (authRequired) setUserAuthenticationRequired(true).setInvalidatedByBiometricEnrollment(true) }
                    .build()
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
        }
        return try {
            generate(true)
        } catch (e: StrongBoxUnavailableException) {
            Log.i(TAG, "StrongBox unavailable (${e.message}); using the TEE")
            generate(false)
        }
    }

    companion object {
        private const val TAG = "KeyHierarchy"
        private const val PREFS = "vault_keys"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val GCM = "AES/GCM/NoPadding"

        /** AES-GCM nonce length in bytes: 96 bits. */
        private const val GCM_IV_BYTES = 12

        /** AES-GCM authentication tag length in bits. */
        private const val GCM_TAG_BITS = 128
        private const val AES_KEY_BITS = 256
        private const val AES_KEY_BYTES = 32
        private const val HMAC = "HmacSHA256"
        private const val KEY_SALT = "salt"
        private const val KEY_WRAPPED_PIN = "wrapped_pin"
        private const val KEY_WRAP_VERSION = "wrap_v"
        private const val KEY_ITERATIONS = "kdf_iter"
        private const val KEY_PIN_LENGTH = "pin_len"
        private const val KEY_DURESS_SALT = "duress_salt"
        private const val KEY_DURESS_HASH = "duress_hash"
        private const val KEY_DURESS_ITERATIONS = "duress_iter"
        private const val KEY_WRAPPED_BIO = "wrapped_bio"

        /** Pre-v2 vaults also stored the master key under an unauthenticated Keystore key; nothing reads it and it is removed on upgrade. */
        private const val KEY_WRAPPED_KS_OBSOLETE = "wrapped_ks"
        private const val KS_ALIAS = "vault_wrap_key"
        private const val BIO_ALIAS = "vault_bio_key"

        /** Base64 without line wraps, for storing bytes in preferences. */
        private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

        /** Inverse of the byte-array overload. */
        private fun b64(s: String) = Base64.decode(s, Base64.NO_WRAP)

        /** AES-256-GCM encrypt; output layout is `iv(12) || ciphertext || tag(16)`. */
        fun gcmEncrypt(
            key: SecretKey,
            plain: ByteArray,
        ): ByteArray {
            val c = Cipher.getInstance(GCM).apply { init(Cipher.ENCRYPT_MODE, key) }
            return c.iv + c.doFinal(plain)
        }

        /** Inverse of [gcmEncrypt]; throws when the tag does not verify (wrong key or tampered data). */
        fun gcmDecrypt(
            key: SecretKey,
            blob: ByteArray,
        ): ByteArray {
            val c = Cipher.getInstance(GCM)
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, blob, 0, GCM_IV_BYTES))
            return c.doFinal(blob, GCM_IV_BYTES, blob.size - GCM_IV_BYTES)
        }
    }
}
