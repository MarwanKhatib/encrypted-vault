package com.example.encryptedvault.media

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.encryptedvault.VaultApp
import com.example.encryptedvault.crypto.VaultFiles
import com.example.encryptedvault.data.VaultItem
import com.example.encryptedvault.data.VaultStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import kotlin.coroutines.resume

private const val TRIM_DIR = "trim"

/** A trim selection in milliseconds of the source video. Always at least [MIN_LENGTH_MS] long. */
data class TrimRange(
    val startMs: Long,
    val endMs: Long,
) {
    init {
        require(startMs >= 0 && endMs - startMs >= MIN_LENGTH_MS) { "Trim must keep at least $MIN_LENGTH_MS ms" }
    }

    /** Moves the start handle, keeping the minimum length. */
    fun withStart(ms: Long) = TrimRange(ms.coerceIn(0, endMs - MIN_LENGTH_MS), endMs)

    /** Moves the end handle, keeping the minimum length and staying inside a [durationMs] video. */
    fun withEnd(
        ms: Long,
        durationMs: Long,
    ) = TrimRange(startMs, ms.coerceIn(startMs + MIN_LENGTH_MS, durationMs))

    /** True when the selection keeps the whole video, so there is nothing to save. */
    fun covers(durationMs: Long) = startMs == 0L && endMs == durationMs

    companion object {
        const val MIN_LENGTH_MS = 1_000L

        /** Whether a video of [durationMs] is long enough to trim at all. */
        fun trimmable(durationMs: Long) = durationMs >= MIN_LENGTH_MS

        /** The whole video as one selection, from the start to [durationMs]. */
        fun whole(durationMs: Long) = TrimRange(0, durationMs)
    }
}

/** "clip.mov" -> "clip (trimmed).mp4"; trimmed copies are always MP4. */
fun trimmedName(name: String): String {
    val base = name.substringBeforeLast('.', name).trim().ifEmpty { "Video" }
    return "$base (trimmed).mp4"
}

/**
 * Decrypts [item] to a temporary plaintext file, exports [range] as MP4 and returns the staged result for the import worker.
 * The plaintext is always deleted; the original vaulted video is never modified.
 */
suspend fun stageTrim(
    context: Context,
    item: VaultItem,
    range: TrimRange,
): MediaTransfer.StagedFile? {
    val key = VaultApp.sessionKey ?: return null
    val source = File(File(context.cacheDir, TRIM_DIR).apply { mkdirs() }, "${System.nanoTime()}.${item.name.substringAfterLast('.', "mp4")}")
    return try {
        withContext(Dispatchers.IO) {
            VaultFiles.decryptStream(key, VaultStorage.content(context, item.hash)).use { input -> source.outputStream().use { input.copyTo(it) } }
        }
        val output = File(File(context.cacheDir, MediaTransfer.IMPORT_DIR).apply { mkdirs() }, "${System.nanoTime()}.mp4")
        if (exportTrim(context, source, range, output)) {
            MediaTransfer.StagedFile(output.path, SourceInfo(trimmedName(item.name), item.sourceFolder, item.takenAt))
        } else {
            output.delete()
            null
        }
    } catch (e: IOException) {
        Log.w(TAG, "trim failed (I/O): ${e.javaClass.simpleName}")
        null
    } catch (e: GeneralSecurityException) {
        Log.w(TAG, "trim failed (decryption): ${e.javaClass.simpleName}")
        null
    } finally {
        source.delete()
    }
}

private const val TAG = "VideoTrim"

/** Writes the [range] of [source] to [output] with Media3 Transformer. Transformer must run on a Looper thread, so this hops to main. */
private suspend fun exportTrim(
    context: Context,
    source: File,
    range: TrimRange,
    output: File,
): Boolean =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val transformer = Transformer.Builder(context).build()
            val mainHandler = Handler(Looper.getMainLooper())
            transformer.addListener(
                object : Transformer.Listener {
                    override fun onCompleted(
                        composition: Composition,
                        exportResult: ExportResult,
                    ) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
            )
            continuation.invokeOnCancellation { mainHandler.post { transformer.cancel() } }
            val clip =
                MediaItem.ClippingConfiguration
                    .Builder()
                    .setStartPositionMs(range.startMs)
                    .setEndPositionMs(range.endMs)
                    .build()
            val mediaItem =
                MediaItem
                    .Builder()
                    .setUri(Uri.fromFile(source))
                    .setClippingConfiguration(clip)
                    .build()
            transformer.start(EditedMediaItem.Builder(mediaItem).build(), output.path)
        }
    }
