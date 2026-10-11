package com.example.encryptedvault.crypto

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-encrypted backup stream (ADR-007).
 *
 * Header: magic `EVBK`, version byte, PBKDF2 salt, base nonce, iteration count.
 * Body: records of `[final flag][length][AES-256-GCM ciphertext]`. Each chunk's nonce is the base nonce XOR its index, and its
 * AAD is the index plus the final flag, so reordering, dropping, or truncating chunks fails authentication.
 */
object BackupCrypto {
    const val MIN_PASSPHRASE_LENGTH = 12

    private val MAGIC = "EVBK".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val ITERATIONS = 600_000
    private const val CHUNK_BYTES = 1 shl 20
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / 8
    private const val AES_BITS = 256

    /** Writes the header to [out] and returns a stream that encrypts what is written to it. Closing it writes the final chunk and closes [out]. */
    fun encrypting(out: OutputStream, passphrase: CharArray, random: SecureRandom = SecureRandom()): OutputStream {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val baseNonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val data = DataOutputStream(out)
        data.write(MAGIC)
        data.writeByte(VERSION.toInt())
        data.write(salt)
        data.write(baseNonce)
        data.writeInt(ITERATIONS)
        data.flush()
        return EncryptingStream(data, deriveKey(passphrase, salt, ITERATIONS), baseNonce)
    }

    /** Reads and checks the header of [input] and returns a stream of the decrypted body. Any failure throws [IOException]. */
    fun decrypting(input: InputStream, passphrase: CharArray): InputStream {
        val data = DataInputStream(input)
        val magic = ByteArray(MAGIC.size).also { data.readFully(it) }
        if (!magic.contentEquals(MAGIC)) throw IOException("Not a vault backup file")
        val version = data.readByte()
        if (version != VERSION) throw IOException("Unsupported backup version $version")
        val salt = ByteArray(SALT_BYTES).also { data.readFully(it) }
        val baseNonce = ByteArray(NONCE_BYTES).also { data.readFully(it) }
        val iterations = data.readInt()
        if (iterations !in 1..ITERATIONS * 4) throw IOException("Implausible iteration count")
        return DecryptingStream(data, deriveKey(passphrase, salt, iterations), baseNonce)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, AES_BITS)
        val bytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
    }

    private fun nonceFor(base: ByteArray, index: Long): ByteArray {
        val nonce = base.copyOf()
        val counter = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(index).array()
        for (i in counter.indices) {
            val at = NONCE_BYTES - counter.size + i
            nonce[at] = (nonce[at].toInt() xor counter[i].toInt()).toByte()
        }
        return nonce
    }

    private fun aad(index: Long, last: Boolean): ByteArray =
        ByteBuffer.allocate(Long.SIZE_BYTES + 1).putLong(index).put(if (last) 1 else 0).array()

    private fun cipher(mode: Int, key: SecretKeySpec, baseNonce: ByteArray, index: Long, last: Boolean): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG_BITS, nonceFor(baseNonce, index)))
            updateAAD(aad(index, last))
        }

    private class EncryptingStream(
        private val out: DataOutputStream,
        private val key: SecretKeySpec,
        private val baseNonce: ByteArray,
    ) : OutputStream() {
        private val buffer = ByteArrayOutputStream(CHUNK_BYTES)
        private var index = 0L
        private var closed = false

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            check(!closed) { "Stream is closed" }
            var position = off
            var remaining = len
            while (remaining > 0) {
                val take = minOf(CHUNK_BYTES - buffer.size(), remaining)
                buffer.write(b, position, take)
                position += take
                remaining -= take
                if (buffer.size() == CHUNK_BYTES) sealChunk(last = false)
            }
        }

        override fun flush() = out.flush()

        override fun close() {
            if (closed) return
            closed = true
            sealChunk(last = true)
            out.close()
        }

        private fun sealChunk(last: Boolean) {
            val sealed = cipher(Cipher.ENCRYPT_MODE, key, baseNonce, index, last).doFinal(buffer.toByteArray())
            out.writeByte(if (last) 1 else 0)
            out.writeInt(sealed.size)
            out.write(sealed)
            buffer.reset()
            index++
        }
    }

    private class DecryptingStream(
        private val input: DataInputStream,
        private val key: SecretKeySpec,
        private val baseNonce: ByteArray,
    ) : InputStream() {
        private var chunk = ByteArray(0)
        private var position = 0
        private var index = 0L
        private var finished = false

        override fun read(): Int {
            if (!fill()) return -1
            return chunk[position++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (!fill()) return -1
            val take = minOf(len, chunk.size - position)
            System.arraycopy(chunk, position, b, off, take)
            position += take
            return take
        }

        private fun fill(): Boolean {
            while (position >= chunk.size) {
                if (finished) return false
                openChunk()
            }
            return true
        }

        private fun openChunk() {
            val last = when (input.readByte().toInt()) {
                0 -> false
                1 -> true
                else -> throw IOException("Corrupt backup chunk")
            }
            val length = input.readInt()
            if (length !in TAG_BYTES..CHUNK_BYTES + TAG_BYTES) throw IOException("Corrupt backup chunk length")
            val sealed = ByteArray(length)
            input.readFully(sealed)
            chunk = try {
                cipher(Cipher.DECRYPT_MODE, key, baseNonce, index, last).doFinal(sealed)
            } catch (e: GeneralSecurityException) {
                throw IOException("Wrong passphrase or corrupted backup", e)
            }
            position = 0
            index++
            if (last) {
                finished = true
                if (input.read() != -1) throw IOException("Unexpected data after the end of the backup")
            }
        }
    }
}
