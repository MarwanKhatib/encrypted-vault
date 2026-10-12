package com.example.encryptedvault.work

import android.content.Context
import android.graphics.Bitmap
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.encryptedvault.VaultApp
import com.example.encryptedvault.crypto.VaultFiles
import com.example.encryptedvault.data.PhotoDao
import com.example.encryptedvault.data.PhotoEntity
import com.example.encryptedvault.data.PhotoMetadata
import com.example.encryptedvault.data.VaultDatabase
import com.example.encryptedvault.data.VaultRepository
import com.example.encryptedvault.data.VaultStorage
import com.example.encryptedvault.media.SourceInfo
import com.example.encryptedvault.media.Thumbnails
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Encrypts staged files into the vault.
 *
 * Idempotent and resumable (Gap 4): the queue is the input paths, dedup is by hash against the DB,
 * so a killed worker can simply be re-run. A row is marked `complete` only after the content,
 * thumbnail and face crops are all committed (Gap 5).
 */
class ImportWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val key = VaultApp.sessionKey ?: return Result.retry() // vault locked; retry after unlock
        val dao = VaultDatabase.get(applicationContext).photoDao()
        VaultStorage.sweepTemporaryFiles(applicationContext)

        val paths = inputData.getStringArray(KEY_PATHS) ?: return Result.success()
        val batch =
            BatchInput(
                names = inputData.getStringArray(KEY_NAMES).orEmpty(),
                folders = inputData.getStringArray(KEY_FOLDERS).orEmpty(),
                takenAt = inputData.getLongArray(KEY_TAKEN_AT) ?: LongArray(0),
            )
        paths.forEachIndexed { index, path -> importEntry(key, dao, path, index, batch) }
        return Result.success()
    }

    /**
     * Imports the staged or picked file at [path]. A missing file or one that is already vaulted is skipped; a staged copy is
     * always deleted afterwards, so no plaintext is left in the cache even on failure (the original stays untouched).
     */
    private suspend fun importEntry(
        key: SecretKey,
        dao: PhotoDao,
        path: String,
        index: Int,
        batch: BatchInput,
    ) {
        val source = File(path)
        if (!source.exists()) return
        val isStaged = source.path.startsWith(applicationContext.cacheDir.path)
        val hash = VaultFiles.sha256(source)
        if (dao.findCompleteByHash(hash) != null) {
            if (isStaged) source.delete()
            return
        }
        val mime =
            android.webkit.MimeTypeMap
                .getSingleton()
                .getMimeTypeFromExtension(source.extension.lowercase()) ?: "application/octet-stream"
        val info =
            SourceInfo(
                batch.names.getOrNull(index).orEmpty(),
                batch.folders.getOrNull(index)?.takeIf { it.isNotEmpty() },
                batch.takenAt.getOrNull(index) ?: source.lastModified(),
            )
        // One unreadable file must not abort the rest of the batch.
        runCatching { importOne(key, source, hash, mime, info) }
            .onFailure { VaultStorage.deleteAll(applicationContext, hash) }
        if (isStaged) source.delete()
    }

    /** The per-batch arrays from the worker input: display names, source folders and capture times, indexed like the paths. */
    private class BatchInput(
        val names: Array<out String>,
        val folders: Array<out String>,
        val takenAt: LongArray,
    )

    /** Encrypts one file and commits its database row; throws on any failure so the caller can clean up. */
    private suspend fun importOne(
        key: SecretKey,
        source: File,
        hash: String,
        mime: String,
        info: SourceInfo,
    ) {
        val dao = VaultDatabase.get(applicationContext).photoDao()
        val (width, height) = Thumbnails.dimensions(source, mime)
        val pending =
            PhotoEntity(
                hash = hash,
                mimeType = mime,
                timestamp = info.takenAt,
                status = "pending",
                size = source.length(),
                width = width,
                height = height,
                durationMs = if (mime.startsWith("video/")) Thumbnails.videoDurationMs(source) else 0,
            )
        val id = dao.upsert(pending)
        var committed = false
        try {
            source.inputStream().use { VaultFiles.encryptAtomic(key, it, VaultStorage.content(applicationContext, hash)) }
            writeThumbnail(key, source, mime, hash)
            val metadata = PhotoMetadata(info.name, info.folder)
            dao.upsert(pending.copy(id = id, status = "complete", metaEnc = VaultRepository.encryptMetadata(key, metadata)))
            committed = true
        } finally {
            // A row that never reached "complete" is removed, whatever the failure; the caller still sees the exception.
            if (!committed) dao.deleteByIds(listOf(id))
        }
    }

    /** Encrypts a JPEG thumbnail of [source] next to the content file, so the grid never has to decrypt the full media. */
    private fun writeThumbnail(
        key: SecretKey,
        source: File,
        mime: String,
        hash: String,
    ) {
        val bitmap = Thumbnails.generate(source, mime)
        val jpeg = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_JPEG_QUALITY, it) }.toByteArray()
        VaultFiles.encryptAtomic(key, jpeg.inputStream(), VaultStorage.thumbnail(applicationContext, hash))
    }

    companion object {
        const val KEY_PATHS = "paths"

        /** JPEG quality of the encrypted thumbnail that the grid shows; smaller than the full file, so it loads fast. */
        const val THUMBNAIL_JPEG_QUALITY = 82
        const val KEY_NAMES = "names"
        const val KEY_FOLDERS = "folders"
        const val KEY_TAKEN_AT = "taken_at"

        /** Builds the worker input for staged files. */
        fun inputFor(files: List<com.example.encryptedvault.media.MediaTransfer.StagedFile>): Data =
            Data
                .Builder()
                .putStringArray(KEY_PATHS, files.map { it.path }.toTypedArray())
                .putStringArray(KEY_NAMES, files.map { it.info.name }.toTypedArray())
                .putStringArray(KEY_FOLDERS, files.map { it.info.folder.orEmpty() }.toTypedArray())
                .putLongArray(KEY_TAKEN_AT, files.map { it.info.takenAt }.toLongArray())
                .build()

        /**
         * Splits [files] into batches whose worker input stays well under WorkManager's 10 KB Data limit. A fixed count is not
         * enough: long names and folders push a large selection over the limit and the import crashes.
         */
        internal fun batchesOf(
            files: List<com.example.encryptedvault.media.MediaTransfer.StagedFile>,
            budgetBytes: Int = INPUT_BUDGET_BYTES,
        ): List<List<com.example.encryptedvault.media.MediaTransfer.StagedFile>> {
            val batches = mutableListOf<MutableList<com.example.encryptedvault.media.MediaTransfer.StagedFile>>()
            var used = 0
            for (file in files) {
                val cost =
                    file.path.utf8Size() + file.info.name.utf8Size() +
                        file.info.folder
                            .orEmpty()
                            .utf8Size() + PER_FILE_OVERHEAD_BYTES
                if (batches.isEmpty() || used + cost > budgetBytes) {
                    batches.add(mutableListOf())
                    used = 0
                }
                batches.last().add(file)
                used += cost
            }
            return batches
        }

        /**
         * Queues [files] as a chain of batches under one unique name, so they run one after another and a second selection
         * appends to the same chain. Returns the batch ids in order; empty when there is nothing to import.
         */
        fun enqueueBatches(
            context: Context,
            files: List<com.example.encryptedvault.media.MediaTransfer.StagedFile>,
        ): List<UUID> {
            val requests = batchesOf(files).map { OneTimeWorkRequestBuilder<ImportWorker>().setInputData(inputFor(it)).build() }
            if (requests.isEmpty()) return emptyList()
            WorkManager.getInstance(context).beginUniqueWork(CHAIN_NAME, ExistingWorkPolicy.APPEND, requests).enqueue()
            return requests.map { it.id }
        }

        /** Imports [staged] through this worker and suspends until it finishes; true when it succeeded. */
        suspend fun importAndWait(
            context: Context,
            staged: com.example.encryptedvault.media.MediaTransfer.StagedFile,
        ): Boolean {
            val request = OneTimeWorkRequestBuilder<ImportWorker>().setInputData(inputFor(listOf(staged))).build()
            val workManager = WorkManager.getInstance(context)
            workManager.enqueue(request)
            val info = workManager.getWorkInfoByIdFlow(request.id).first { it != null && it.state.isFinished }
            return info?.state == WorkInfo.State.SUCCEEDED
        }

        /** UTF-8 length of this string in bytes, used to budget WorkManager input. */
        private fun String.utf8Size() = toByteArray(Charsets.UTF_8).size

        /** WorkManager rejects input over 10 KB; keep headroom for the keys and the encoding overhead. */
        private const val INPUT_BUDGET_BYTES = 8 * 1024
        private const val PER_FILE_OVERHEAD_BYTES = 64
        private const val CHAIN_NAME = "import"
    }
}
