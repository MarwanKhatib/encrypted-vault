package com.example.encryptedvault.data

import android.content.Context
import java.io.File

/**
 * Single source of truth for where encrypted files live on disk.
 *
 * Layout inside `files/vault/` (all names derive from the plaintext SHA-256 `hash`):
 *  - `<hash>.enc`        encrypted media (see [com.example.encryptedvault.crypto.VaultFiles] for the byte layout)
 *  - `<hash>.thumb.enc`  encrypted JPEG thumbnail
 *
 * The directory is in credential-encrypted app-private storage (`filesDir`), excluded from backups.
 */
object VaultStorage {
    /** The vault directory, created on first use. */
    fun directory(context: Context): File = File(context.filesDir, "vault").apply { mkdirs() }

    /** Encrypted full-size media file of the item whose plaintext hash is [hash]. */
    fun content(
        context: Context,
        hash: String,
    ) = File(directory(context), "$hash.enc")

    /** Encrypted JPEG thumbnail of the item whose plaintext hash is [hash]. */
    fun thumbnail(
        context: Context,
        hash: String,
    ) = File(directory(context), "$hash.thumb.enc")

    /** Deletes every file belonging to [hash] (content and thumbnail). */
    fun deleteAll(
        context: Context,
        hash: String,
    ) {
        directory(context).listFiles { f -> f.name.startsWith("$hash.") }?.forEach { it.delete() }
    }

    /**
     * Removes orphans a killed process or an older app version left behind: `.tmp` files from interrupted
     * writes and the `.face<N>.enc` crops of the removed face feature. Call at startup and before imports.
     */
    fun sweepTemporaryFiles(context: Context) {
        directory(context).listFiles { f -> f.name.endsWith(".tmp") || LEGACY_FACE_CROP.matches(f.name) }?.forEach { it.delete() }
    }

    private val LEGACY_FACE_CROP = Regex(""".+\.face\d+\.enc""")
}
