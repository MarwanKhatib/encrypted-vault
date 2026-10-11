package com.example.encryptedvault.media

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns

/** Facts about a picked file that are lost once it is copied into the vault. */
data class SourceInfo(
    val name: String,
    /** Relative folder such as `DCIM/Camera`, or null if the provider did not expose it. */
    val folder: String?,
    /** When the media was taken (epoch millis). */
    val takenAt: Long,
)

/** Reads [SourceInfo] for a picked [Uri], using MediaStore when possible. */
object SourceInfoResolver {

    /** Prefers MediaStore (real folder and date taken) and falls back to the provider's openable columns. */
    fun resolve(context: Context, uri: Uri): SourceInfo {
        val mediaUri = if (Build.VERSION.SDK_INT >= 31) runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull() else null
        return fromMediaStore(context, mediaUri ?: uri) ?: fromOpenable(context, uri)
    }

    /** Reads name, relative folder and taken date from MediaStore; null when [uri] is not a MediaStore item. */
    private fun fromMediaStore(context: Context, uri: Uri): SourceInfo? = runCatching {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_MODIFIED,
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val taken = c.getLong(2).takeIf { it > 0 } ?: (c.getLong(3) * 1000).takeIf { it > 0 } ?: System.currentTimeMillis()
            SourceInfo(c.getString(0).orEmpty(), c.getString(1)?.trim('/')?.takeIf { it.isNotEmpty() }, taken)
        }
    }.getOrNull()

    /** Fallback: file name and last-modified only. The original folder is unknown. */
    private fun fromOpenable(context: Context, uri: Uri): SourceInfo {
        var name = ""
        var modified = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it).orEmpty() }
                    c.getColumnIndex("last_modified").takeIf { it >= 0 }?.let { modified = c.getLong(it) }
                }
            }
        }
        return SourceInfo(name, null, modified.takeIf { it > 0 } ?: System.currentTimeMillis())
    }
}
