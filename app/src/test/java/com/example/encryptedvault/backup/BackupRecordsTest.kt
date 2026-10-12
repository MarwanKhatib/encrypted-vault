package com.example.encryptedvault.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class BackupRecordsTest {
    private fun record(
        name: String = "Beach 🌅.jpg",
        folder: String? = "DCIM/Camera",
        albums: List<String> = listOf("Trips", "Ünïcödé"),
    ) = BackupRecord(
        hash = "ab".repeat(32),
        mimeType = "image/jpeg",
        takenAt = 1_700_000_000_000,
        sizeBytes = 2_048,
        durationMs = 0,
        width = 4032,
        height = 3024,
        favorite = true,
        hidden = false,
        trashed = true,
        name = name,
        folder = folder,
        caption = "Sunset, ünïcode",
        albums = albums,
    )

    private fun encode(vararg records: BackupRecord): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            BackupRecords.writeCount(out, records.size)
            records.forEach { BackupRecords.write(out, it) }
        }
        return bytes.toByteArray()
    }

    @Test fun recordsRoundTripIncludingUnicodeAndNullFolder() {
        val input = DataInputStream(ByteArrayInputStream(encode(record(), record(folder = null, albums = emptyList()))))
        val count = BackupRecords.readCount(input)
        assertEquals(2, count)
        assertEquals(record(), BackupRecords.read(input))
        assertEquals(record(folder = null, albums = emptyList()), BackupRecords.read(input))
    }

    @Test fun emptyFolderReadsAsNull() {
        val read = BackupRecords.read(DataInputStream(ByteArrayInputStream(encode(record(folder = "")).drop(4).toByteArray())))
        assertNull(read.folder)
    }

    @Test fun countOutsideTheAllowedRangeIsRejected() {
        val negative = DataInputStream(ByteArrayInputStream(byteArrayOf(-1, -1, -1, -1)))
        assertThrows(IOException::class.java) { BackupRecords.readCount(negative) }
        val tooMany = DataInputStream(ByteArrayInputStream(intBytes(BackupRecords.MAX_ALBUMS_PER_ITEM + 1)))
        assertThrows(IOException::class.java) { BackupRecords.readCount(tooMany, BackupRecords.MAX_ALBUMS_PER_ITEM) }
    }

    @Test fun textLongerThanTheLimitIsRejectedBeforeAllocation() {
        val bogus = DataInputStream(ByteArrayInputStream(intBytes(BackupRecords.MAX_TEXT_BYTES + 1)))
        assertThrows(IOException::class.java) { BackupRecords.read(bogus) }
    }

    @Test fun truncatedRecordIsRejected() {
        val bytes = encode(record())
        val cut = DataInputStream(ByteArrayInputStream(bytes.copyOf(bytes.size - 10)))
        BackupRecords.readCount(cut)
        assertThrows(IOException::class.java) { BackupRecords.read(cut) }
    }

    @Test fun copyExactlyStopsAtTheRequestedLength() {
        val out = ByteArrayOutputStream()
        val input = ByteArrayInputStream(ByteArray(200_000) { it.toByte() })
        BackupRecords.copyExactly(input, out, 150_000)
        assertEquals(150_000, out.size())
        assertEquals(50_000L, input.available().toLong())
    }

    @Test fun copyExactlyDiscardsWhenNoSinkIsGiven() {
        val input = ByteArrayInputStream(ByteArray(10) { 7 })
        BackupRecords.copyExactly(input, null, 4)
        assertEquals(6, input.available())
    }

    @Test fun copyExactlyFailsOnShortInput() {
        assertThrows(IOException::class.java) {
            BackupRecords.copyExactly(ByteArrayInputStream(ByteArray(3)), ByteArrayOutputStream(), 4)
        }
    }

    @Test fun contentBytesAreNotTouchedByTheRecordCodec() {
        val content = byteArrayOf(1, 2, 3, 4)
        val stream = ByteArrayOutputStream()
        DataOutputStream(stream).use {
            BackupRecords.write(it, record(albums = emptyList()))
            it.write(content)
        }
        val input = DataInputStream(ByteArrayInputStream(stream.toByteArray()))
        BackupRecords.read(input)
        val out = ByteArrayOutputStream()
        BackupRecords.copyExactly(input, out, 4)
        assertArrayEquals(content, out.toByteArray())
    }

    private fun intBytes(value: Int) = ByteArrayOutputStream().also { DataOutputStream(it).use { o -> o.writeInt(value) } }.toByteArray()
}
