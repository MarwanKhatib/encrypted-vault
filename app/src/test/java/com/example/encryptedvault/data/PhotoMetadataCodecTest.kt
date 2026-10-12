package com.example.encryptedvault.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class PhotoMetadataCodecTest {
    /** Builds a legacy payload the way older app versions wrote it, so the compatibility tests do not depend on the current writer. */
    private fun legacy(build: DataOutputStream.() -> Unit): ByteArray = ByteArrayOutputStream().also { raw -> DataOutputStream(raw).use(build) }.toByteArray()

    @Test
    fun roundTripKeepsEveryField() {
        val meta = PhotoMetadata(name = "IMG_001.jpg", sourceFolder = "DCIM/Camera", caption = "Sunset at the lake 🌅")
        assertEquals(meta, PhotoMetadataCodec.decode(PhotoMetadataCodec.encode(meta)))
    }

    @Test
    fun nullFolderSurvives() {
        assertNull(PhotoMetadataCodec.decode(PhotoMetadataCodec.encode(PhotoMetadata(name = "a.png"))).sourceFolder)
    }

    @Test
    fun unicodeNamesSurvive() {
        val meta = PhotoMetadata(name = "صورة_📷.jpg", sourceFolder = "Pictures/عائلة")
        assertEquals(meta, PhotoMetadataCodec.decode(PhotoMetadataCodec.encode(meta)))
    }

    @Test
    fun version1DataDecodesWithEmptyCaption() {
        val bytes =
            legacy {
                writeByte(1)
                writeUTF("old.jpg")
                writeBoolean(true)
                writeUTF("DCIM/Camera")
                writeBoolean(true)
                writeShort(0)
            }
        assertEquals(PhotoMetadata(name = "old.jpg", sourceFolder = "DCIM/Camera"), PhotoMetadataCodec.decode(bytes))
    }

    @Test
    fun version2FacesAreSkippedAndCaptionIsKept() {
        val bytes =
            legacy {
                writeByte(2)
                writeUTF("v2.jpg")
                writeBoolean(false)
                writeBoolean(true)
                writeShort(1)
                writeFloat(0.1f)
                writeFloat(0.2f)
                writeFloat(0.3f)
                writeFloat(0.4f)
                writeLong(9)
                writeUTF("hello")
            }
        assertEquals(PhotoMetadata(name = "v2.jpg", caption = "hello"), PhotoMetadataCodec.decode(bytes))
    }

    @Test
    fun version3EmbeddingsAreSkippedAndCaptionIsKept() {
        val bytes =
            legacy {
                writeByte(3)
                writeUTF("v3.jpg")
                writeBoolean(true)
                writeUTF("DCIM")
                writeBoolean(true)
                writeShort(2)
                repeat(2) {
                    repeat(4) { writeFloat(0.5f) }
                    writeLong(-1)
                    writeShort(3)
                    repeat(3) { writeFloat(0.25f) }
                }
                writeUTF("kept")
            }
        assertEquals(PhotoMetadata(name = "v3.jpg", sourceFolder = "DCIM", caption = "kept"), PhotoMetadataCodec.decode(bytes))
    }

    @Test
    fun unknownVersionIsRejected() {
        val bytes = PhotoMetadataCodec.encode(PhotoMetadata()).also { it[0] = 99 }
        assertThrows(IllegalArgumentException::class.java) { PhotoMetadataCodec.decode(bytes) }
    }
}
