package com.example.encryptedvault

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.encryptedvault.crypto.Bip39
import com.example.encryptedvault.crypto.KeyHierarchy
import com.example.encryptedvault.data.VaultDatabase
import com.example.encryptedvault.data.VaultStorage
import com.example.encryptedvault.media.MediaTransfer
import com.example.encryptedvault.media.SourceInfo
import com.example.encryptedvault.work.ImportWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Random

/**
 * Kill-mid-batch drill (Gap 13). It runs only when started with `-e killDrill true`, because the host has to force-stop the
 * app between the two phases. `scripts/device-checks.sh` runs them in order:
 *  1. [phase1StartBatchThenHostKillsApp] builds a vault and starts one import of [COUNT] photos. The host kills the app mid-run.
 *  2. [phase2VerifyAfterKill] unlocks again, lets WorkManager resume the batch, and checks there is no loss, no duplicate, no
 *     half-written row and no leftover file.
 */
@RunWith(AndroidJUnit4::class)
class KillMidBatchTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pin = "604817".toCharArray()

    @Before fun onlyInTheDrill() {
        assumeTrue("run through scripts/device-checks.sh", InstrumentationRegistry.getArguments().getString("killDrill") == "true")
    }

    @Test fun phase1StartBatchThenHostKillsApp() {
        val keys = KeyHierarchy(context)
        keys.destroyAllKeyMaterial()
        VaultDatabase.destroy(context)
        VaultStorage.directory(context).listFiles()?.forEach { it.delete() }
        val bip39 = Bip39(context.assets.open("bip39_english.txt").bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() })
        keys.setUp(pin, bip39)
        VaultApp.sessionKey = checkNotNull(keys.unlockWithPin(pin))

        val staged = (1..COUNT).map { stagePhoto(it) }
        val batches = ImportWorker.enqueueBatches(context, staged) // the production path: the same chain the import button uses
        Log.i(TAG, "batch enqueued: $COUNT files in ${batches.size} batches")
        // Log progress until the host kills the process, so the last line shows the rows at the moment of the kill.
        val until = System.currentTimeMillis() + WAIT_FOR_KILL_MS
        while (System.currentTimeMillis() < until) {
            Log.i(TAG, "progress: ${completeCount()} complete, ${pendingCount()} pending")
            Thread.sleep(PROGRESS_EVERY_MS)
        }
        fail("the app was not killed during the batch; the drill did not test anything")
    }

    @Test fun phase2VerifyAfterKill() {
        val keys = KeyHierarchy(context)
        VaultApp.sessionKey = checkNotNull(keys.unlockWithPin(pin)) { "phase 1 must run first" }
        Log.i(TAG, "phase 2 starts with ${completeCount()} complete, ${pendingCount()} pending rows (WorkManager may already be resuming)")

        val deadline = System.currentTimeMillis() + RESUME_TIMEOUT_MS
        while (completeCount() < COUNT && System.currentTimeMillis() < deadline) Thread.sleep(2_000)

        val hashes = runBlocking { VaultDatabase.get(context).photoDao().allCompleted().map { it.hash } }
        assertEquals("no photo lost", COUNT, hashes.size)
        assertEquals("no duplicate entries", COUNT, hashes.toSet().size)
        assertEquals("no half-written rows", 0, pendingCount())
        val orphans = VaultStorage.directory(context).listFiles().orEmpty().filter { it.name.endsWith(".tmp") }
        assertEquals("no .tmp files left in the vault", 0, orphans.size)
        val leftovers = File(context.cacheDir, MediaTransfer.IMPORT_DIR).listFiles().orEmpty()
        assertEquals("no plaintext left in the import cache", 0, leftovers.size)
        Log.i(TAG, "drill passed: $COUNT photos, one entry each")
    }

    private fun completeCount(): Int = runBlocking { VaultDatabase.get(context).photoDao().allCompleted().size }

    /** Rows that never reached `complete`. The DAO only lists complete rows, so this counts them directly. */
    private fun pendingCount(): Int = VaultDatabase.get(context).openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM photos WHERE status != 'complete'").use { it.moveToFirst(); it.getInt(0) }

    /** A large, distinct JPEG staged in the import cache, the way the picker hands files to the import. */
    private fun stagePhoto(index: Int): MediaTransfer.StagedFile {
        val dir = File(context.cacheDir, MediaTransfer.IMPORT_DIR).apply { mkdirs() }
        val file = File(dir, "drill-$index.jpg")
        val bitmap = Bitmap.createBitmap(2000, 1500, Bitmap.Config.ARGB_8888)
        val random = Random(index.toLong())
        bitmap.setPixels(IntArray(2000 * 1500) { random.nextInt() }, 0, 2000, 0, 0, 2000, 1500)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return MediaTransfer.StagedFile(file.path, SourceInfo("IMG_$index.jpg", "DCIM/Camera", index.toLong() * 1000))
    }

    private companion object {
        const val TAG = "KillMidBatch"
        const val COUNT = 150
        const val WAIT_FOR_KILL_MS = 120_000L
        const val PROGRESS_EVERY_MS = 250L
        const val RESUME_TIMEOUT_MS = 10 * 60_000L
    }
}
