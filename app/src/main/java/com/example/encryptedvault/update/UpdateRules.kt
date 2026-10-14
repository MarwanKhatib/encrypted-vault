package com.example.encryptedvault.update

/** Pure rules for update checks: version order and checksum comparison (ADR-015). */
internal object UpdateRules {
    private val VERSION = Regex("""v?\d{1,4}(\.\d{1,4}){0,2}""")

    /** True when [candidateTag] (like "v0.3.0") is newer than [current] (like "0.2.0"). Unreadable versions are never newer. */
    fun isNewer(
        current: String,
        candidateTag: String,
    ): Boolean {
        val candidate = parse(candidateTag) ?: return false
        val installed = parse(current) ?: return false
        return compare(candidate, installed) > 0
    }

    /** "0.3" → [0, 3, 0]; null when the text is not a version. */
    fun parse(version: String): List<Int>? {
        if (!VERSION.matches(version)) return null
        val parts = version.removePrefix("v").split('.').map { it.toInt() }
        return parts + List(3 - parts.size) { 0 }
    }

    /** Negative, zero or positive, comparing major, minor, patch in order. */
    fun compare(
        a: List<Int>,
        b: List<Int>,
    ): Int {
        for (i in 0 until 3) {
            if (a[i] != b[i]) return a[i].compareTo(b[i])
        }
        return 0
    }

    /** True when the `.sha256` file text ("<hex>  name") names the same digest as [actualHex]. */
    fun checksumMatches(
        shaFile: String,
        actualHex: String,
    ): Boolean {
        val expected =
            shaFile
                .trim()
                .split(Regex("\\s+"))
                .firstOrNull()
                ?.lowercase() ?: return false
        return expected.length == SHA256_HEX_LENGTH && expected == actualHex.lowercase()
    }

    private const val SHA256_HEX_LENGTH = 64
}
