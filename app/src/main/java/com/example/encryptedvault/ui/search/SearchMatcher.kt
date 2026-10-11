package com.example.encryptedvault.ui.search

import com.example.encryptedvault.data.VaultItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Free-text search over what the vault knows about an item: file name, original folder, media type,
 * caption and date (year, month name). Every word of the query must match somewhere (AND semantics).
 * Pure Kotlin so it is unit-tested off-device.
 */
object SearchMatcher {

    /** True when every whitespace-separated word of [query] occurs in the item's searchable text; a blank query matches all. */
    fun matches(item: VaultItem, query: String, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = searchableText(item, zone)
        return words.all { it in haystack }
    }

    /** The lower-cased text a query is matched against. Exposed (internal) so tests can assert what is searchable. */
    internal fun searchableText(item: VaultItem, zone: ZoneId): String {
        val date = Instant.ofEpochMilli(item.takenAt).atZone(zone)
        return buildList {
            add(item.displayName)
            item.sourceFolder?.let(::add)
            add(if (item.isVideo) "video" else "photo")
            if (item.isScreenshot) add("screenshot")
            if (item.isFavorite) add("favorite favourite")
            if (item.isEdited) add("edited")
            if (item.caption.isNotBlank()) add(item.caption)
            add(date.year.toString())
            add(date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
        }.joinToString(" ").lowercase()
    }
}
