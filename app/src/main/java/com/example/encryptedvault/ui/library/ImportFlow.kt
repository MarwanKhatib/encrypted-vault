package com.example.encryptedvault.ui.library

import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.encryptedvault.media.MediaTransfer
import com.example.encryptedvault.security.SessionManager
import com.example.encryptedvault.ui.settings.AppSettings
import com.example.encryptedvault.work.ImportWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Remembers the whole "Import" journey and returns a function that starts it:
 *
 *  1. system file picker, 2. stage files in cache, 3. [ImportWorker] encrypts them,
 *  4. optionally ask Android to delete the originals — only those verified to decrypt from the vault.
 *
 * [onBusy] receives a progress message while working and null when finished.
 */
@Composable
fun rememberImportLauncher(
    settings: AppSettings,
    snackbar: SnackbarHostState,
    onBusy: (String?) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val deleteLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            SessionManager.suppressNextStopLock = false
            scope.launch {
                snackbar.showSnackbar(
                    if (result.resultCode ==
                        android.app.Activity.RESULT_OK
                    ) {
                        "Originals removed from this device. Also delete them in Google Photos and empty its trash."
                    } else {
                        "Originals kept in your gallery."
                    },
                )
            }
        }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
            SessionManager.suppressNextStopLock = false
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            scope.launch {
                val notMedia = withContext(Dispatchers.IO) { uris.filterNot { MediaTransfer.isPhotoOrVideo(MediaTransfer.mimeOf(context, it)) } }
                if (notMedia.isNotEmpty()) snackbar.showSnackbar("Skipped ${notMedia.size} file(s) that are not photos or videos")
                val candidates = uris - notMedia.toSet()
                val oversized = withContext(Dispatchers.IO) { candidates.filter { MediaTransfer.exceedsLimit(context, it) } }
                if (oversized.isNotEmpty()) snackbar.showSnackbar("Skipped ${oversized.size} file(s) larger than 500 MB")
                val picked = candidates - oversized.toSet()
                if (picked.isEmpty()) return@launch
                onBusy("Encrypting ${picked.size}…")
                val staged = withContext(Dispatchers.IO) { picked.mapNotNull { MediaTransfer.stage(context, it) } }
                val workManager = WorkManager.getInstance(context)
                val batches = ImportWorker.enqueueBatches(context, staged)
                val succeeded =
                    batches.isNotEmpty() &&
                        batches.all { id ->
                            workManager.getWorkInfoByIdFlow(id).first { it != null && it.state.isFinished }?.state == WorkInfo.State.SUCCEEDED
                        }
                onBusy(null)

                if (!succeeded) {
                    snackbar.showSnackbar("Encryption failed; originals untouched.")
                    return@launch
                }
                if (!settings.offerDeleteOriginals) {
                    snackbar.showSnackbar("Added ${staged.size} to the vault.")
                    return@launch
                }

                val deletable = withContext(Dispatchers.IO) { MediaTransfer.verifiedOriginalUris(context, picked) }
                if (deletable.isEmpty()) {
                    snackbar.showSnackbar("Encrypted. Originals can't be auto-removed; delete them in your gallery.")
                    return@launch
                }
                SessionManager.suppressNextStopLock = true
                val sender = MediaStore.createDeleteRequest(context.contentResolver, deletable).intentSender
                deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
        }

    return {
        SessionManager.suppressNextStopLock = true
        picker.launch("*/*")
    }
}
