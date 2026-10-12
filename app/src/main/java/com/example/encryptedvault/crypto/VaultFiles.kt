package com.example.encryptedvault.crypto

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts and decrypts vault files and commits them atomically (tmp -> fsync -> rename), so a killed process never leaves a half-written file.
 *
 * Two on-disk formats exist; [decryptStream] reads both, [encryptAtomic] only writes v2:
 *
 * **v2 (current), streaming and seekable**
 * ```
 * "VLTSTRM2"            8-byte magic
 * Tink AES-256-GCM-HKDF stream: header (random salt + nonce prefix), then independently authenticated 1 MiB segments
 * ```
 * Every segment carries its own GCM tag and the last one is marked final, so a flipped bit, a swapped segment or a
 * truncated file is detected. Memory use is one segment regardless of file size, and a segment can be decrypted
 * on its own, which is what lets video playback seek without writing plaintext anywhere ([openSeekable]).
 * The associated data is the file name, so ciphertext moved to a different item's name fails to decrypt.
 * The stream key is HKDF-derived from the master key, never the master key itself.
 *
 * **v1 (legacy)**: `iv(12) || AES-GCM ciphertext || tag` as one message. The tag is only checked at the end, and the Android provider
 * buffers it all in memory, which is why large files need v2. v1 files are upgraded in place by [upgradeToStreaming].
 */
object VaultFiles {
    private const val CHUNK = 64 * 1024
    private val MAGIC = "VLTSTRM2".toByteArray(Charsets.US_ASCII)

    /** Size of one authenticated ciphertext segment. */
    const val SEGMENT_BYTES = 1 shl 20

    /** Which on-disk format a file uses. */
    enum class Format { LEGACY, STREAMING }

    // ---- Hashing ------------------------------------------------------------------------------

    /** Lower-case hex SHA-256 of a file, streamed (used for dedup and as the on-disk base name). */
    fun sha256(file: File): String = file.inputStream().use { sha256(it) }

    /** Lower-case hex SHA-256 of everything in [s]; the stream is read to the end but not closed. */
    fun sha256(s: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val b = ByteArray(CHUNK)
        while (true) {
            val n = s.read(b)
            if (n < 0) break
            md.update(b, 0, n)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // ---- Writing ------------------------------------------------------------------------------

    /**
     * Encrypts [src] into [dest] in the streaming v2 format. Never leaves a partial [dest]: data goes to `<dest>.tmp`,
     * is fsynced, then renamed over [dest]. The temp file is removed on any failure.
     * @throws IllegalStateException when the final rename fails.
     */
    fun encryptAtomic(
        key: SecretKey,
        src: InputStream,
        dest: File,
    ) {
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        try {
            FileOutputStream(tmp).use { fos ->
                fos.write(MAGIC)
                // Closing Tink's stream writes the final segment and would close fos; shield it so we can fsync first.
                val shielded =
                    object : FilterOutputStream(fos) {
                        override fun write(
                            b: ByteArray,
                            off: Int,
                            len: Int,
                        ) = out.write(b, off, len)

                        override fun close() = flush()
                    }
                aead(key).newEncryptingStream(shielded, associatedData(dest)).use { out -> src.copyTo(out, CHUNK) }
                fos.fd.sync()
            }
            check(tmp.renameTo(dest)) { "atomic rename failed" }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    /**
     * Rewrites a legacy v1 [file] in the streaming format, in place and atomically. A no-op for v2 files.
     * Needed once per old video before it can be played without a plaintext temp file.
     */
    fun upgradeToStreaming(
        key: SecretKey,
        file: File,
    ) {
        if (formatOf(file) == Format.STREAMING) return
        decryptStream(key, file).use { encryptAtomic(key, it, file) }
    }

    // ---- Reading ------------------------------------------------------------------------------

    /** Detects the format by its magic prefix. A v1 file starts with a random IV, so a collision has probability 2^-64. */
    fun formatOf(file: File): Format = FileInputStream(file).use { fis -> if (hasMagic(fis)) Format.STREAMING else Format.LEGACY }

    /**
     * Opens [file] for sequential decryption in either format. Authentication failures surface as an IOException while reading.
     * Closing the returned stream closes the file.
     */
    fun decryptStream(
        key: SecretKey,
        file: File,
    ): InputStream {
        val fis = FileInputStream(file)
        try {
            return if (hasMagic(fis)) {
                aead(key).newDecryptingStream(fis, associatedData(file))
            } else {
                fis.channel.position(0)
                val iv = ByteArray(12).also { check(fis.read(it) == 12) }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
                CipherInputStream(fis, cipher)
            }
        } catch (e: Exception) {
            fis.close()
            throw e
        }
    }

    /**
     * Opens a v2 file as a random-access plaintext channel: reading at any position decrypts only the segments touched.
     * The caller must close it. Throws [IllegalArgumentException] for legacy files; call [upgradeToStreaming] first.
     */
    fun openSeekable(
        key: SecretKey,
        file: File,
    ): SeekableByteChannel {
        require(formatOf(file) == Format.STREAMING) { "legacy file: upgrade first" }
        val raf = java.io.RandomAccessFile(file, "r")
        return aead(key).newSeekableDecryptingChannel(OffsetChannel(raf.channel, MAGIC.size.toLong()), associatedData(file))
    }

    // ---- Housekeeping -------------------------------------------------------------------------

    /** Removes orphaned `.tmp` files left by a killed process. Call at startup. */
    fun sweepTmp(dir: File) = dir.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.delete() }

    // ---- Internals ----------------------------------------------------------------------------

    /** Reads up to the magic length from the current position of [s] (blocking until filled or EOF) and compares it; works on every API level. */
    private fun hasMagic(s: InputStream): Boolean {
        val head = ByteArray(MAGIC.size)
        var read = 0
        while (read < head.size) {
            val n = s.read(head, read, head.size - read)
            if (n < 0) break
            read += n
        }
        return read == head.size && head.contentEquals(MAGIC)
    }

    /** Tink streaming cipher for [key]; its input keying material is an HKDF-style subkey so the master key itself is never used for streams. */
    private fun aead(key: SecretKey): AesGcmHkdfStreaming {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.encoded, "HmacSHA256")) }
        val ikm = mac.doFinal("vault-stream-v2".toByteArray())
        return AesGcmHkdfStreaming(ikm, "HmacSha256", 32, SEGMENT_BYTES, 0)
    }

    /** Binds the ciphertext to its file name (without the `.tmp` suffix that writing uses). */
    private fun associatedData(file: File): ByteArray = file.name.removeSuffix(".tmp").toByteArray()

    /** A read-only view of [delegate] that hides the first [offset] bytes, so Tink sees the stream start at 0. */
    private class OffsetChannel(
        private val delegate: java.nio.channels.FileChannel,
        private val offset: Long,
    ) : SeekableByteChannel {
        private var pos = 0L

        override fun read(dst: ByteBuffer): Int = delegate.read(dst, offset + pos).also { if (it > 0) pos += it }

        override fun position(): Long = pos

        override fun position(newPosition: Long): SeekableByteChannel {
            pos = newPosition
            return this
        }

        override fun size(): Long = delegate.size() - offset

        override fun write(src: ByteBuffer): Int = throw UnsupportedOperationException("read-only")

        override fun truncate(size: Long): SeekableByteChannel = throw UnsupportedOperationException("read-only")

        override fun isOpen(): Boolean = delegate.isOpen

        override fun close() = delegate.close()
    }
}
