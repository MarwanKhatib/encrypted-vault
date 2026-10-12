package com.example.encryptedvault

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.encryptedvault.crypto.Bip39
import com.example.encryptedvault.crypto.KeyHierarchy
import com.example.encryptedvault.data.VaultDatabase
import com.example.encryptedvault.data.VaultItem
import com.example.encryptedvault.data.VaultRepository
import com.example.encryptedvault.data.VaultStorage
import com.example.encryptedvault.media.MediaTransfer
import com.example.encryptedvault.media.SourceInfo
import com.example.encryptedvault.work.ImportWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Imports every photo and video sample in filesDir/samples (JPEG, PNG, GIF, HEIC, MP4), checks non-media files are not stored,
 * and checks each import is stored with the right type, its size, and dimensions for images or a duration for video.
 * Put the samples there with `scripts/device-checks.sh formats`.
 */
@RunWith(AndroidJUnit4::class)
class ImportFormatsTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var keys: KeyHierarchy
    private val pin = "604817".toCharArray()

    @Before fun unlockFreshVault() {
        keys = KeyHierarchy(context)
        keys.destroyAllKeyMaterial()
        VaultDatabase.destroy(context)
        VaultRepository.reset()
        VaultStorage.directory(context).listFiles()?.forEach { it.delete() }
        val bip39 =
            Bip39(
                context.assets
                    .open("bip39_english.txt")
                    .bufferedReader()
                    .readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() },
            )
        keys.setUp(pin, bip39)
        VaultApp.sessionKey = checkNotNull(keys.unlockWithPin(pin))
    }

    @After fun tearDown() {
        keys.destroyAllKeyMaterial()
        VaultDatabase.destroy(context)
        VaultRepository.reset()
        VaultStorage.directory(context).listFiles()?.forEach { it.delete() }
        VaultApp.sessionKey = null
    }

    /** The MIME type the importer should store for each sample, by extension. */
    private val expectedMime =
        mapOf(
            "jpg" to "image/jpeg",
            "png" to "image/png",
            "gif" to "image/gif",
            "heic" to "image/heic",
            "mp4" to "video/mp4",
            "txt" to "text/plain",
        )

    @Test fun everySampleImportsWithItsTypeSizeAndDimensionsOrDuration() {
        val dir = File(context.filesDir, "samples")
        val samples =
            dir
                .listFiles()
                .orEmpty()
                .filter { it.isFile }
                .sortedBy { it.name }
        assumeTrue("needs filesDir/samples (scripts/device-checks.sh formats)", samples.isNotEmpty())

        val problems = mutableListOf<String>()
        // Only photos and videos reach the importer; the picker refuses everything else.
        val media = samples.filter { MediaTransfer.isPhotoOrVideo(expectedMime[it.extension.lowercase()]) }
        for (sample in media) {
            val staged = stage(sample)
            if (!runBlocking { ImportWorker.importAndWait(context, staged) }) problems += "import failed: ${sample.name}"
        }

        val items = runBlocking { VaultRepository.get(context).items.first() }
        for (sample in samples.filterNot { it in media }) {
            if (items.any { it.name == sample.name }) problems += "non-media stored: ${sample.name}"
        }
        for (sample in media) {
            val item = items.singleOrNull { it.name == sample.name }
            problems += if (item == null) listOf("not stored: ${sample.name}") else problemsWith(sample, item)
        }
        assertTrue("problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /** Everything that is wrong with one stored item compared with the file it came from. */
    private fun problemsWith(
        sample: File,
        item: VaultItem,
    ): List<String> =
        buildList {
            val expected = expectedMime[sample.extension.lowercase()]
            if (item.mimeType != expected) add("${sample.name}: type ${item.mimeType}, expected $expected")
            if (item.sizeBytes != sample.length()) add("${sample.name}: stored ${item.sizeBytes} bytes, file has ${sample.length()}")
            if (item.isVideo && item.durationMs <= 0) add("${sample.name}: no duration")
            if (item.mimeType.startsWith("image/") && (item.width <= 0 || item.height <= 0)) add("${sample.name}: no dimensions")
        }

    /** Copies [source] into the import folder under its own name, the way the picker stages files. */
    private fun stage(source: File): MediaTransfer.StagedFile {
        val dir = File(context.cacheDir, MediaTransfer.IMPORT_DIR).apply { mkdirs() }
        val copy = File(dir, "${System.nanoTime()}.${source.extension}").also { source.copyTo(it) }
        return MediaTransfer.StagedFile(copy.path, SourceInfo(source.name, "DCIM/Camera", System.currentTimeMillis()))
    }
}
