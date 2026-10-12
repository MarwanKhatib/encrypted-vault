package com.example.encryptedvault.ui.common

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.example.encryptedvault.VaultApp
import com.example.encryptedvault.data.VaultItem
import com.example.encryptedvault.data.VaultStorage
import com.example.encryptedvault.media.MediaTransfer
import com.example.encryptedvault.security.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/** Decrypted grid thumbnail for [item]; cached in memory until the vault locks. */
@Composable
fun rememberThumbnail(item: VaultItem): State<Bitmap?> {
    val context = LocalContext.current
    return rememberDecryptedBitmap(cacheKey = item.hash) { VaultStorage.thumbnail(context, item.hash) }
}

/** Caps concurrent thumbnail decrypts at three, so fast scrolling does not start one decrypt per visible cell. */
private val decryptGate = Semaphore(permits = 3)

/** Shared decrypt-once loader: checks the in-memory cache first, then decrypts [file] off the main thread. */
@Composable
private fun rememberDecryptedBitmap(
    cacheKey: String,
    file: () -> File,
): State<Bitmap?> =
    produceState(SessionManager.thumbCache.get(cacheKey), cacheKey) {
        if (value != null) return@produceState
        value =
            decryptGate.withPermit {
                withContext(Dispatchers.IO) {
                    val key = VaultApp.sessionKey ?: return@withContext null
                    MediaTransfer.loadEncryptedJpeg(key, file())?.also { SessionManager.thumbCache.put(cacheKey, it) }
                }
            }
    }
