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
 * Finds gallery photos and videos added since the last import, for auto-import while the vault is open (ADR-013).
 * Items are only read here; copying and encryption go through [MediaTransfer] and [com.example.encryptedvault.work.ImportWorker].
 */
object AutoImport {
    /** Read permissions for gallery media on this Android version. */
    fun permissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    /** True when every permission from [permissions] is granted. */
    fun granted(context: Context): Boolean = permissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** True when an item added at [dateAddedSec] (epoch seconds) is newer than [watermarkSec]. */
    internal fun isNew(
        dateAddedSec: Long,
        watermarkSec: Long,
    ): Boolean = dateAddedSec > watermarkSec

    /** Watermark after importing items added at [addedSec]: the newest of them, never lower than [current]. */
    internal fun nextWatermark(
        current: Long,
        addedSec: List<Long>,
    ): Long = maxOf(current, addedSec.maxOrNull() ?: current)

    /**
     * Gallery images and videos (from any app) added after [watermarkSec], oldest first, as (uri, dateAddedSec).
     * Empty when the permission is missing or the provider refuses the query.
     */
    fun newItems(
        context: Context,
        watermarkSec: Long,
    ): List<Pair<Uri, Long>> {
        if (!granted(context)) return emptyList()
        return runCatching {
            listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .flatMap { queryCollection(context, it, watermarkSec) }
                .sortedBy { it.second }
        }.getOrDefault(emptyList())
    }

    /** New rows of one MediaStore collection, as (item uri, dateAddedSec). */
    private fun queryCollection(
        context: Context,
        collection: Uri,
        watermarkSec: Long,
    ): List<Pair<Uri, Long>> {
        val cursor =
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED),
                "${MediaStore.MediaColumns.DATE_ADDED} > ?",
                arrayOf(watermarkSec.toString()),
                null,
            ) ?: return emptyList()
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val added = c.getLong(1)
                    if (isNew(added, watermarkSec)) add(ContentUris.withAppendedId(collection, c.getLong(0)) to added)
                }
            }
        }
    }
}
