package com.example.encryptedvault.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Random

class BackupCryptoTest {
    private val pass = "correct horse battery".toCharArray()

    /** One chunk record on the wire: flag byte, length, then the sealed chunk (1 MiB plus the 16-byte tag). */
    private val fullChunkRecord = 1 + 4 + (1 shl 20) + 16
    private val headerBytes = 4 + 1 + 16 + 12 + 4

    private fun encrypt(data: ByteArray, passphrase: CharArray = pass): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypting(out, passphrase).use { it.write(data) }
        return out.toByteArray()
    }

    private fun decrypt(bytes: ByteArray, passphrase: CharArray = pass): ByteArray =
        BackupCrypto.decrypting(ByteArrayInputStream(bytes), passphrase).use { it.readBytes() }

    private fun payload(size: Int) = ByteArray(size).also { Random(7).nextBytes(it) }

    @Test fun roundTripsEmptySmallAndMultiChunkData() {
        for (size in listOf(0, 10, 2_500_000)) {
            val data = payload(size)
            assertArrayEquals("size $size", data, decrypt(encrypt(data)))
        }
    }

    @Test fun wrongPassphraseIsRejected() {
        val bytes = encrypt(payload(100))
        assertThrows(IOException::class.java) { decrypt(bytes, "wrong passphrase!".toCharArray()) }
    }

    @Test fun flippedCiphertextIsRejected() {
        val bytes = encrypt(payload(100))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        assertThrows(IOException::class.java) { decrypt(bytes) }
    }

    @Test fun truncatedBackupIsRejected() {
        val bytes = encrypt(payload(2_500_000))
        // Keep the header and the first (non-final) chunk only: the final chunk is missing.
        val truncated = bytes.copyOf(headerBytes + fullChunkRecord)
        assertThrows(IOException::class.java) { decrypt(truncated) }
    }

    @Test fun reorderedChunksAreRejected() {
        val bytes = encrypt(payload(2_500_000))
        val first = bytes.copyOfRange(headerBytes, headerBytes + fullChunkRecord)
        val second = bytes.copyOfRange(headerBytes + fullChunkRecord, headerBytes + 2 * fullChunkRecord)
        second.copyInto(bytes, headerBytes)
        first.copyInto(bytes, headerBytes + fullChunkRecord)
        assertThrows(IOException::class.java) { decrypt(bytes) }
    }

    @Test fun finalFlagCannotBeForged() {
        val bytes = encrypt(payload(2_500_000))
        val firstFlag = headerBytes
        bytes[firstFlag] = 1 // mark a non-final chunk as final
        assertThrows(IOException::class.java) { decrypt(bytes) }
    }

    @Test fun dataAfterTheFinalChunkIsRejected() {
        val bytes = encrypt(payload(100)) + byteArrayOf(0)
        assertThrows(IOException::class.java) { decrypt(bytes) }
    }

    @Test fun unknownHeaderIsRejected() {
        val bytes = encrypt(payload(100))
        bytes[0] = 'X'.code.toByte()
        assertThrows(IOException::class.java) { decrypt(bytes) }
        val wrongVersion = encrypt(payload(100)).also { it[4] = 2 }
        assertThrows(IOException::class.java) { decrypt(wrongVersion) }
    }
}
