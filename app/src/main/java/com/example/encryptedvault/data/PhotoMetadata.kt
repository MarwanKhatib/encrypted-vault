package com.example.encryptedvault.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Sensitive per-item metadata. It is serialised with [PhotoMetadataCodec] and stored ONLY in
 * encrypted form (`PhotoEntity.metaEnc`), so file names and folders never sit in the database in clear text.
 */
data class PhotoMetadata(
    /** Original file name, e.g. `IMG_0001.jpg`. Empty when the source did not provide one. */
    val name: String = "",
    /** MediaStore-style relative folder the item came from, e.g. `DCIM/Camera`; null if unknown. */
    val sourceFolder: String? = null,
    /** User-written caption ("Add a Caption" in the details sheet). */
    val caption: String = "",
)

/**
 * Compact versioned binary format for [PhotoMetadata] (pure JDK, so it is unit-testable off-device).
 *
 * Layout history (the reader accepts every version, the writer always emits [VERSION]):
 *  - v1: name, folder, faces-scanned flag, face list
 *  - v2: v1 + caption
 *  - v3: v2 + one embedding per face
 *  - v4: name, folder, caption. The face feature was removed, so its fields are skipped on read and dropped on the next write.
 */
object PhotoMetadataCodec {
    private const val VERSION = 4

    /** Serialises [meta] using the current layout (v4). */
    fun encode(meta: PhotoMetadata): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(VERSION)
            out.writeUTF(meta.name)
            out.writeBoolean(meta.sourceFolder != null)
            meta.sourceFolder?.let(out::writeUTF)
            out.writeUTF(meta.caption)
        }
        return bytes.toByteArray()
    }

    /**
     * Parses any supported layout.
     * @throws IllegalArgumentException when the version byte is unknown (a corrupted or newer payload).
     */
    fun decode(data: ByteArray): PhotoMetadata =
        DataInputStream(ByteArrayInputStream(data)).use { input ->
            val version = input.readUnsignedByte()
            require(version in 1..VERSION) { "Unsupported metadata version $version" }
            val name = input.readUTF()
            val folder = if (input.readBoolean()) input.readUTF() else null
            if (version <= 3) skipLegacyFaces(input, version)
            val caption = if (version >= 2) input.readUTF() else ""
            PhotoMetadata(name, folder, caption)
        }

    /** Consumes the face block that v1-v3 wrote between the folder and the caption. */
    private fun skipLegacyFaces(
        input: DataInputStream,
        version: Int,
    ) {
        input.readBoolean() // facesScanned flag
        repeat(input.readUnsignedShort()) {
            repeat(4) { input.readFloat() } // left, top, right, bottom
            input.readLong() // person id
            if (version >= 3) repeat(input.readUnsignedShort()) { input.readFloat() } // embedding
        }
    }
}
