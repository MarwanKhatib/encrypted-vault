package com.example.encryptedvault.ui.settings

import java.io.File
import java.util.Locale

private const val KB = 1024L
private const val MB = KB * 1024
private const val GB = MB * 1024

/** Bytes of every regular file under [dir], recursively. This is the encrypted size on disk. */
internal fun sumFileBytes(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

/** Human-readable size: bytes below 1 KB, otherwise one decimal in KB, MB or GB. */
internal fun formatBytes(bytes: Long): String =
    when {
        bytes < KB -> "$bytes B"
        bytes < MB -> "%.1f KB".format(Locale.US, bytes / KB.toDouble())
        bytes < GB -> "%.1f MB".format(Locale.US, bytes / MB.toDouble())
        else -> "%.1f GB".format(Locale.US, bytes / GB.toDouble())
    }

/** Recovery file check: due when it was never confirmed, or was last confirmed more than [PERIOD_MS] ago. */
internal object RecoveryReminder {
    const val PERIOD_MS = 90L * 24 * 60 * 60 * 1000

    /** [checkedAt] is epoch millis of the last confirmation; 0 means never. */
    fun isDue(
        checkedAt: Long,
        now: Long,
    ): Boolean = checkedAt <= 0 || now - checkedAt > PERIOD_MS
}
