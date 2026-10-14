package com.example.encryptedvault.media

import android.content.Context
import java.io.File

/**
 * Remembers the content hash of each gallery file, keyed by uri, size and modified time, so a rescan only hashes new or
 * changed files (ADR-016). Stored in no-backup storage; holds hashes of plaintext files, never the files.
 */
internal object ScanCache {
    private const val FILE = "gallery-hashes.txt"

    /** Lines of `key|hash`. The key contains `|` itself, so the hash is what follows the last one. */
    fun decode(text: String): MutableMap<String, String> =
        text
            .lineSequence()
            .mapNotNull { line ->
                val split = line.lastIndexOf('|')
                if (split <= 0) null else line.substring(0, split) to line.substring(split + 1)
            }.toMap()
            .toMutableMap()

    fun encode(map: Map<String, String>): String = map.entries.joinToString("\n") { "${it.key}|${it.value}" }

    fun load(context: Context): MutableMap<String, String> {
        val file = File(context.noBackupFilesDir, FILE)
        return if (file.exists()) decode(file.readText()) else mutableMapOf()
    }

    /** Saves only the entries whose keys are in [keep], so deleted gallery files drop out of the cache. */
    fun save(
        context: Context,
        map: Map<String, String>,
        keep: Set<String>,
    ) {
        File(context.noBackupFilesDir, FILE).writeText(encode(map.filterKeys { it in keep }))
    }
}
