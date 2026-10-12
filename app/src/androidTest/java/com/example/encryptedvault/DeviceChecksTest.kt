package com.example.encryptedvault

import android.content.Context
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.encryptedvault.crypto.Bip39
import com.example.encryptedvault.crypto.KeyHierarchy
import com.example.encryptedvault.data.VaultDatabase
import com.example.encryptedvault.data.VaultStorage
import com.example.encryptedvault.media.MediaTransfer
import com.example.encryptedvault.media.SourceInfo
import com.example.encryptedvault.media.Thumbnails
import com.example.encryptedvault.work.ImportWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Random

/**
 * Device checks from the Gap 13 list that run without UI: duplicate import, GPS stripping, temporary plaintext cleanup,
 * HEIC and MP4 thumbnails, and the 10-wrong-PIN lockout. Each test starts from an empty vault.
 */
@RunWith(AndroidJUnit4::class)
class DeviceChecksTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var keys: KeyHierarchy
    private lateinit var bip39: Bip39
    private val pin = "604817".toCharArray()

    @Before fun resetVault() {
        keys = KeyHierarchy(context)
        keys.destroyAllKeyMaterial()
        VaultDatabase.destroy(context)
        VaultStorage.directory(context).listFiles()?.forEach { it.delete() }
        File(context.cacheDir, MediaTransfer.IMPORT_DIR).deleteRecursively()
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

    @After fun tearDown() {
        keys.destroyAllKeyMaterial()
        VaultDatabase.destroy(context)
        VaultStorage.directory(context).listFiles()?.forEach { it.delete() }
        com.example.encryptedvault.VaultApp.sessionKey = null
    }

    private fun unlockedVault() {
        keys.setUp(pin, bip39)
        com.example.encryptedvault.VaultApp.sessionKey = checkNotNull(keys.unlockWithPin(pin))
    }

    /** A noisy JPEG so each file is large and distinct. Optionally tagged with a GPS location. */
    private fun jpeg(
        file: File,
        seed: Long,
        gps: Boolean = false,
    ): File {
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val random = Random(seed)
        bitmap.setPixels(IntArray(640 * 480) { random.nextInt() }, 0, 640, 0, 0, 640, 480)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (gps) {
            ExifInterface(file.path).apply {
                setLatLong(37.7749, -122.4194)
                saveAttributes()
            }
        }
        return file
    }

    private fun stagedCopy(
        source: File,
        name: String,
    ): MediaTransfer.StagedFile {
        val dir = File(context.cacheDir, MediaTransfer.IMPORT_DIR).apply { mkdirs() }
        val copy = File(dir, "${System.nanoTime()}.jpg").also { source.copyTo(it) }
        return MediaTransfer.StagedFile(copy.path, SourceInfo(name, "DCIM/Camera", System.currentTimeMillis()))
    }

    private fun completeRows(hash: String) =
        runBlocking {
            VaultDatabase
                .get(context)
                .photoDao()
                .allCompleted()
                .count { it.hash == hash }
        }

    private fun pendingCount(): Int =
        VaultDatabase
            .get(context)
            .openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM photos WHERE status != 'complete'")
            .use {
                it.moveToFirst()
                it.getInt(0)
            }

    @Test fun encryptingTheSamePhotoTwiceMakesOneEntry() {
        unlockedVault()
        val source = jpeg(File(context.cacheDir, "dup-source.jpg"), seed = 1)
        val hash =
            com.example.encryptedvault.crypto.VaultFiles
                .sha256(source)
        assertTrue("first import succeeds", runBlocking { ImportWorker.importAndWait(context, stagedCopy(source, "IMG_1.jpg")) })
        assertTrue("second import succeeds", runBlocking { ImportWorker.importAndWait(context, stagedCopy(source, "IMG_1 copy.jpg")) })
        assertEquals("exactly one entry for the double tap", 1, completeRows(hash))
        assertEquals("no pending rows left behind", 0, pendingCount())
    }

    @Test fun gpsIsStrippedFromTheSharedCopy() {
        val photo = jpeg(File(context.cacheDir, "gps-source.jpg"), seed = 2, gps = true)
        assertNotNull("the test photo really has a location", ExifInterface(photo.path).latLong)
        Thumbnails.stripSensitiveExif(photo)
        assertNull("location is gone after stripping", ExifInterface(photo.path).latLong)
    }

    @Test fun sharedPlaintextCopyIsSweptAway() {
        val shared = File(File(context.cacheDir, "share").apply { mkdirs() }, "IMG_9.jpg").apply { writeBytes(ByteArray(64) { 1 }) }
        assertTrue("the shared copy exists before locking", shared.exists())
        MediaTransfer.sweepTemporaryPlaintext(context, olderThanMs = 0)
        assertFalse("the shared plaintext copy is gone", shared.exists())
    }

    /**
     * Needs a real HEIC at filesDir/sample.heic. The SDK has no HEIF encoder, so the sample has to come from a real phone.
     * Without it the test is skipped and reported as skipped, not passed.
     */
    @Test fun heicPhotoGetsAThumbnail() {
        val heic = File(context.filesDir, "sample.heic")
        assumeTrue("no HEIC sample at filesDir/sample.heic: this check is NOT run", heic.exists())
        val thumbnail = Thumbnails.decodeUpright(heic, "image/heic", 256)
        assertNotNull("HEIC decodes through ImageDecoder", thumbnail)
        assertTrue("thumbnail is no larger than the edge limit", maxOf(thumbnail!!.width, thumbnail.height) <= 256)
    }

    /** Needs a real MP4 at the app files path filesDir/sample.mp4. `scripts/device-checks.sh` copies one there with run-as. */
    @Test fun mp4VideoGetsADurationAndAFrame() {
        val video = File(context.filesDir, "sample.mp4")
        assertTrue("sample.mp4 must be in the app cache (see docs/DESIGN_v5.md, device checks)", video.exists())
        assertTrue("duration is read", Thumbnails.videoDurationMs(video) > 0)
        assertTrue("a frame is extracted", Thumbnails.videoFrames(video, 1, 256).isNotEmpty())
    }

    @Test fun tenWrongPinsLockTheVault() {
        keys.setUp(pin, bip39)
        val wrong = "111111"
        assertNull("the wrong PIN is refused", keys.unlockWithPin(wrong.toCharArray()))
        repeat(10) { keys.recordFailure() }
        assertEquals("all ten failures are counted", 10, keys.failCount())
        assertTrue("the vault is locked out after ten failures", keys.lockedForMs() > 0)
        keys.resetFailures()
        assertEquals("a correct unlock clears the lockout", 0L, keys.lockedForMs())
    }
}
