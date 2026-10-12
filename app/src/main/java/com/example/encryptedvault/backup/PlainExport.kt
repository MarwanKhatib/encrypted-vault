package com.example.encryptedvault.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.encryptedvault.VaultApp
import com.example.encryptedvault.crypto.VaultFiles
import com.example.encryptedvault.data.VaultItem
import com.example.encryptedvault.data.VaultRepository
import com.example.encryptedvault.data.VaultStorage
import com.example.encryptedvault.media.MediaTransfer
import com.example.encryptedvault.media.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import javax.crypto.SecretKey

/**
 * Plain (unencrypted) export to a folder the user picks (ADR-008). Every non-trashed item is written as an ordinary file, and
 * `manifest.csv` records the names, folders, dates, flags, captions and albums that the files alone do not carry.
 *
 * This writes plaintext to shared storage on purpose; the caller shows the warning. Location is removed from JPEG copies when
 * [stripLocation] is set, using a temporary copy in the share folder, which lock and start-up already sweep.
 */
object PlainExport {
    /** Counts shown to the user after an export; [lastError] is the reason for the most recent failed file, if any. */
    data class Report(
        val written: Int,
        val failed: Int,
        val lastError: String?,
    )

    private const val MANIFEST_NAME = "manifest.csv"
    private val MANIFEST_HEADER = listOf("file", "original_name", "folder", "taken_at_epoch_ms", "favorite", "hidden", "caption", "albums")
    private val CSV_SPECIAL = setOf(',', '"', '\n', '\r')

    /** Quotes a CSV field when it holds a separator, quote or line break, doubling any quotes inside it (RFC 4180). */
    fun csvField(value: String): String = if (value.any { it in CSV_SPECIAL }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    /** Builds the manifest text: a header row and one row per exported file. */
    fun manifest(rows: List<List<String>>): String =
        (listOf(MANIFEST_HEADER) + rows).joinToString(separator = "\r\n", postfix = "\r\n") { row -> row.joinToString(",") { csvField(it) } }

    /** Makes a display name safe for a document provider: path separators become underscores. */
    internal fun safeName(name: String): String =
        name
            .replace('/', '_')
            .replace('\\', '_')
            .trim()
            .ifEmpty { "item" }

    /** Writes the exported files and the manifest into the folder behind [treeUri] and returns the counts. */
    suspend fun export(
        context: Context,
        treeUri: Uri,
        stripLocation: Boolean,
    ): Report {
        val key = checkNotNull(VaultApp.sessionKey) { "Vault is locked" }
        val repository = VaultRepository.get(context)
        val items = repository.items.first().filter { !it.isTrashed }
        val albums = repository.albums.first()
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        var written = 0
        var failed = 0
        var lastError: String? = null
        val rows = mutableListOf<List<String>>()
        withContext(Dispatchers.IO) {
            for (item in items) {
                val fileName = safeName(item.name)
                val titles = albums.filter { item.id in it.itemIds }.map { it.name }
                try {
                    val target = createFile(context, parent, item.mimeType, fileName)
                    writeItem(context, key, item, target, stripLocation)
                    rows += manifestRow(fileName, item, titles)
                    written++
                } catch (e: IOException) {
                    failed++
                    lastError = e.message
                }
            }
            val manifestUri = createFile(context, parent, "text/csv", MANIFEST_NAME)
            context.contentResolver.openOutputStream(manifestUri)?.use { it.write(manifest(rows).toByteArray(Charsets.UTF_8)) }
        }
        return Report(written, failed, lastError)
    }

    /** One manifest row for an exported item, in the order of [MANIFEST_HEADER]. */
    private fun manifestRow(
        fileName: String,
        item: VaultItem,
        albumTitles: List<String>,
    ): List<String> =
        listOf(
            fileName,
            item.name,
            item.sourceFolder.orEmpty(),
            item.takenAt.toString(),
            item.isFavorite.toString(),
            item.isHidden.toString(),
            item.caption,
            albumTitles.joinToString("; "),
        )

    /** Creates a document in [parent]; the provider may rename it if the name is taken. */
    private fun createFile(
        context: Context,
        parent: Uri,
        mimeType: String,
        name: String,
    ): Uri =
        DocumentsContract.createDocument(context.contentResolver, parent, mimeType, name)
            ?: throw IOException("The folder did not accept $name")

    /** Writes one item's plaintext to [target]. JPEG copies go through a temporary file so their location can be removed first. */
    private fun writeItem(
        context: Context,
        key: SecretKey,
        item: VaultItem,
        target: Uri,
        stripLocation: Boolean,
    ) {
        val source = VaultStorage.content(context, item.hash)
        val sink = context.contentResolver.openOutputStream(target) ?: throw IOException("Couldn't open the destination")
        sink.use { out ->
            if (stripLocation && item.mimeType == "image/jpeg") {
                copyWithoutLocation(context, key, source, item.hash, out)
            } else {
                VaultFiles.decryptStream(key, source).use { it.copyTo(out) }
            }
        }
    }

    /** Decrypts a JPEG into the share folder, removes its location tags, then copies the cleaned file to [out]. */
    private fun copyWithoutLocation(
        context: Context,
        key: SecretKey,
        source: File,
        hash: String,
        out: OutputStream,
    ) {
        val dir = File(context.cacheDir, MediaTransfer.SHARE_DIR).apply { mkdirs() }
        val temp = File(dir, "export-$hash.jpg")
        try {
            VaultFiles.decryptStream(key, source).use { input -> temp.outputStream().use { input.copyTo(it) } }
            Thumbnails.stripSensitiveExif(temp)
            temp.inputStream().use { it.copyTo(out) }
        } finally {
            temp.delete()
        }
    }
}
