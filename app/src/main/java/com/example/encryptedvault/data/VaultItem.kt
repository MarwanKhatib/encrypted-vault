package com.example.encryptedvault.data

import java.util.Locale

/** Decrypted, UI-facing view of one encrypted photo or video. Built by [VaultRepository]. */
data class VaultItem(
    val id: Long,
    /** SHA-256 of the plaintext; also the base name of the encrypted files on disk. */
    val hash: String,
    val mimeType: String,
    /** When the media was taken (epoch millis). */
    val takenAt: Long,
    val sizeBytes: Long,
    val durationMs: Long,
    val isFavorite: Boolean,
    /** Epoch millis when moved to Recently Deleted, or null when the item is live. */
    val trashedAt: Long?,
    val name: String,
    val sourceFolder: String?,
    val isHidden: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    val caption: String = "",
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isTrashed: Boolean get() = trashedAt != null

    /** Shown in the Library: neither in Recently Deleted nor Hidden. */
    val isInLibrary: Boolean get() = !isTrashed && !isHidden

    /** Animated image (GIF). */
    val isAnimated: Boolean get() = mimeType == "image/gif"

    /** Very wide still image (aspect ratio of 2:1 or more). */
    val isPanorama: Boolean
        get() = !isVideo && width > 0 && height > 0 && maxOf(width, height).toFloat() / minOf(width, height) >= PANORAMA_ASPECT

    /** Screen recording: a video from a screen-recording folder or with a recorder-style name. */
    val isScreenRecording: Boolean
        get() = isVideo && (sourceFolder?.contains("screenrecord", true) == true || sourceFolder?.contains("recordings", true) == true ||
            name.contains("screenrecord", true) || name.contains("screen_record", true) || name.contains("screen-record", true))
    /** A copy saved by the photo editor (see `ImageEdits.editedName`). */
    val isEdited: Boolean get() = name.contains("(edited)")
    val isScreenshot: Boolean
        get() = sourceFolder?.contains("screenshot", ignoreCase = true) == true ||
            name.contains("screenshot", ignoreCase = true)

    /** Short folder label such as `Camera` for `DCIM/Camera`. */
    val sourceFolderLabel: String? get() = sourceFolder?.substringAfterLast('/')?.takeIf { it.isNotBlank() }

    /** File name to show and to restore with. Falls back to a stable id-based name. */
    val displayName: String
        get() = name.ifBlank { "${hash.take(8)}.${fileExtension}" }

    val fileExtension: String
        get() = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?: mimeType.substringAfter('/', "bin").lowercase(Locale.ROOT)
}

private const val PANORAMA_ASPECT = 2f

/** A user-created album and the ids of the items it contains. */
data class Album(val id: Long, val name: String, val itemIds: Set<Long>)
