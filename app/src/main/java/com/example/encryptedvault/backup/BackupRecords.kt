package com.example.encryptedvault.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * One item's record in a backup. Its content bytes follow the record in the stream and are exactly [sizeBytes] long.
 * [folder] is null when the item has no source folder.
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
    val trashed: Boolean,
    val name: String,
    val folder: String?,
    val caption: String,
    val albums: List<String>,
)

/**
 * Binary layout of backup records (ADR-007). Works only on plain streams, so the format is unit-tested without Android.
 * Text is UTF-8 with a 4-byte length; every length is bounded on read so a damaged file cannot ask for gigabytes.
 */
object BackupRecords {
    const val MAX_TEXT_BYTES = 1 shl 20
    const val MAX_ALBUMS_PER_ITEM = 4096
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

    /** Writes one item record: its clear fields, its metadata and its album titles. The content bytes are written by the caller. */
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
        out.writeBoolean(record.trashed)
        out.writeText(record.name)
        out.writeText(record.folder.orEmpty())
        out.writeText(record.caption)
        writeCount(out, record.albums.size)
        record.albums.forEach { out.writeText(it) }
    }

    /** Reads one item record written by [write]. The content bytes that follow it are read by the caller. */
    fun read(input: DataInputStream): BackupRecord =
        BackupRecord(
            hash = input.readText(),
            mimeType = input.readText(),
            takenAt = input.readLong(),
            sizeBytes = input.readLong(),
            durationMs = input.readLong(),
            width = input.readInt(),
            height = input.readInt(),
            favorite = input.readBoolean(),
            hidden = input.readBoolean(),
            trashed = input.readBoolean(),
            name = input.readText(),
            folder = input.readText().ifEmpty { null },
            caption = input.readText(),
            albums = List(readCount(input, MAX_ALBUMS_PER_ITEM)) { input.readText() },
        )

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
}
