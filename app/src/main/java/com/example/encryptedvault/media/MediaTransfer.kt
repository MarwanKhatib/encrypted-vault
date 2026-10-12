package com.example.encryptedvault.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.example.encryptedvault.VaultApp
import com.example.encryptedvault.crypto.VaultFiles
import com.example.encryptedvault.data.VaultDatabase
import com.example.encryptedvault.data.VaultItem
import com.example.encryptedvault.data.VaultStorage
import com.example.encryptedvault.security.SessionManager
import java.io.File
import javax.crypto.SecretKey

/**
 * Moves media across the vault boundary: importing picked files in, restoring or sharing decrypted
 * copies out, and loading decrypted bitmaps for display. Everything here handles plaintext, so
 * temporary files are kept in cache and wiped by [sweepTemporaryPlaintext].
 */
object MediaTransfer {
    internal const val IMPORT_DIR = "import"

    /** JPEG quality for photos saved from the editor and thumbnails restored from the vault. */
    private const val JPEG_QUALITY = 92
    private const val COPY_BUFFER_BYTES = 64 * 1024
    internal const val SHARE_DIR = "share"

    /** Folder an older version decrypted videos into; it is only swept now, never written. */
    private const val PLAYBACK_DIR = "play"

    // ---- Importing --------------------------------------------------------------------------

    /** A picked file copied into app cache, waiting for [com.example.encryptedvault.work.ImportWorker]. */
    class StagedFile(
        val path: String,
        val info: SourceInfo,
    )

    /** Largest file the vault accepts (videos are the reason; stills are far smaller). */
    const val MAX_IMPORT_BYTES = 500L * 1024 * 1024

    /** True when the provider reports that [uri] is larger than [MAX_IMPORT_BYTES]. An unknown size is treated as acceptable. */
    fun exceedsLimit(
        context: Context,
        uri: Uri,
    ): Boolean =
        runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
                c.moveToFirst() && !c.isNull(0) && c.getLong(0) > MAX_IMPORT_BYTES
            }
        }.getOrNull() == true

    /** Photos and videos only. Anything else is refused at import, so documents and other files never enter the vault. */
    fun isPhotoOrVideo(mime: String?): Boolean = mime != null && (mime.startsWith("image/") || mime.startsWith("video/"))

    /** MIME type of a picked file: what the provider reports, else guessed from its extension. */
    fun mimeOf(
        context: Context,
        uri: Uri,
    ): String? =
        context.contentResolver.getType(uri)
            ?: uri.lastPathSegment
                ?.substringAfterLast('.', "")
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    android.webkit.MimeTypeMap
                        .getSingleton()
                        .getMimeTypeFromExtension(it)
                }

    /** Copies [uri] into app cache (the worker deletes the copy once it is vaulted). */
    fun stage(
        context: Context,
        uri: Uri,
    ): StagedFile? =
        runCatching {
            val info = SourceInfoResolver.resolve(context, uri)
            val mime = context.contentResolver.getType(uri)
            val extension =
                info.name
                    .substringAfterLast('.', "")
                    .ifEmpty { MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin" }
            val file = File(File(context.cacheDir, IMPORT_DIR).apply { mkdirs() }, "${System.nanoTime()}.$extension")
            context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            file.setLastModified(info.takenAt)
            StagedFile(file.path, info)
        }.getOrNull()

    /** Writes an edited [bitmap] to the import cache as a JPEG, ready for [com.example.encryptedvault.work.ImportWorker]. */
    fun stageBitmap(
        context: Context,
        bitmap: Bitmap,
        info: SourceInfo,
    ): StagedFile? =
        runCatching {
            val file = File(File(context.cacheDir, IMPORT_DIR).apply { mkdirs() }, "${System.nanoTime()}.jpg")
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)) }
            file.setLastModified(info.takenAt)
            StagedFile(file.path, info)
        }.getOrNull()

    /**
     * MediaStore uris of picked items that are verifiably in the vault (hash match + full GCM decrypt).
     * Anything unverified is never offered for deletion.
     */
    suspend fun verifiedOriginalUris(
        context: Context,
        pickedUris: List<Uri>,
    ): List<Uri> {
        val key = VaultApp.sessionKey ?: return emptyList()
        val dao = VaultDatabase.get(context).photoDao()
        return pickedUris.mapNotNull { uri ->
            runCatching {
                val hash = context.contentResolver.openInputStream(uri)!!.use { VaultFiles.sha256(it) }
                if (dao.findCompleteByHash(hash) == null) return@runCatching null
                VaultFiles.decryptStream(key, VaultStorage.content(context, hash)).use { input ->
                    val sink = ByteArray(COPY_BUFFER_BYTES)
                    while (input.read(sink) >=
                        0
                    ) {
                        Unit
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaStore.getMediaUri(context, uri) else null
            }.getOrNull()
        }
    }

    // ---- Restoring --------------------------------------------------------------------------

    /** Outcome of [restoreToGallery]. [restoredIds] are safe to remove from the vault. */
    class RestoreResult(
        val restoredIds: List<Long>,
        val failed: Int,
    )

    /**
     * Writes decrypted copies back to the gallery, each into the folder it originally came from
     * (see [RestoreTargets]); MediaStore creates the folder when it no longer exists.
     */
    fun restoreToGallery(
        context: Context,
        items: List<VaultItem>,
    ): RestoreResult {
        val key = VaultApp.sessionKey ?: return RestoreResult(emptyList(), items.size)
        val restored = mutableListOf<Long>()
        for (item in items) {
            val ok =
                runCatching {
                    val target = RestoreTargets.resolve(item.isVideo, item.sourceFolder)
                    val values =
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName)
                            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                            put(MediaStore.MediaColumns.RELATIVE_PATH, target.relativePath)
                            put(MediaStore.MediaColumns.DATE_TAKEN, item.takenAt)
                            put(MediaStore.MediaColumns.DATE_MODIFIED, item.takenAt / 1000)
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                    val collection =
                        when (target.collection) {
                            RestoreCollection.IMAGES -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            RestoreCollection.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            RestoreCollection.DOWNLOADS -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        }
                    val uri = context.contentResolver.insert(collection, values)!!
                    var written = false
                    try {
                        context.contentResolver.openOutputStream(uri)!!.use { out ->
                            VaultFiles.decryptStream(key, VaultStorage.content(context, item.hash)).use { it.copyTo(out) }
                        }
                        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                        written = true
                    } finally {
                        // Remove the half-written MediaStore row if anything above failed.
                        if (!written) context.contentResolver.delete(uri, null, null)
                    }
                }.isSuccess
            if (ok) restored += item.id
        }
        return RestoreResult(restored, items.size - restored.size)
    }

    // ---- Sharing ----------------------------------------------------------------------------

    /** Decrypts to cache/share (optionally stripping GPS) and opens the system share sheet. */
    fun share(
        context: Context,
        items: List<VaultItem>,
        stripLocation: Boolean,
    ): Boolean {
        val key = VaultApp.sessionKey ?: return false
        sweepTemporaryPlaintext(context, olderThanMs = 0)
        val shareDir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        val uris =
            items.mapNotNull { item ->
                runCatching {
                    val file = File(shareDir, item.displayName)
                    VaultFiles.decryptStream(key, VaultStorage.content(context, item.hash)).use { i -> file.outputStream().use { i.copyTo(it) } }
                    if (stripLocation && !item.isVideo) runCatching { Thumbnails.stripSensitiveExif(file) }
                    FileProvider.getUriForFile(context, "${context.packageName}.share", file)
                }.getOrNull()
            }
        if (uris.isEmpty()) return false
        val send =
            Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
                type =
                    when {
                        items.all { it.isVideo } -> "video/*"
                        items.none { it.isVideo } -> "image/*"
                        else -> "*/*"
                    }
                if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris[0]) else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        SessionManager.suppressNextStopLock = true
        context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    // ---- Decrypting for display -------------------------------------------------------------

    /** Full-size decode (downsampled to about [maxEdge] px, EXIF-rotated) of a vaulted image. */
    fun loadFullImage(
        context: Context,
        item: VaultItem,
        maxEdge: Int = 2400,
    ): Bitmap? {
        val key = VaultApp.sessionKey ?: return null
        return runCatching {
            val bytes = VaultFiles.decryptStream(key, VaultStorage.content(context, item.hash)).use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val isHeif = item.mimeType == "image/heic" || item.mimeType == "image/heif"
            val degrees = if (isHeif) 0 else runCatching { ExifInterface(bytes.inputStream()).rotationDegrees }.getOrDefault(0)
            Thumbnails.rotate(bitmap, degrees)
        }.getOrNull()
    }

    /** Decodes an encrypted JPEG thumbnail as RGB_565: JPEG has no alpha, and half the memory keeps more thumbnails in the grid cache. */
    fun loadEncryptedJpeg(
        key: SecretKey,
        file: File,
    ): Bitmap? =
        runCatching {
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            VaultFiles.decryptStream(key, file).use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()

    /** Removes temporary plaintext created for sharing/playback. [olderThanMs] = 0 wipes everything. */
    fun sweepTemporaryPlaintext(
        context: Context,
        olderThanMs: Long,
    ) {
        val cutoff = System.currentTimeMillis() - olderThanMs
        listOf(SHARE_DIR, PLAYBACK_DIR).forEach { dir ->
            File(context.cacheDir, dir).listFiles()?.forEach { if (it.lastModified() <= cutoff) it.delete() }
        }
    }
}
