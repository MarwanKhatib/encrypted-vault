package com.example.encryptedvault.backup

import android.content.Context
import com.example.encryptedvault.VaultApp
import com.example.encryptedvault.crypto.BackupCrypto
import com.example.encryptedvault.crypto.VaultFiles
import com.example.encryptedvault.data.VaultDatabase
import com.example.encryptedvault.data.VaultItem
import com.example.encryptedvault.data.VaultRepository
import com.example.encryptedvault.data.VaultStorage
import com.example.encryptedvault.media.MediaTransfer
import com.example.encryptedvault.media.SourceInfo
import com.example.encryptedvault.work.ImportWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Counts shown to the user after a restore. */
data class RestoreReport(
    val imported: Int,
    val skipped: Int,
    val failed: Int,
)

/**
 * Encrypted export and restore of the whole vault (ADR-007).
 *
 * Items are read decrypted and written under the backup passphrase, so the file restores on any device. Restore stages each
 * item's content and runs it through [ImportWorker] (which dedups by hash), then re-applies favourite, hidden, caption, trash
 * and album membership. Items already in the vault are skipped, never overwritten.
 */
object VaultBackup {
    /** Writes every item to [out] under [passphrase] and closes [out]. Returns the number of items written. */
    suspend fun export(
        context: Context,
        passphrase: CharArray,
        out: OutputStream,
    ): Int {
        val key = checkNotNull(VaultApp.sessionKey) { "Vault is locked" }
        val repository = VaultRepository.get(context)
        val items = repository.items.first()
        val albums = repository.albums.first()
        withContext(Dispatchers.IO) {
            DataOutputStream(BackupCrypto.encrypting(out, passphrase)).use { data ->
                BackupRecords.writeCount(data, items.size)
                for (item in items) {
                    val titles = albums.filter { item.id in it.itemIds }.map { it.name }
                    BackupRecords.write(data, item.toRecord(titles))
                    val copied = VaultFiles.decryptStream(key, VaultStorage.content(context, item.hash)).use { it.copyTo(data) }
                    check(copied == item.sizeBytes) { "Stored size of ${item.hash} does not match its record" }
                }
            }
        }
        return items.size
    }

    /** Restores the backup in [input] into the unlocked vault and closes [input]. Throws [IOException] for a wrong passphrase or damaged file. */
    suspend fun restore(
        context: Context,
        passphrase: CharArray,
        input: InputStream,
    ): RestoreReport {
        checkNotNull(VaultApp.sessionKey) { "Vault is locked" }
        val repository = VaultRepository.get(context)
        val dao = VaultDatabase.get(context).photoDao()
        var imported = 0
        var skipped = 0
        var failed = 0
        withContext(Dispatchers.IO) {
            DataInputStream(BackupCrypto.decrypting(input, passphrase)).use { data ->
                repeat(BackupRecords.readCount(data)) {
                    val record = BackupRecords.read(data)
                    // The content must be consumed even when the item is skipped or fails, or the rest of the stream is misread.
                    when {
                        dao.findCompleteByHash(record.hash) != null -> {
                            BackupRecords.copyExactly(data, null, record.sizeBytes)
                            skipped++
                        }
                        else -> {
                            val staged = stageContent(context, data, record)
                            if (staged != null && ImportWorker.importAndWait(context, staged)) {
                                applyState(context, repository, record)
                                imported++
                            } else {
                                failed++
                            }
                        }
                    }
                }
            }
        }
        return RestoreReport(imported, skipped, failed)
    }

    /** Maps a vault item to the record that is written to the backup, with its album titles. */
    private fun VaultItem.toRecord(albums: List<String>) =
        BackupRecord(
            hash = hash,
            mimeType = mimeType,
            takenAt = takenAt,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            width = width,
            height = height,
            favorite = isFavorite,
            hidden = isHidden,
            trashed = isTrashed,
            name = name,
            folder = sourceFolder,
            caption = caption,
            albums = albums,
        )

    /** Writes the content to a staged file in the import folder and checks its hash. Returns null if the content does not match. */
    private fun stageContent(
        context: Context,
        data: DataInputStream,
        record: BackupRecord,
    ): MediaTransfer.StagedFile? {
        val dir = File(context.cacheDir, MediaTransfer.IMPORT_DIR).apply { mkdirs() }
        val extension =
            android.webkit.MimeTypeMap
                .getSingleton()
                .getExtensionFromMimeType(record.mimeType) ?: "bin"
        val file = File(dir, "${System.nanoTime()}.$extension")
        try {
            file.outputStream().use { BackupRecords.copyExactly(data, it, record.sizeBytes) }
        } catch (e: IOException) {
            file.delete()
            throw e
        }
        if (VaultFiles.sha256(file) == record.hash) {
            return MediaTransfer.StagedFile(file.path, SourceInfo(record.name, record.folder, record.takenAt))
        }
        file.delete()
        return null
    }

    /** Re-applies the state that the import worker does not carry: favourite, hidden, caption, trash and albums. */
    private suspend fun applyState(
        context: Context,
        repository: VaultRepository,
        record: BackupRecord,
    ) {
        val id =
            VaultDatabase
                .get(context)
                .photoDao()
                .findCompleteByHash(record.hash)
                ?.id ?: return
        if (record.favorite) repository.setFavorite(listOf(id), true)
        if (record.hidden) repository.setHidden(listOf(id), true)
        if (record.caption.isNotEmpty()) repository.setCaption(id, record.caption)
        if (record.trashed) repository.moveToTrash(listOf(id))
        record.albums.forEach { title -> repository.addToAlbum(albumIdFor(repository, title), listOf(id)) }
    }

    /** Finds the id of the album titled [title], creating it when it does not exist yet. */
    private suspend fun albumIdFor(
        repository: VaultRepository,
        title: String,
    ): Long =
        repository.albums
            .first()
            .firstOrNull { it.name == title }
            ?.id ?: repository.createAlbum(title)
}
