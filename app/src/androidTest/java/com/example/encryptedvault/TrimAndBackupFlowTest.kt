package com.example.encryptedvault

import android.content.Context
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.encryptedvault.backup.RestoreReport
import com.example.encryptedvault.backup.VaultBackup
import com.example.encryptedvault.crypto.Bip39
import com.example.encryptedvault.crypto.KeyHierarchy
import com.example.encryptedvault.data.VaultDatabase
import com.example.encryptedvault.data.VaultRepository
import com.example.encryptedvault.data.VaultStorage
import com.example.encryptedvault.media.MediaTransfer
import com.example.encryptedvault.media.SourceInfo
import com.example.encryptedvault.media.TrimRange
import com.example.encryptedvault.media.stageTrim
import com.example.encryptedvault.work.ImportWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/**
 * End-to-end trim and backup on a device, without the file picker: real import, real Transformer export, real backup file.
 * Needs filesDir/sample.mp4 for the trim test (scripts/device-checks.sh copies it in).
 */
@RunWith(AndroidJUnit4::class)
class TrimAndBackupFlowTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var keys: KeyHierarchy
    private val pin = "604817".toCharArray()
    private val passphrase = "backup-test-passphrase".toCharArray()

    @Before fun unlockFreshVault() {
        keys = KeyHierarchy(context)
        keys.destroyAllKeyMaterial()
        wipeVault()
        val bip39 = Bip39(context.assets.open("bip39_english.txt").bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() })
        keys.setUp(pin, bip39)
        VaultApp.sessionKey = checkNotNull(keys.unlockWithPin(pin))
    }

    @After fun tearDown() {
        keys.destroyAllKeyMaterial()
        wipeVault()
        VaultApp.sessionKey = null
    }

    private fun wipeVault() {
        VaultDatabase.destroy(context)
        VaultRepository.reset()
        VaultStorage.directory(context).listFiles()?.forEach { it.delete() }
        File(context.cacheDir, MediaTransfer.IMPORT_DIR).deleteRecursively()
    }

    /** Copies [source] into the import folder under [name], the way the picker stages files. */
    private fun stage(source: File, name: String): MediaTransfer.StagedFile {
        val dir = File(context.cacheDir, MediaTransfer.IMPORT_DIR).apply { mkdirs() }
        val copy = File(dir, "${System.nanoTime()}.${source.extension}").also { source.copyTo(it) }
        return MediaTransfer.StagedFile(copy.path, SourceInfo(name, "DCIM/Camera", System.currentTimeMillis()))
    }

    private fun items() = runBlocking { VaultRepository.get(context).items.first() }

    @Test fun trimmingAVideoSavesANewShorterCopy() {
        val sample = File(context.filesDir, "sample.mp4")
        assumeTrue("needs filesDir/sample.mp4 (scripts/device-checks.sh copies it)", sample.exists())
        assertTrue("import the video", runBlocking { ImportWorker.importAndWait(context, stage(sample, "clip.mp4")) })
        val original = items().single()

        val staged = runBlocking { stageTrim(context, original, TrimRange(0, 2_000)) }
        assertNotNull("Transformer exports the trimmed copy", staged)
        assertTrue("import the trimmed copy", runBlocking { ImportWorker.importAndWait(context, staged!!) })

        val trimmed = items().single { it.name == "clip (trimmed).mp4" }
        assertTrue("trimmed copy is about 2 s, was ${trimmed.durationMs} ms", trimmed.durationMs in 1_000..2_600)
        assertEquals("the original is kept", 2, items().size)
        assertEquals("the original is unchanged", original.sizeBytes, items().single { it.id == original.id }.sizeBytes)
    }

    @Test fun backupRestoresFavoritesCaptionsAndAlbumsOnAFreshVault() {
        val photo = File(context.cacheDir, "beach.jpg").apply {
            outputStream().use { Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF3366CC.toInt()) }.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        assertTrue("import the photo", runBlocking { ImportWorker.importAndWait(context, stage(photo, "beach.jpg")) })
        val repository = VaultRepository.get(context)
        val id = items().single().id
        runBlocking {
            repository.setFavorite(listOf(id), true)
            repository.setCaption(id, "Sunset")
            repository.addToAlbum(repository.createAlbum("Trips"), listOf(id))
        }

        val backup = File(context.cacheDir, "test-backup.evbk")
        val exported = runBlocking { backup.outputStream().use { VaultBackup.export(context, passphrase, it) } }
        assertEquals("one item exported", 1, exported)

        wipeVault()
        assertEquals("vault is empty before restore", 0, items().size)
        val report = runBlocking { backup.inputStream().use { VaultBackup.restore(context, passphrase, it) } }
        assertEquals(RestoreReport(imported = 1, skipped = 0, failed = 0), report)

        val restored = items().single()
        assertTrue("favourite restored", restored.isFavorite)
        assertEquals("caption restored", "Sunset", restored.caption)
        val trips = runBlocking { VaultRepository.get(context).albums.first() }.single { it.name == "Trips" }
        assertEquals("album membership restored", setOf(restored.id), trips.itemIds)

        val again = runBlocking { backup.inputStream().use { VaultBackup.restore(context, passphrase, it) } }
        assertEquals("a second restore skips what is already here", RestoreReport(imported = 0, skipped = 1, failed = 0), again)
    }

    @Test fun wrongPassphraseIsRefusedAndTheVaultStaysEmpty() {
        val photo = File(context.cacheDir, "wrong.jpg").apply {
            outputStream().use { Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        assertTrue(runBlocking { ImportWorker.importAndWait(context, stage(photo, "wrong.jpg")) })
        val backup = File(context.cacheDir, "wrong-pass.evbk")
        runBlocking { backup.outputStream().use { VaultBackup.export(context, passphrase, it) } }

        wipeVault()
        assertThrows(IOException::class.java) {
            runBlocking { backup.inputStream().use { VaultBackup.restore(context, "not the passphrase".toCharArray(), it) } }
        }
        assertEquals("nothing was restored", 0, items().size)
    }
}
