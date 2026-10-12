package com.example.encryptedvault

import android.content.Context
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.encryptedvault.crypto.Bip39
import com.example.encryptedvault.crypto.KeyHierarchy
import com.example.encryptedvault.crypto.PinVerifier
import com.example.encryptedvault.crypto.UnlockAttempts
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.spec.SecretKeySpec

/**
 * Exercises [KeyHierarchy] against the real Android Keystore (JVM tests cannot): setup, unlock, recovery, PIN change,
 * duress PIN, lockout, crypto-erase and the transparent upgrade of a pre-v2 vault.
 */
@RunWith(AndroidJUnit4::class)
class KeyHierarchyTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var keys: KeyHierarchy
    private lateinit var bip39: Bip39
    private val pin = "483915".toCharArray()
    private val other = "729164".toCharArray()

    @Before fun freshState() {
        keys = KeyHierarchy(context)
        keys.destroyAllKeyMaterial()
        bip39 =
            Bip39(
                context.assets
                    .open("bip39_english.txt")
                    .bufferedReader()
                    .readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() },
            )
    }

    @After fun cleanUp() = keys.destroyAllKeyMaterial()

    @Test fun setUpThenUnlockWithTheSamePin() {
        val words = keys.setUp(pin, bip39)
        assertEquals(24, words.size)
        assertTrue(keys.isSetUp())
        assertNotNull(keys.unlockWithPin(pin))
    }

    @Test fun wrongPinDoesNotUnlock() {
        keys.setUp(pin, bip39)
        assertNull(keys.unlockWithPin(other))
    }

    @Test fun unlockIsRepeatableAndReturnsTheSameMasterKey() {
        keys.setUp(pin, bip39)
        assertArrayEquals(keys.unlockWithPin(pin)!!.encoded, keys.unlockWithPin(pin)!!.encoded)
    }

    @Test fun pinLengthIsStoredForAutoSubmit() {
        keys.setUp(pin, bip39)
        assertEquals(6, keys.pinLength())
        keys.changePin(pin, "7291640".toCharArray())
        assertEquals(7, keys.pinLength())
    }

    @Test fun recoveryPhraseRestoresTheSameMasterKeyUnderANewPin() {
        val words = keys.setUp(pin, bip39)
        val original = keys.unlockWithPin(pin)!!.encoded
        val recovered = keys.recover(words, other, bip39)
        assertArrayEquals(original, recovered!!.encoded)
        assertNotNull(keys.unlockWithPin(other))
        assertNull(keys.unlockWithPin(pin))
    }

    @Test fun invalidPhraseIsRejected() {
        keys.setUp(pin, bip39)
        assertNull(keys.recover(List(24) { "abandon" }, other, bip39))
    }

    @Test fun changePinKeepsTheMasterKey() {
        keys.setUp(pin, bip39)
        val before = keys.unlockWithPin(pin)!!.encoded
        assertTrue(keys.changePin(pin, other))
        assertArrayEquals(before, keys.unlockWithPin(other)!!.encoded)
        assertNull(keys.unlockWithPin(pin))
    }

    @Test fun changePinNeedsTheCurrentPin() {
        keys.setUp(pin, bip39)
        assertFalse(keys.changePin(other, "135792".toCharArray()))
    }

    // ---- Duress PIN ----

    @Test fun duressPinIsRecognisedAndNormalPinIsNot() {
        keys.setUp(pin, bip39)
        assertNull(keys.setDuressPin(pin, other))
        assertTrue(keys.hasDuressPin())
        assertTrue(keys.isDuressPin(other))
        assertFalse(keys.isDuressPin(pin))
        assertFalse(keys.isDuressPin("135792".toCharArray()))
    }

    @Test fun duressPinDoesNotUnlockTheVault() {
        keys.setUp(pin, bip39)
        keys.setDuressPin(pin, other)
        assertNull(keys.unlockWithPin(other))
    }

    @Test fun duressPinIsRefusedWhenItEqualsTheRealPin() {
        keys.setUp(pin, bip39)
        assertNotNull(keys.setDuressPin(pin, pin))
        assertFalse(keys.hasDuressPin())
    }

    @Test fun duressPinIsRefusedWhenTheLengthDiffers() {
        keys.setUp(pin, bip39)
        assertNotNull(keys.setDuressPin(pin, "7291640".toCharArray()))
    }

    @Test fun duressPinNeedsTheRealPin() {
        keys.setUp(pin, bip39)
        assertEquals("Your current PIN is wrong", keys.setDuressPin(other, "135792".toCharArray()))
    }

    @Test fun changePinRefusesTheDuressPin() {
        keys.setUp(pin, bip39)
        keys.setDuressPin(pin, other)
        assertFalse(keys.changePin(pin, other))
    }

    @Test fun changePinRefusesALengthThatWouldBreakTheDuressPin() {
        keys.setUp(pin, bip39)
        keys.setDuressPin(pin, other)
        assertFalse(keys.changePin(pin, "7291641".toCharArray()))
    }

    @Test fun clearingTheDuressPinNeedsTheRealPin() {
        keys.setUp(pin, bip39)
        keys.setDuressPin(pin, other)
        assertFalse(keys.clearDuressPin(other))
        assertTrue(keys.hasDuressPin())
        assertTrue(keys.clearDuressPin(pin))
        assertFalse(keys.hasDuressPin())
        assertFalse(keys.isDuressPin(other))
    }

    // ---- Lockout ----

    @Test fun lockoutStartsAfterTheFreeTriesAndResetsOnSuccess() {
        keys.setUp(pin, bip39)
        repeat(UnlockAttempts.FREE_TRIES - 1) { keys.attempts.recordFailure() }
        assertEquals(0L, keys.attempts.lockedForMs())
        keys.attempts.recordFailure()
        assertTrue(keys.attempts.lockedForMs() > 0)
        keys.attempts.resetFailures()
        assertEquals(0L, keys.attempts.lockedForMs())
        assertEquals(0, keys.attempts.failCount())
    }

    // ---- Crypto-erase ----

    @Test fun destroyingKeyMaterialMakesTheVaultUnrecoverable() {
        val words = keys.setUp(pin, bip39)
        keys.destroyAllKeyMaterial()
        assertFalse(keys.isSetUp())
        assertNull(keys.unlockWithPin(pin))
        // The phrase still derives the same master key mathematically, but there is no wrapper and no ciphertext relying on it any more.
        assertEquals(24, words.size)
    }

    // ---- Upgrade of a vault created before the Keystore layer ----

    @Test fun legacyVaultIsUpgradedToTheKeystoreLayerOnFirstUnlock() {
        val master = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        val salt = PinVerifier.newSalt()
        val legacyPinKey = SecretKeySpec(PinVerifier.derive(pin, salt, PinVerifier.LEGACY_ITERATIONS), "AES")
        val prefs = context.getSharedPreferences("vault_keys", Context.MODE_PRIVATE)
        prefs
            .edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("wrapped_pin", Base64.encodeToString(KeyHierarchy.gcmEncrypt(legacyPinKey, master.encoded), Base64.NO_WRAP))
            .putString("wrapped_ks", "obsolete-unauthenticated-wrapper")
            .commit()

        assertArrayEquals(master.encoded, keys.unlockWithPin(pin)!!.encoded)

        assertEquals(2, prefs.getInt("wrap_v", 0))
        assertEquals(PinVerifier.CURRENT_ITERATIONS, prefs.getInt("kdf_iter", 0))
        assertEquals(6, keys.pinLength())
        assertFalse("the unauthenticated wrapper must be removed", prefs.contains("wrapped_ks"))
        assertArrayEquals(master.encoded, keys.unlockWithPin(pin)!!.encoded) // and still works after the upgrade
    }
}
