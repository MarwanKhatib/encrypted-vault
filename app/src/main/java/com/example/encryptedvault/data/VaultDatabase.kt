package com.example.encryptedvault.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** One encrypted item. Everything identifying (name, folder, caption) lives encrypted in [metaEnc]. */
@Entity(tableName = "photos", indices = [Index("hash", unique = true)])
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** SHA-256 of plaintext; dedup key and on-disk file name. */
    val hash: String,
    val mimeType: String,
    /** Taken-at time in epoch millis. */
    val timestamp: Long,
    /** `pending` until both `.enc` and `.thumb.enc` are committed, then `complete` (Gap 5). */
    val status: String,
    @ColumnInfo(defaultValue = "0") val size: Long = 0,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    /** Epoch millis when moved to Recently Deleted; 0 when live. */
    @ColumnInfo(defaultValue = "0") val deletedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val durationMs: Long = 0,
    /** Hidden items are removed from the Library and shown only in the Hidden album. */
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
    /** Pixel size (0 when unknown); used to recognise panoramas. */
    @ColumnInfo(defaultValue = "0") val width: Int = 0,
    @ColumnInfo(defaultValue = "0") val height: Int = 0,
    /** AES-GCM encrypted [PhotoMetadata]; null until the worker writes it. */
    val metaEnc: ByteArray? = null,
) {
    override fun equals(other: Any?) = other is PhotoEntity && id == other.id && hash == other.hash &&
        timestamp == other.timestamp && status == other.status && size == other.size &&
        favorite == other.favorite && deletedAt == other.deletedAt && durationMs == other.durationMs &&
        hidden == other.hidden && width == other.width && height == other.height &&
        metaEnc.contentEquals(other.metaEnc)

    override fun hashCode() = 31 * id.hashCode() + hash.hashCode()
}

/** Room row of an album. The title is stored encrypted ([nameEnc]), so album names never sit in clear text. */
@Entity(tableName = "albums")
class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Encrypted album title. */
    val nameEnc: ByteArray,
)

/** Join row linking one item to one album; (albumId, photoId) is the composite primary key. */
@Entity(tableName = "album_items", primaryKeys = ["albumId", "photoId"])
data class AlbumItemEntity(val albumId: Long, val photoId: Long)

/** Queries and updates for the `photos` table. Suspend queries run off the main thread (Room's default). */
@Dao
interface PhotoDao {
    /** The completed row whose plaintext SHA-256 is [hash], or null. This is the dedup check used by imports. */
    @Query("SELECT * FROM photos WHERE hash = :hash AND status = 'complete' LIMIT 1")
    suspend fun findCompleteByHash(hash: String): PhotoEntity?

    /** Live list of every completed item, newest first. Emits again whenever the table changes. */
    @Query("SELECT * FROM photos WHERE status = 'complete' ORDER BY timestamp DESC")
    fun observeCompleted(): Flow<List<PhotoEntity>>

    /** The row with primary key [id], or null. */
    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun byId(id: Long): PhotoEntity?

    /** One-shot snapshot of every completed row, for callers where a Flow would be wasteful. */
    @Query("SELECT * FROM photos WHERE status = 'complete'")
    suspend fun allCompleted(): List<PhotoEntity>

    /** Inserts a row, or replaces the one with the same id or hash, and returns its row id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(photo: PhotoEntity): Long

    /** Sets the favourite flag on all [ids] in one statement. */
    @Query("UPDATE photos SET favorite = :favorite WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<Long>, favorite: Boolean)

    /** [deletedAt] = 0 restores from Recently Deleted. */
    @Query("UPDATE photos SET deletedAt = :deletedAt WHERE id IN (:ids)")
    suspend fun setDeletedAt(ids: List<Long>, deletedAt: Long)

    /** Sets the hidden flag on all [ids]; hidden items leave the Library and show only in the Hidden collection. */
    @Query("UPDATE photos SET hidden = :hidden WHERE id IN (:ids)")
    suspend fun setHidden(ids: List<Long>, hidden: Boolean)

    /** Replaces only the encrypted metadata blob of row [id]. */
    @Query("UPDATE photos SET metaEnc = :metaEnc WHERE id = :id")
    suspend fun updateMeta(id: Long, metaEnc: ByteArray)

    /** Rows moved to Recently Deleted before [cutoff] (epoch ms): the candidates for the 30-day purge. */
    @Query("SELECT * FROM photos WHERE deletedAt > 0 AND deletedAt < :cutoff")
    suspend fun trashedBefore(cutoff: Long): List<PhotoEntity>

    /** Deletes rows by id. The caller removes album links and the encrypted files. */
    @Query("DELETE FROM photos WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

/** Queries and updates for the `albums` and `album_items` tables. */
@Dao
interface AlbumDao {
    /** Live list of all albums (titles still encrypted). */
    @Query("SELECT * FROM albums")
    fun observeAlbums(): Flow<List<AlbumEntity>>

    /** Live list of every album membership row. */
    @Query("SELECT * FROM album_items")
    fun observeLinks(): Flow<List<AlbumItemEntity>>

    /** Inserts an album and returns its id. */
    @Insert
    suspend fun insertAlbum(album: AlbumEntity): Long

    /** Replaces the encrypted title of album [id]. */
    @Query("UPDATE albums SET nameEnc = :nameEnc WHERE id = :id")
    suspend fun rename(id: Long, nameEnc: ByteArray)

    /** Deletes the album row only. Remove its links first with [deleteLinksOfAlbum]; the items themselves are untouched. */
    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun deleteAlbum(id: Long)

    /** Removes every membership row of one album. */
    @Query("DELETE FROM album_items WHERE albumId = :albumId")
    suspend fun deleteLinksOfAlbum(albumId: Long)

    /** Adds memberships; rows that already exist are ignored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addLinks(links: List<AlbumItemEntity>)

    /** Removes the given items from one album. */
    @Query("DELETE FROM album_items WHERE albumId = :albumId AND photoId IN (:photoIds)")
    suspend fun removeLinks(albumId: Long, photoIds: List<Long>)

    /** Removes the given items from every album (called before deleting them). */
    @Query("DELETE FROM album_items WHERE photoId IN (:photoIds)")
    suspend fun removeLinksOfItems(photoIds: List<Long>)
}

@Database(
    entities = [PhotoEntity::class, AlbumEntity::class, AlbumItemEntity::class],
    version = 5,
    exportSchema = true,
)
/** Room database of the vault (schema v5). Every version is exported to `app/schemas` and covered by `DatabaseMigrationTest`; never add a destructive fallback. */
abstract class VaultDatabase : RoomDatabase() {
    /** Data access for the `photos` table. */
    abstract fun photoDao(): PhotoDao
    /** Data access for albums and album membership. */
    abstract fun albumDao(): AlbumDao

    companion object {
        // Every schema change needs a real Migration. NEVER add fallbackToDestructiveMigration() (Gap 11).

        /** v1 -> v2: file name, size and favourite flag. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN name TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE photos ADD COLUMN size INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v2 -> v3: moves the clear-text `name` column into encrypted metadata (dropped here, since the
         * key is unavailable during migration), adds trash/duration/metadata, people and albums.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE photos_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, hash TEXT NOT NULL, " +
                        "mimeType TEXT NOT NULL, timestamp INTEGER NOT NULL, status TEXT NOT NULL, " +
                        "size INTEGER NOT NULL DEFAULT 0, favorite INTEGER NOT NULL DEFAULT 0, " +
                        "deletedAt INTEGER NOT NULL DEFAULT 0, durationMs INTEGER NOT NULL DEFAULT 0, metaEnc BLOB)"
                )
                db.execSQL(
                    "INSERT INTO photos_new (id, hash, mimeType, timestamp, status, size, favorite) " +
                        "SELECT id, hash, mimeType, timestamp, status, size, favorite FROM photos"
                )
                db.execSQL("DROP TABLE photos")
                db.execSQL("ALTER TABLE photos_new RENAME TO photos")
                db.execSQL("CREATE UNIQUE INDEX index_photos_hash ON photos (hash)")
                db.execSQL("CREATE TABLE people (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, nameEnc BLOB NOT NULL)")
                db.execSQL("CREATE TABLE albums (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, nameEnc BLOB NOT NULL)")
                db.execSQL(
                    "CREATE TABLE album_items (albumId INTEGER NOT NULL, photoId INTEGER NOT NULL, " +
                        "PRIMARY KEY (albumId, photoId))"
                )
            }
        }

        /** v3 -> v4: hidden flag and pixel size. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN width INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN height INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v4 -> v5: the People feature was removed, so its table goes away. Photos and albums are untouched. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS people")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        @Volatile private var instance: VaultDatabase? = null

        /** Single instance so Flow invalidation reaches the UI when workers write. */
        fun get(context: Context): VaultDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, VaultDatabase::class.java, DB_NAME)
                .addMigrations(*MIGRATIONS)
                .build().also { instance = it }
        }

        /** Closes the database and deletes `vault.db` with its WAL/SHM files (used by the duress wipe). The next [get] creates an empty one. */
        fun destroy(context: Context) = synchronized(this) {
            instance?.close()
            instance = null
            context.applicationContext.deleteDatabase(DB_NAME)
        }

        private const val DB_NAME = "vault.db"
    }
}
