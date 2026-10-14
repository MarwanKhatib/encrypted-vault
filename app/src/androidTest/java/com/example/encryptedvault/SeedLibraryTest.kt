package com.example.encryptedvault

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.encryptedvault.crypto.Bip39
import com.example.encryptedvault.crypto.KeyHierarchy
import com.example.encryptedvault.data.VaultDatabase
import com.example.encryptedvault.data.VaultRepository
import com.example.encryptedvault.data.VaultStorage
import com.example.encryptedvault.media.MediaTransfer
import com.example.encryptedvault.media.SourceInfo
import com.example.encryptedvault.work.ImportWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Developer seed, not a regression test: builds a vault with the test PIN and imports every file in filesDir/lagseed, then
 * leaves the vault in place so the Library can be checked by hand (for example frame timing while scrolling).
 * It only runs when that folder exists, so the normal `unit` run skips it. It destroys any existing vault first.
 */
@RunWith(AndroidJUnit4::class)
class SeedLibraryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun seedsTheVaultFromLagseedFolder() {
        val dir = File(context.filesDir, "lagseed")
        val files =
            dir
                .listFiles()
                .orEmpty()
                .filter { it.isFile }
                .sortedBy { it.name }
        assumeTrue("needs filesDir/lagseed (developer seed only)", files.isNotEmpty())

        val keys = KeyHierarchy(context)
        keys.destroyAllKeyMaterial()
        VaultDatabase.destroy(context)
        VaultRepository.reset()
        VaultStorage.directory(context).listFiles()?.forEach { it.delete() }
        val pin = "604817".toCharArray()
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

        for (file in files) {
            val cache = File(context.cacheDir, MediaTransfer.IMPORT_DIR).apply { mkdirs() }
            val copy = File(cache, "${System.nanoTime()}.${file.extension}").also { file.copyTo(it) }
            val staged =
                MediaTransfer.StagedFile(
                    copy.path,
                    SourceInfo(
                        file.name,
                        "DCIM/Camera",
                        System.currentTimeMillis() - file.hashCode().toLong().mod(86_400_000L),
                    ),
                )
            runBlocking { ImportWorker.importAndWait(context, staged) }
        }
        check(runBlocking { VaultRepository.get(context).items.first() }.size >= files.size - 1) { "seed import did not store the files" }
    }
}
