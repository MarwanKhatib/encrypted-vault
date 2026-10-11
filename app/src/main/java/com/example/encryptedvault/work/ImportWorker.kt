package com.example.encryptedvault.work

import android.content.Context
import android.graphics.Bitmap
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.encryptedvault.VaultApp
import com.example.encryptedvault.crypto.VaultFiles
import com.example.encryptedvault.data.PhotoEntity
import com.example.encryptedvault.data.PhotoMetadata
import com.example.encryptedvault.data.VaultDatabase
import com.example.encryptedvault.data.VaultRepository
import com.example.encryptedvault.data.VaultStorage
import com.example.encryptedvault.media.SourceInfo
import com.example.encryptedvault.media.Thumbnails
import com.example.encryptedvault.ui.settings.AppSettings
import java.io.ByteArrayOutputStream
import java.io.File
import javax.crypto.SecretKey

/**
 * Encrypts staged files into the vault.
 *
 * Idempotent and resumable (Gap 4): the queue is the input paths, dedup is by hash against the DB,
 * so a killed worker can simply be re-run. A row is marked `complete` only after the content,
 * thumbnail and face crops are all committed (Gap 5).
 */
class ImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val key = VaultApp.sessionKey ?: return Result.retry() // vault locked; retry after unlock
        val dao = VaultDatabase.get(applicationContext).photoDao()
        VaultStorage.sweepTemporaryFiles(applicationContext)

        val paths = inputData.getStringArray(KEY_PATHS) ?: return Result.success()
        val names = inputData.getStringArray(KEY_NAMES).orEmpty()
        val folders = inputData.getStringArray(KEY_FOLDERS).orEmpty()
        val takenAt = inputData.getLongArray(KEY_TAKEN_AT) ?: LongArray(0)

        for ((index, path) in paths.withIndex()) {
            val source = File(path)
            if (!source.exists()) continue
            val isStaged = source.path.startsWith(applicationContext.cacheDir.path)
            val hash = VaultFiles.sha256(source)
            if (dao.findCompleteByHash(hash) != null) { if (isStaged) source.delete(); continue } // already vaulted

            val mime = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(source.extension.lowercase()) ?: "application/octet-stream"
            val info = SourceInfo(names.getOrNull(index).orEmpty(), folders.getOrNull(index)?.takeIf { it.isNotEmpty() }, takenAt.getOrNull(index) ?: source.lastModified())
            // One unreadable file must not abort the rest of the batch.
            runCatching { importOne(key, source, hash, mime, info) }
                .onFailure { VaultStorage.deleteAll(applicationContext, hash) }
            if (isStaged) source.delete() // no plaintext left in cache, even on failure (the original stays untouched)
        }
        return Result.success()
    }

    /** Encrypts one file and commits its database row; throws on any failure so the caller can clean up. */
    private suspend fun importOne(key: SecretKey, source: File, hash: String, mime: String, info: SourceInfo) {
        val dao = VaultDatabase.get(applicationContext).photoDao()
        val (width, height) = Thumbnails.dimensions(source, mime)
        val pending = PhotoEntity(
            hash = hash, mimeType = mime, timestamp = info.takenAt, status = "pending", size = source.length(), width = width, height = height,
            durationMs = if (mime.startsWith("video/")) Thumbnails.videoDurationMs(source) else 0,
        )
        val id = dao.upsert(pending)
        try {
            source.inputStream().use { VaultFiles.encryptAtomic(key, it, VaultStorage.content(applicationContext, hash)) }
            writeThumbnail(key, source, mime, hash)
            val metadata = PhotoMetadata(info.name, info.folder)
            dao.upsert(pending.copy(id = id, status = "complete", metaEnc = VaultRepository.encryptMetadata(key, metadata)))
        } catch (e: Exception) {
            dao.deleteByIds(listOf(id))
            throw e
        }
    }

    /** Encrypts a JPEG thumbnail of [source] next to the content file, so the grid never has to decrypt the full media. */
    private fun writeThumbnail(key: SecretKey, source: File, mime: String, hash: String) {
        val jpeg = ByteArrayOutputStream().also { Thumbnails.generate(source, mime).compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
        VaultFiles.encryptAtomic(key, jpeg.inputStream(), VaultStorage.thumbnail(applicationContext, hash))
    }

    companion object {
        const val KEY_PATHS = "paths"
        const val KEY_NAMES = "names"
        const val KEY_FOLDERS = "folders"
        const val KEY_TAKEN_AT = "taken_at"

        /** Builds the worker input for staged files. */
        fun inputFor(files: List<com.example.encryptedvault.media.MediaTransfer.StagedFile>): Data = Data.Builder()
            .putStringArray(KEY_PATHS, files.map { it.path }.toTypedArray())
            .putStringArray(KEY_NAMES, files.map { it.info.name }.toTypedArray())
            .putStringArray(KEY_FOLDERS, files.map { it.info.folder.orEmpty() }.toTypedArray())
            .putLongArray(KEY_TAKEN_AT, files.map { it.info.takenAt }.toLongArray())
            .build()
    }
}
