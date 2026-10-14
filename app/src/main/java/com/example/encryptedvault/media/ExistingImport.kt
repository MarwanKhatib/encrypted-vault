package com.example.encryptedvault.media

import android.content.Context
import androidx.work.WorkManager
import com.example.encryptedvault.VaultApp
import com.example.encryptedvault.ui.settings.AppSettings
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
 * One-time import of the gallery's existing photos and videos, oldest first, in chunks so the cache never holds the whole
 * library. Runs while the vault is unlocked; originals are kept (no delete prompt here). Re-running is safe: dedup is by hash.
 */
object ExistingImport {
    private const val CHUNK = 20
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val statusFlow = MutableStateFlow<String?>(null)

    /** Progress text for the Settings screen; null when no import has run in this session. */
    val status: StateFlow<String?> = statusFlow.asStateFlow()

    @Volatile private var running = false

    /** Starts the import unless one is already running or the vault is locked. Permission must already be granted. */
    fun start(
        context: Context,
        settings: AppSettings,
    ) {
        if (running) return
        val app = context.applicationContext
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
                run(app, settings)
            } finally {
                running = false
            }
        }
    }

    private suspend fun run(
        app: Context,
        settings: AppSettings,
    ) {
        val items = AutoImport.newItems(app, 0L).filterNot { MediaTransfer.exceedsLimit(app, it.first) }
        if (items.isEmpty()) {
            statusFlow.value = "No gallery items found."
            return
        }
        val workManager = WorkManager.getInstance(app)
        var done = 0
        var failed = 0
        for (chunk in items.chunked(CHUNK)) {
            if (VaultApp.sessionKey == null) {
                statusFlow.value = "Locked at $done of ${items.size}. Unlock and start again to continue."
                return
            }
            val staged = chunk.mapNotNull { MediaTransfer.stage(app, it.first) }
            failed += chunk.size - staged.size
            val infos = ImportWorker.enqueueBatches(app, staged).map { id -> workManager.getWorkInfoByIdFlow(id).first { it != null && it.state.isFinished } }
            failed += infos.sumOf { it?.outputData?.getInt(ImportWorker.KEY_FAILED, 0) ?: 0 }
            done += chunk.size
            statusFlow.value = "Importing $done of ${items.size}…"
        }
        // Later auto-import starts after the newest existing item, so it does not stage them again.
        withContext(Dispatchers.Main) { settings.putAutoImportSince(items.maxOf { it.second }) }
        statusFlow.value = "Done: ${items.size - failed} imported, $failed failed. Originals stay in your gallery."
    }
}
