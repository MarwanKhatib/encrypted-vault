package com.example.encryptedvault.media

import android.content.Context
import android.net.Uri
import androidx.work.WorkManager
import com.example.encryptedvault.VaultApp
import com.example.encryptedvault.crypto.VaultFiles
import com.example.encryptedvault.data.VaultDatabase
import com.example.encryptedvault.work.ImportWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Scans the phone's photos and videos and adds what the vault does not have yet (ADR-016).
 *
 * Each gallery file is compared by content hash with the vault's complete copies. Only the missing ones are copied
 * and encrypted, in chunks so the cache never holds the whole library. Runs by default when the vault opens and when
 * the gallery changes, and on demand from Settings. Originals stay in the gallery.
 */
object ExistingImport {
    private const val CHUNK = 20
    private const val PROGRESS_EVERY = 50
    private const val MIN_INTERVAL_MS = 10 * 60_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val statusFlow = MutableStateFlow<String?>(null)

    /** Progress text for the Settings screen; null when no scan has run in this session. */
    val status: StateFlow<String?> = statusFlow.asStateFlow()

    /** Main-thread callback with the gallery URIs one scan added, so the app can offer to delete their originals. */
    @Volatile var onAdded: ((List<Uri>) -> Unit)? = null

    @Volatile private var running = false

    @Volatile private var lastFinishedAt = 0L

    /**
     * Starts a scan unless one is running or the vault is locked. Without [force], a scan that finished less than
     * [MIN_INTERVAL_MS] ago is skipped, so reopening the app does not rehash the gallery each time.
     */
    fun start(
        context: Context,
        force: Boolean = false,
    ) {
        if (running) return
        val app = context.applicationContext
        if (!force && System.currentTimeMillis() - lastFinishedAt < MIN_INTERVAL_MS) return
        if (VaultApp.sessionKey == null) {
            statusFlow.value = "Unlock the vault first."
            return
        }
        if (!AutoImport.granted(app)) {
            statusFlow.value = "Allow photo and video access first."
            return
        }
        running = true
        scope.launch {
            try {
                scan(app)
            } finally {
                lastFinishedAt = System.currentTimeMillis()
                running = false
            }
        }
    }

    private suspend fun scan(app: Context) {
        val listed = AutoImport.listAll(app).filterNot { MediaTransfer.exceedsLimit(app, it.uri) }
        if (listed.isEmpty()) {
            statusFlow.value = "No gallery items found."
            return
        }
        val known =
            VaultDatabase
                .get(app)
                .photoDao()
                .completeHashes()
                .toHashSet()
        val cache = ScanCache.load(app)
        val missing = mutableListOf<AutoImport.MediaRef>()
        listed.forEachIndexed { index, ref ->
            val hash = cache[ref.cacheKey()] ?: hashOf(app, ref.uri)?.also { cache[ref.cacheKey()] = it }
            if (hash == null || hash !in known) missing += ref
            if ((index + 1) % PROGRESS_EVERY == 0) statusFlow.value = "Checking ${index + 1} of ${listed.size}…"
        }
        ScanCache.save(app, cache, listed.map { it.cacheKey() }.toSet())
        if (missing.isEmpty()) {
            statusFlow.value = "Up to date: all ${listed.size} gallery items are in the vault."
            return
        }
        val added = importMissing(app, missing)
        statusFlow.value = "Done: ${added.size} added, ${missing.size - added.size} failed, ${listed.size - missing.size} already in the vault."
        if (added.isNotEmpty()) withContext(Dispatchers.Main) { onAdded?.invoke(added) }
    }

    /** Copies and encrypts [missing] in chunks. Returns the gallery URIs that were imported. */
    private suspend fun importMissing(
        app: Context,
        missing: List<AutoImport.MediaRef>,
    ): List<Uri> {
        val workManager = WorkManager.getInstance(app)
        val imported = mutableListOf<Uri>()
        for (chunk in missing.chunked(CHUNK)) {
            if (VaultApp.sessionKey == null) {
                statusFlow.value = "Locked after ${imported.size} added. Unlock to continue."
                break
            }
            val staged = chunk.mapNotNull { ref -> MediaTransfer.stage(app, ref.uri)?.let { ref to it } }
            val infos =
                ImportWorker.enqueueBatches(app, staged.map { it.second }).map { id ->
                    workManager.getWorkInfoByIdFlow(id).first { it != null && it.state.isFinished }
                }
            val failedInBatch = infos.sumOf { it?.outputData?.getInt(ImportWorker.KEY_FAILED, 0) ?: 0 }
            // Only the files the worker did not report as failed count as added; the delete prompt re-checks each one anyway.
            imported += staged.map { it.first.uri }.take(staged.size - failedInBatch)
            statusFlow.value = "Importing ${imported.size} of ${missing.size}…"
        }
        return imported
    }

    /** Gallery URIs whose content already has a complete vault copy. Feeds the "delete originals" action; nothing is deleted here. */
    suspend fun inVaultUris(app: Context): List<Uri> {
        val listed = AutoImport.listAll(app)
        val known =
            VaultDatabase
                .get(app)
                .photoDao()
                .completeHashes()
                .toHashSet()
        val cache = ScanCache.load(app)
        val inVault =
            listed.filter { ref ->
                val hash = cache[ref.cacheKey()] ?: hashOf(app, ref.uri)?.also { cache[ref.cacheKey()] = it }
                hash != null && hash in known
            }
        ScanCache.save(app, cache, listed.map { it.cacheKey() }.toSet())
        return inVault.map { it.uri }
    }

    /** Content hash of a gallery file, read without copying it. Null when the file cannot be opened. */
    private fun hashOf(
        app: Context,
        uri: Uri,
    ): String? = runCatching { app.contentResolver.openInputStream(uri)?.use { VaultFiles.sha256(it) } }.getOrNull()
}
