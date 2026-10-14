package com.example.encryptedvault.backup

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File

/**
 * Password-protected zip that carries one encrypted vault backup (.evbk) as its only entry (ADR-014).
 * The zip moves to another phone and opens with the same password. The zip layer is AES-256, but the zip standard derives its key
 * weakly; the inner .evbk keeps its own passphrase encryption, so the zip password alone does not expose the photos.
 */
object ZipBackup {
    const val ENTRY = "vault-backup.evbk"

    /** Writes [evbk] into a new AES-256 zip at [zip], protected with [password]. Replaces an existing file. */
    fun write(
        evbk: File,
        zip: File,
        password: CharArray,
    ) {
        zip.delete()
        val params =
            ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                // The .evbk is already encrypted; compressing it gains nothing.
                compressionMethod = CompressionMethod.STORE
                fileNameInZip = ENTRY
            }
        ZipFile(zip, password).addFile(evbk, params)
    }

    /** Extracts the backup entry from [zip] into [outDir] and returns it. Throws on a wrong password or a damaged zip. */
    fun read(
        zip: File,
        password: CharArray,
        outDir: File,
    ): File {
        ZipFile(zip, password).extractFile(ENTRY, outDir.path)
        return File(outDir, ENTRY)
    }
}
