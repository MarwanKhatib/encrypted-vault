package com.example.encryptedvault

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.encryptedvault.crypto.VaultFiles
import com.example.encryptedvault.media.VaultDataSource
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** Verifies the ExoPlayer data source reads, seeks and fails correctly over a real encrypted file on a device. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class VaultDataSourceTest {
    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "ds-test").apply { mkdirs() }
    private val key: SecretKey = SecretKeySpec(ByteArray(32) { (it + 5).toByte() }, "AES")
    private val plain = ByteArray(VaultFiles.SEGMENT_BYTES * 2 + 4321).also { SecureRandom().nextBytes(it) }
    private val hash = "videohash"
    private lateinit var file: File

    @Before fun encryptSample() {
        file = File(dir, "$hash.enc").also { VaultFiles.encryptAtomic(key, ByteArrayInputStream(plain), it) }
    }

    @After fun cleanUp() { dir.deleteRecursively() }

    private fun source(currentKey: SecretKey? = key) = VaultDataSource({ currentKey }, { File(dir, "$it.enc") })

    private fun spec(position: Long = 0, length: Long = C.LENGTH_UNSET.toLong()) =
        DataSpec.Builder().setUri(Uri.parse("vault://$hash")).setPosition(position).setLength(length).build()

    private fun VaultDataSource.readAll(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) { val n = read(buf, 0, buf.size); if (n == C.RESULT_END_OF_INPUT) break; out.write(buf, 0, n) }
        return out.toByteArray()
    }

    @Test fun readsTheWholeFile() {
        val ds = source()
        assertEquals(plain.size.toLong(), ds.open(spec()))
        assertArrayEquals(plain, ds.readAll())
        ds.close()
    }

    @Test fun startsAtTheRequestedPosition() {
        val ds = source()
        val position = VaultFiles.SEGMENT_BYTES + 99L
        assertEquals(plain.size - position, ds.open(spec(position)))
        assertArrayEquals(plain.copyOfRange(position.toInt(), plain.size), ds.readAll())
        ds.close()
    }

    @Test fun honoursARequestedLength() {
        val ds = source()
        assertEquals(1000L, ds.open(spec(position = 50, length = 1000)))
        assertArrayEquals(plain.copyOfRange(50, 1050), ds.readAll())
        ds.close()
    }

    @Test fun aLockedVaultFailsToOpen() {
        assertThrows(IOException::class.java) { source(currentKey = null).open(spec()) }
    }

    @Test fun aWrongKeyFailsWhileReading() {
        val wrong = SecretKeySpec(ByteArray(32) { 9 }, "AES")
        val ds = source(wrong)
        assertThrows(IOException::class.java) { ds.open(spec()); ds.readAll() }
        ds.close()
    }

    @Test fun aMissingFileFailsToOpen() {
        file.delete()
        assertThrows(IOException::class.java) { source().open(spec()) }
    }

    @Test fun closeIsIdempotentAndForgetsTheUri() {
        val ds = source()
        ds.open(spec())
        ds.close(); ds.close()
        assertEquals(null, ds.uri)
    }
}
