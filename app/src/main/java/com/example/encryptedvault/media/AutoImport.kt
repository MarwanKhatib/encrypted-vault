package com.example.encryptedvault.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

/**
 * Lists the gallery's photos and videos (any app's) so a scan can compare them with the vault (ADR-016).
 * Items are only read here; copying and encryption go through [MediaTransfer] and [com.example.encryptedvault.work.ImportWorker].
 */
object AutoImport {
    /** One gallery item as MediaStore reports it. [cacheKey] identifies the file content without reading it. */
    data class MediaRef(
        val uri: Uri,
        val size: Long,
        val modified: Long,
    ) {
        /** Key for [ScanCache]: uri, size and modified time together. */
        fun cacheKey(): String = "$uri|$size|$modified"
    }

    /** Read permissions for gallery media on this Android version. */
    fun permissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    /** True when every permission from [permissions] is granted. */
    fun granted(context: Context): Boolean = permissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** Every gallery image and video, from any app. Empty when the permission is missing or the provider refuses the query. */
    fun listAll(context: Context): List<MediaRef> {
        if (!granted(context)) return emptyList()
        return runCatching {
            listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).flatMap { queryCollection(context, it) }
        }.getOrDefault(emptyList())
    }

    private fun queryCollection(
        context: Context,
        collection: Uri,
    ): List<MediaRef> {
        val cursor =
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED),
                null,
                null,
                null,
            ) ?: return emptyList()
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(MediaRef(ContentUris.withAppendedId(collection, c.getLong(0)), c.getLong(1), c.getLong(2)))
                }
            }
        }
    }
}
