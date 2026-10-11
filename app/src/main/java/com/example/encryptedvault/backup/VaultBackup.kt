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
import javax.crypto.SecretKey

/** Counts shown to the user after a restore. */
data class RestoreReport(val imported: Int, val skipped: Int, val failed: Int)

/**
 * Encrypted export and restore of the whole vault (ADR-007).
 *
 * Items are read decrypted and written under the backup passphrase, so the file restores on any device. Restore stages each
 * item's content and runs it through [ImportWorker] (which dedups by hash), then re-applies favourite, hidden, caption, trash
 * and album membership. Items already in the vault are skipped, never overwritten.
 */
object VaultBackup {
    private const val MAX_TEXT_BYTES = 1 shl 20
    private const val MAX_ALBUMS_PER_ITEM = 4096
    private const val COPY_BUFFER_BYTES = 64 * 1024

    /** Writes every item to [out] under [passphrase] and closes [out]. Returns the number of items written. */
    suspend fun export(context: Context, passphrase: CharArray, out: OutputStream): Int {
        val key = checkNotNull(VaultApp.sessionKey) { "Vault is locked" }
        val repository = VaultRepository.get(context)
        val items = repository.items.first()
        val albums = repository.albums.first()
        withContext(Dispatchers.IO) {
            DataOutputStream(BackupCrypto.encrypting(out, passphrase)).use { data ->
                data.writeInt(items.size)
                for (item in items) {
                    writeItem(data, context, key, item, albums.filter { item.id in it.itemIds }.map { it.name })
                }
            }
        }
        return items.size
    }

    /** Restores the backup in [input] into the unlocked vault and closes [input]. Throws [java.io.IOException] for a wrong passphrase or damaged file. */
    suspend fun restore(context: Context, passphrase: CharArray, input: InputStream): RestoreReport {
        checkNotNull(VaultApp.sessionKey) { "Vault is locked" }
        val repository = VaultRepository.get(context)
        val dao = VaultDatabase.get(context).photoDao()
        var imported = 0
        var skipped = 0
        var failed = 0
        withContext(Dispatchers.IO) {
            DataInputStream(BackupCrypto.decrypting(input, passphrase)).use { data ->
                repeat(readCount(data)) {
                    val item = readItem(data)
                    // The content must be consumed even when the item is skipped or fails, or the rest of the stream is misread.
                    when {
                        dao.findCompleteByHash(item.hash) != null -> {
                            copyExactly(data, null, item.sizeBytes)
                            skipped++
                        }
                        else -> {
                            val staged = stageContent(context, data, item)
                            if (staged != null && ImportWorker.importAndWait(context, staged)) {
                                applyState(context, repository, item)
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

    // ---- Export -----------------------------------------------------------------------------

    private fun writeItem(data: DataOutputStream, context: Context, key: SecretKey, item: VaultItem, albums: List<String>) {
        data.writeText(item.hash)
        data.writeText(item.mimeType)
        data.writeLong(item.takenAt)
        data.writeLong(item.sizeBytes)
        data.writeLong(item.durationMs)
        data.writeInt(item.width)
        data.writeInt(item.height)
        data.writeBoolean(item.isFavorite)
        data.writeBoolean(item.isHidden)
        data.writeBoolean(item.isTrashed)
        data.writeText(item.name)
        data.writeText(item.sourceFolder.orEmpty())
        data.writeText(item.caption)
        data.writeInt(albums.size)
        albums.forEach { data.writeText(it) }
        val copied = VaultFiles.decryptStream(key, VaultStorage.content(context, item.hash)).use { it.copyTo(data) }
        check(copied == item.sizeBytes) { "Stored size of ${item.hash} does not match its record" }
    }

    // ---- Restore ----------------------------------------------------------------------------

    /** One item's record as read from the backup. The content follows it in the stream and is not read here. */
    private class BackupItem(
        val hash: String,
        val mimeType: String,
        val takenAt: Long,
        val sizeBytes: Long,
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val favorite: Boolean,
        val hidden: Boolean,
        val trashed: Boolean,
        val name: String,
        val folder: String?,
        val caption: String,
        val albums: List<String>,
    ) {
        val extension: String get() = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
    }

    private fun readItem(data: DataInputStream) = BackupItem(
        hash = data.readText(),
        mimeType = data.readText(),
        takenAt = data.readLong(),
        sizeBytes = data.readLong(),
        durationMs = data.readLong(),
        width = data.readInt(),
        height = data.readInt(),
        favorite = data.readBoolean(),
        hidden = data.readBoolean(),
        trashed = data.readBoolean(),
        name = data.readText(),
        folder = data.readText().ifEmpty { null },
        caption = data.readText(),
        albums = List(readCount(data, MAX_ALBUMS_PER_ITEM)) { data.readText() },
    )

    /** Writes the content to a staged file in the import folder and checks its hash. Returns null if the content does not match. */
    private fun stageContent(context: Context, data: DataInputStream, item: BackupItem): MediaTransfer.StagedFile? {
        val dir = File(context.cacheDir, MediaTransfer.IMPORT_DIR).apply { mkdirs() }
        val file = File(dir, "${System.nanoTime()}.${item.extension}")
        try {
            file.outputStream().use { copyExactly(data, it, item.sizeBytes) }
        } catch (e: IOException) {
            file.delete()
            throw e
        }
        if (VaultFiles.sha256(file) == item.hash) {
            return MediaTransfer.StagedFile(file.path, SourceInfo(item.name, item.folder, item.takenAt))
        }
        file.delete()
        return null
    }

    /** Re-applies the state that the import worker does not carry: favourite, hidden, caption, trash and albums. */
    private suspend fun applyState(context: Context, repository: VaultRepository, item: BackupItem) {
        val id = VaultDatabase.get(context).photoDao().findCompleteByHash(item.hash)?.id ?: return
        if (item.favorite) repository.setFavorite(listOf(id), true)
        if (item.hidden) repository.setHidden(listOf(id), true)
        if (item.caption.isNotEmpty()) repository.setCaption(id, item.caption)
        if (item.trashed) repository.moveToTrash(listOf(id))
        item.albums.forEach { title -> repository.addToAlbum(albumIdFor(repository, title), listOf(id)) }
    }

    private suspend fun albumIdFor(repository: VaultRepository, title: String): Long =
        repository.albums.first().firstOrNull { it.name == title }?.id ?: repository.createAlbum(title)

    // ---- Wire helpers -----------------------------------------------------------------------

    private fun DataOutputStream.writeText(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readText(): String {
        val bytes = ByteArray(readCount(this, MAX_TEXT_BYTES))
        readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readCount(data: DataInputStream, max: Int = Int.MAX_VALUE): Int {
        val count = data.readInt()
        if (count !in 0..max) throw IOException("Corrupt backup record")
        return count
    }

    /** Copies exactly [count] bytes from [input] to [out], or discards them when [out] is null. A short stream means the backup was truncated. */
    private fun copyExactly(input: InputStream, out: OutputStream?, count: Long) {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var remaining = count
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw IOException("Backup is truncated")
            out?.write(buffer, 0, read)
            remaining -= read
        }
    }
}
