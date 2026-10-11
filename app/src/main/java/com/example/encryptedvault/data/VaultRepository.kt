package com.example.encryptedvault.data

import android.content.Context
import androidx.compose.runtime.snapshotFlow
import com.example.encryptedvault.VaultApp
import com.example.encryptedvault.crypto.KeyHierarchy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey

/**
 * The only place that turns database rows into [VaultItem]s and back.
 *
 * Sensitive fields (file name, folder, caption, album titles) are encrypted with the in-memory
 * master key, so while the vault is locked every flow emits an empty list and nothing readable
 * can leak through the UI layer.
 */
class VaultRepository private constructor(private val context: Context, private val db: VaultDatabase) {

    // ---- Observation ------------------------------------------------------------------------

    /** Every completed item, including those in Recently Deleted. Empty while the vault is locked. */
    val items: Flow<List<VaultItem>> = whenUnlocked { key ->
        db.photoDao().observeCompleted().map { rows -> rows.map { it.toItem(key) } }
    }

    /** All albums with their member ids, joined from the albums and album_items tables. */
    val albums: Flow<List<Album>> = whenUnlocked { key ->
        combine(db.albumDao().observeAlbums(), db.albumDao().observeLinks()) { albums, links ->
            val idsByAlbum = links.groupBy({ it.albumId }, { it.photoId })
            albums.map { Album(it.id, decryptText(key, it.nameEnc), idsByAlbum[it.id].orEmpty().toSet()) }
        }
    }

    // ---- Item mutations ---------------------------------------------------------------------

    /** Marks or un-marks [ids] as favourite. */
    suspend fun setFavorite(ids: List<Long>, favorite: Boolean) = db.photoDao().setFavorite(ids, favorite)

    /** Hides [ids] from the Library (they stay reachable through the Hidden album). */
    suspend fun setHidden(ids: List<Long>, hidden: Boolean) = db.photoDao().setHidden(ids, hidden)

    /** Stores a trimmed user caption inside the encrypted metadata of one item. */
    suspend fun setCaption(itemId: Long, caption: String) = updateMetadata(itemId) { it.copy(caption = caption.trim()) }

    /** Moves items to Recently Deleted (recoverable for [TRASH_RETENTION_DAYS] days). */
    suspend fun moveToTrash(ids: List<Long>) = db.photoDao().setDeletedAt(ids, System.currentTimeMillis())

    /** Brings items back from Recently Deleted. */
    suspend fun restoreFromTrash(ids: List<Long>) = db.photoDao().setDeletedAt(ids, 0)

    /** Permanently removes items: database rows, album links and every encrypted file. */
    suspend fun deleteForever(items: List<VaultItem>) {
        if (items.isEmpty()) return
        val ids = items.map { it.id }
        db.albumDao().removeLinksOfItems(ids)
        db.photoDao().deleteByIds(ids)
        items.forEach { VaultStorage.deleteAll(context, it.hash) }
    }

    /** Permanently removes items that have been in Recently Deleted for longer than [retentionDays]. */
    suspend fun purgeExpiredTrash(retentionDays: Int = TRASH_RETENTION_DAYS) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        val expired = db.photoDao().trashedBefore(cutoff)
        if (expired.isEmpty()) return
        db.albumDao().removeLinksOfItems(expired.map { it.id })
        db.photoDao().deleteByIds(expired.map { it.id })
        expired.forEach { VaultStorage.deleteAll(context, it.hash) }
    }

    /** Reads, transforms and re-encrypts the metadata of one item. A no-op while the vault is locked. */
    suspend fun updateMetadata(itemId: Long, transform: (PhotoMetadata) -> PhotoMetadata) {
        val key = VaultApp.sessionKey ?: return
        val entity = db.photoDao().byId(itemId) ?: return
        val updated = transform(entity.decryptMetadata(key))
        db.photoDao().updateMeta(itemId, encryptMetadata(key, updated))
    }

    // ---- Albums -----------------------------------------------------------------------------

    /** Creates an album with an encrypted title and returns its id, or -1 while the vault is locked. */
    suspend fun createAlbum(name: String): Long {
        val key = VaultApp.sessionKey ?: return -1
        return db.albumDao().insertAlbum(AlbumEntity(nameEnc = encryptText(key, name.trim())))
    }

    /** Renames an album (the new title is stored encrypted). */
    suspend fun renameAlbum(id: Long, name: String) {
        val key = VaultApp.sessionKey ?: return
        db.albumDao().rename(id, encryptText(key, name.trim()))
    }

    /** Deletes the album only; the items stay in the library. */
    suspend fun deleteAlbum(id: Long) {
        db.albumDao().deleteLinksOfAlbum(id)
        db.albumDao().deleteAlbum(id)
    }

    /** Adds [itemIds] to an album; items already in it are ignored. */
    suspend fun addToAlbum(albumId: Long, itemIds: List<Long>) =
        db.albumDao().addLinks(itemIds.map { AlbumItemEntity(albumId, it) })

    /** Removes [itemIds] from an album without deleting them. */
    suspend fun removeFromAlbum(albumId: Long, itemIds: List<Long>) = db.albumDao().removeLinks(albumId, itemIds)

    // ---- Mapping helpers --------------------------------------------------------------------

    /** Decrypts the metadata blob; a missing or undecodable blob yields empty metadata instead of failing the whole list. */
    private fun PhotoEntity.decryptMetadata(key: SecretKey): PhotoMetadata =
        metaEnc?.let { runCatching { PhotoMetadataCodec.decode(KeyHierarchy.gcmDecrypt(key, it)) }.getOrNull() }
            ?: PhotoMetadata()

    /** Combines the clear columns with the decrypted metadata into the UI model. */
    private fun PhotoEntity.toItem(key: SecretKey): VaultItem {
        val meta = decryptMetadata(key)
        return VaultItem(
            id = id, hash = hash, mimeType = mimeType, takenAt = timestamp, sizeBytes = size,
            durationMs = durationMs, isFavorite = favorite, trashedAt = deletedAt.takeIf { it > 0 },
            name = meta.name, sourceFolder = meta.sourceFolder,
            isHidden = hidden, width = width, height = height, caption = meta.caption,
        )
    }

    /** Re-subscribes to [source] every time the session key appears, changes or disappears (lock/unlock). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> whenUnlocked(source: (SecretKey) -> Flow<List<T>>): Flow<List<T>> =
        snapshotFlow { VaultApp.sessionKey }.flatMapLatest { key -> if (key == null) flowOf(emptyList()) else source(key) }

    companion object {
        /** How long an item stays recoverable in Recently Deleted. */
        const val TRASH_RETENTION_DAYS = 30

        @Volatile private var instance: VaultRepository? = null

        /** Process-wide singleton so every screen observes the same Room invalidation tracker. */
        fun get(context: Context): VaultRepository = instance ?: synchronized(this) {
            instance ?: VaultRepository(context.applicationContext, VaultDatabase.get(context)).also { instance = it }
        }

        /** Drops the singleton so the next [get] binds to a fresh database (after a wipe the old instance points at a closed one). */
        fun reset() = synchronized(this) { instance = null }

        /** Serialises and encrypts [meta] with [key] (used by the import worker as well). */
        fun encryptMetadata(key: SecretKey, meta: PhotoMetadata): ByteArray =
            KeyHierarchy.gcmEncrypt(key, PhotoMetadataCodec.encode(meta))

        /** Encrypts a short UTF-8 string (album titles). */
        private fun encryptText(key: SecretKey, text: String) = KeyHierarchy.gcmEncrypt(key, text.toByteArray())

        /** Inverse of [encryptText]; a bad blob decodes to an empty string. */
        private fun decryptText(key: SecretKey, blob: ByteArray) =
            runCatching { String(KeyHierarchy.gcmDecrypt(key, blob)) }.getOrDefault("")
    }
}
