package com.example.encryptedvault.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2 helpers shared by the PIN wrapper and the duress-PIN verifier. Plain JCA, so it runs in JVM unit tests.
 *
 * PBKDF2-HMAC-SHA256 is used because it is available on every supported API level without extra libraries;
 * the iteration count is stored next to the salt so it can be raised later without breaking existing vaults.
 */
object PinVerifier {
    /** Iterations used for newly created PIN material (OWASP guidance for PBKDF2-HMAC-SHA256 is 600k). */
    const val CURRENT_ITERATIONS = 600_000

    /** Iterations used by vaults created before the count was stored. */
    const val LEGACY_ITERATIONS = 310_000

    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    /** A fresh random salt. */
    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    /** Derives 32 bytes from [pin]. The PBEKeySpec password copy is wiped before returning. */
    fun derive(
        pin: CharArray,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** True when [pin] derives exactly [expected]; compared in constant time so timing leaks nothing about how many bytes matched. */
    fun matches(
        pin: CharArray,
        salt: ByteArray,
        iterations: Int,
        expected: ByteArray,
    ): Boolean = MessageDigest.isEqual(derive(pin, salt, iterations), expected)
}
