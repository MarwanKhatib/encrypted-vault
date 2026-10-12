package com.example.encryptedvault.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Round-trip, tamper, truncation, seek and legacy-compatibility tests for [VaultFiles]. Runs on the JVM (no Android classes). */
class VaultFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val key: SecretKey = SecretKeySpec(ByteArray(32) { (it * 7 + 1).toByte() }, "AES")
    private val otherKey: SecretKey = SecretKeySpec(ByteArray(32) { (it * 11 + 3).toByte() }, "AES")

    private fun bytes(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }

    private fun encrypt(
        plain: ByteArray,
        name: String = "a.enc",
        with: SecretKey = key,
    ): File = File(tmp.root, name).also { VaultFiles.encryptAtomic(with, ByteArrayInputStream(plain), it) }

    private fun decrypt(
        file: File,
        with: SecretKey = key,
    ): ByteArray = VaultFiles.decryptStream(with, file).use { it.readBytes() }

    /** Writes a file the way the pre-streaming app did (single AES-GCM message), to prove old vaults still open. */
    private fun legacyFile(
        plain: ByteArray,
        name: String,
    ): File {
        val iv = bytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        return File(tmp.root, name).also { it.writeBytes(iv + cipher.doFinal(plain)) }
    }

    @Test fun smallFileRoundTrips() {
        val plain = bytes(1000)
        assertArrayEquals(plain, decrypt(encrypt(plain)))
    }

    @Test fun emptyFileRoundTrips() = assertEquals(0, decrypt(encrypt(ByteArray(0))).size)

    @Test fun fileSpanningSeveralSegmentsRoundTrips() {
        val plain = bytes(VaultFiles.SEGMENT_BYTES * 2 + 12_345)
        assertArrayEquals(plain, decrypt(encrypt(plain)))
    }

    @Test fun fileExactlyOneSegmentLongRoundTrips() {
        val plain = bytes(VaultFiles.SEGMENT_BYTES)
        assertArrayEquals(plain, decrypt(encrypt(plain)))
    }

    @Test fun ciphertextDoesNotContainThePlaintext() {
        val plain = ByteArray(4096) { 0x41 } // 4 KiB of 'A'
        val raw = encrypt(plain).readBytes()
        assertFalse(String(raw, Charsets.ISO_8859_1).contains("AAAAAAAA"))
    }

    @Test fun encryptingTwiceGivesDifferentCiphertext() {
        val plain = bytes(500)
        assertFalse(encrypt(plain, "x.enc").readBytes().contentEquals(encrypt(plain, "y.enc").readBytes()))
    }

    @Test fun wrongKeyIsRejected() {
        val file = encrypt(bytes(5000))
        assertThrows(IOException::class.java) { decrypt(file, otherKey) }
    }

    @Test fun aFlippedBitIsDetected() {
        val file = encrypt(bytes(VaultFiles.SEGMENT_BYTES + 100))
        val raw = file.readBytes().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        file.writeBytes(raw)
        assertThrows(IOException::class.java) { decrypt(file) }
    }

    @Test fun truncationIsDetected() {
        val file = encrypt(bytes(VaultFiles.SEGMENT_BYTES * 2))
        file.writeBytes(file.readBytes().copyOf((file.length() * 0.6).toInt()))
        assertThrows(IOException::class.java) { decrypt(file) }
    }

    @Test fun ciphertextMovedToAnotherNameIsRejected() {
        val original = encrypt(bytes(2000), "item-a.enc")
        val moved = File(tmp.root, "item-b.enc").also { original.copyTo(it) }
        assertThrows(IOException::class.java) { decrypt(moved) }
    }

    @Test fun noTempFileIsLeftBehind() {
        encrypt(bytes(100))
        assertTrue(tmp.root.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun failedEncryptionLeavesNoPartialDestination() {
        val failing =
            object : java.io.InputStream() {
                override fun read(): Int = throw IOException("disk read error")
            }
        val dest = File(tmp.root, "broken.enc")
        assertThrows(IOException::class.java) { VaultFiles.encryptAtomic(key, failing, dest) }
        assertFalse(dest.exists())
        assertTrue(tmp.root.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun formatDetectionDistinguishesNewAndLegacyFiles() {
        assertEquals(VaultFiles.Format.STREAMING, VaultFiles.formatOf(encrypt(bytes(10))))
        assertEquals(VaultFiles.Format.LEGACY, VaultFiles.formatOf(legacyFile(bytes(10), "old.enc")))
    }

    @Test fun legacyFilesStillDecrypt() {
        val plain = bytes(30_000)
        assertArrayEquals(plain, decrypt(legacyFile(plain, "old.enc")))
    }

    @Test fun upgradeConvertsLegacyInPlaceAndKeepsContent() {
        val plain = bytes(70_000)
        val file = legacyFile(plain, "old.enc")
        VaultFiles.upgradeToStreaming(key, file)
        assertEquals(VaultFiles.Format.STREAMING, VaultFiles.formatOf(file))
        assertArrayEquals(plain, decrypt(file))
    }

    @Test fun upgradeOfAnAlreadyStreamingFileChangesNothing() {
        val file = encrypt(bytes(5000))
        val before = file.readBytes()
        VaultFiles.upgradeToStreaming(key, file)
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun seekableChannelReportsPlaintextSize() {
        val plain = bytes(VaultFiles.SEGMENT_BYTES + 777)
        VaultFiles.openSeekable(key, encrypt(plain)).use { assertEquals(plain.size.toLong(), it.size()) }
    }

    @Test fun seekableChannelReadsFromAnyOffsetAcrossSegments() {
        val plain = bytes(VaultFiles.SEGMENT_BYTES * 3 + 10)
        val file = encrypt(plain)
        VaultFiles.openSeekable(key, file).use { ch ->
            for (offset in listOf(0, 1, VaultFiles.SEGMENT_BYTES - 5, VaultFiles.SEGMENT_BYTES, VaultFiles.SEGMENT_BYTES * 2 + 123, plain.size - 20)) {
                ch.position(offset.toLong())
                assertArrayEquals("offset $offset", plain.copyOfRange(offset, offset + 20), readFully(ch, 20))
            }
        }
    }

    /** Reads up to [count] bytes from [ch] at its current position, stopping early only at end of stream. */
    private fun readFully(
        ch: SeekableByteChannel,
        count: Int,
    ): ByteArray {
        val buf = ByteBuffer.allocate(count)
        while (buf.hasRemaining()) {
            if (ch.read(buf) < 0) break
        }
        return buf.array()
    }

    @Test fun seekableChannelRefusesLegacyFiles() {
        val file = legacyFile(bytes(100), "old.enc")
        assertThrows(IllegalArgumentException::class.java) { VaultFiles.openSeekable(key, file) }
    }

    @Test fun sha256MatchesAKnownVector() =
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", VaultFiles.sha256(ByteArrayInputStream("abc".toByteArray())))
}
