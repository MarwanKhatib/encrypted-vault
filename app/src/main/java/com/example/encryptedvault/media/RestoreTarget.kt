package com.example.encryptedvault.media

/** Which public MediaStore collection a restored file is written to. */
enum class RestoreCollection { IMAGES, VIDEO, DOWNLOADS }

/** Where a file leaves the vault: a MediaStore collection plus a relative folder (always ends with `/`). */
data class RestoreTarget(val collection: RestoreCollection, val relativePath: String)

/**
 * Decides where an item goes when it is restored out of the vault.
 *
 * Android only lets an app write images under `DCIM`/`Pictures` and videos under `DCIM`/`Movies`
 * (plus `Download` through the Downloads collection). The original folder is reused whenever that is
 * allowed (MediaStore creates the folder if it no longer exists); anything else is kept under a
 * `Restored/` folder so the original sub-folder name is still preserved.
 */
object RestoreTargets {
    private const val FALLBACK_FOLDER = "Restored"
    private val IMAGE_ROOTS = setOf("DCIM", "Pictures")
    private val VIDEO_ROOTS = setOf("DCIM", "Movies")
    private val DOWNLOAD_ROOTS = setOf("Download", "Downloads")

    /** Decides which MediaStore collection and folder an item returns to. A pure function so the folder rules are unit-tested. */
    fun resolve(isVideo: Boolean, sourceFolder: String?): RestoreTarget {
        val folder = sourceFolder?.trim('/', ' ')?.takeIf { it.isNotEmpty() }
        val defaultRoot = if (isVideo) "Movies" else "Pictures"
        val collection = if (isVideo) RestoreCollection.VIDEO else RestoreCollection.IMAGES
        if (folder == null) return RestoreTarget(collection, "$defaultRoot/$FALLBACK_FOLDER/")

        val root = folder.substringBefore('/')
        val allowedRoots = if (isVideo) VIDEO_ROOTS else IMAGE_ROOTS
        return when {
            allowedRoots.any { it.equals(root, ignoreCase = true) } -> RestoreTarget(collection, "$folder/")
            DOWNLOAD_ROOTS.any { it.equals(root, ignoreCase = true) } ->
                RestoreTarget(RestoreCollection.DOWNLOADS, "Download/${folder.substringAfter('/', "").let { if (it.isEmpty()) "" else "$it/" }}")
            else -> RestoreTarget(collection, "$defaultRoot/$FALLBACK_FOLDER/$folder/")
        }
    }
}
