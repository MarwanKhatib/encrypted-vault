package com.example.encryptedvault.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * One item's record in a backup. Its content bytes follow the record in the stream and are exactly [sizeBytes] long.
 * [folder] is null when the item has no source folder. [trashedAt] is the epoch millis the item entered Recently Deleted, or null
 * when it is live, so a restore keeps its original 30-day window.
 */
data class BackupRecord(
    val hash: String,
    val mimeType: String,
    val takenAt: Long,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val favorite: Boolean,
    val hidden: Boolean,
    val trashedAt: Long?,
    val name: String,
    val folder: String?,
    val caption: String,
    val albums: List<String>,
)

/**
 * Binary layout of backup records (ADR-007, format version 2). Works only on plain streams, so the format is unit-tested without
 * Android. Text is UTF-8 with a 4-byte length; every length is bounded on read so a damaged file cannot ask for gigabytes.
 *
 * Version 1 stored only a trashed flag, so restoring it cannot recover when the item was trashed; [read] uses the restore time.
 */
object BackupRecords {
    const val MAX_TEXT_BYTES = 1 shl 20
    const val MAX_ALBUMS_PER_ITEM = 4096

    /** Format version written by [write]. */
    const val CURRENT_VERSION = 2

    private const val COPY_BUFFER_BYTES = 64 * 1024

    /** Writes a record count (a 4-byte integer). */
    fun writeCount(
        out: DataOutputStream,
        count: Int,
    ) = out.writeInt(count)

    /** Reads a record count, rejecting negative values and anything above [max] so a damaged file cannot request huge allocations. */
    fun readCount(
        input: DataInputStream,
        max: Int = Int.MAX_VALUE,
    ): Int {
        val count = input.readInt()
        if (count !in 0..max) throw IOException("Corrupt backup record")
        return count
    }

    /** Writes one item record in the current format. The content bytes are written by the caller. */
    fun write(
        out: DataOutputStream,
        record: BackupRecord,
    ) {
        out.writeText(record.hash)
        out.writeText(record.mimeType)
        out.writeLong(record.takenAt)
        out.writeLong(record.sizeBytes)
        out.writeLong(record.durationMs)
        out.writeInt(record.width)
        out.writeInt(record.height)
        out.writeBoolean(record.favorite)
        out.writeBoolean(record.hidden)
        out.writeLong(record.trashedAt ?: NOT_TRASHED)
        out.writeText(record.name)
        out.writeText(record.folder.orEmpty())
        out.writeText(record.caption)
        writeCount(out, record.albums.size)
        record.albums.forEach { out.writeText(it) }
    }

    /**
     * Reads one item record written in [version]. Version 1 records carry only a trashed flag, so a trashed item gets
     * [legacyTrashTime] as its trash time. The content bytes that follow the record are read by the caller.
     */
    fun read(
        input: DataInputStream,
        version: Int = CURRENT_VERSION,
        legacyTrashTime: Long = System.currentTimeMillis(),
    ): BackupRecord {
        val hash = input.readText()
        val mimeType = input.readText()
        val takenAt = input.readLong()
        val sizeBytes = input.readLong()
        val durationMs = input.readLong()
        val width = input.readInt()
        val height = input.readInt()
        val favorite = input.readBoolean()
        val hidden = input.readBoolean()
        val trashedAt =
            if (version >= 2) {
                input.readLong().takeIf { it != NOT_TRASHED }
            } else {
                legacyTrashTime.takeIf { input.readBoolean() }
            }
        return BackupRecord(
            hash = hash,
            mimeType = mimeType,
            takenAt = takenAt,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            width = width,
            height = height,
            favorite = favorite,
            hidden = hidden,
            trashedAt = trashedAt,
            name = input.readText(),
            folder = input.readText().ifEmpty { null },
            caption = input.readText(),
            albums = List(readCount(input, MAX_ALBUMS_PER_ITEM)) { input.readText() },
        )
    }

    /** Copies exactly [count] bytes from [input] to [out], or discards them when [out] is null. A short stream means the backup was truncated. */
    fun copyExactly(
        input: InputStream,
        out: OutputStream?,
        count: Long,
    ) {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var remaining = count
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw IOException("Backup is truncated")
            out?.write(buffer, 0, read)
            remaining -= read
        }
    }

    /** Writes a string as a 4-byte length followed by its UTF-8 bytes. */
    private fun DataOutputStream.writeText(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    /** Reads a string written by writeText; the length is bounded by MAX_TEXT_BYTES. */
    private fun DataInputStream.readText(): String {
        val bytes = ByteArray(readCount(this, MAX_TEXT_BYTES))
        readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    /** Stored in place of a trash time for live items (epoch millis are never zero for a real trash time). */
    private const val NOT_TRASHED = 0L
}
