package com.example.encryptedvault.ui.library

import com.example.encryptedvault.data.VaultItem

/** Which media types the Library shows (the "Show" section of the ⋯ menu). */
enum class LibraryFilter(
    val label: String,
) {
    ALL("All items"),
    FAVORITES("Favorites"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
}

/** Selection logic behind the Library grid. Kept out of the composable so it can be unit-tested without a device. */
object LibraryFeed {
    /** Items the Library shows for [filter] and [range]: live items only, oldest first when [newestAtBottom] (the iPhone order). */
    fun visible(
        all: List<VaultItem>,
        filter: LibraryFilter,
        range: TimeRange?,
        newestAtBottom: Boolean,
    ): List<VaultItem> {
        val library = all.filter { it.isInLibrary }
        val byType =
            when (filter) {
                LibraryFilter.ALL -> library
                LibraryFilter.FAVORITES -> library.filter { it.isFavorite }
                LibraryFilter.PHOTOS -> library.filter { !it.isVideo }
                LibraryFilter.VIDEOS -> library.filter { it.isVideo }
            }
        val ranged = Timeline.filter(byType, range)
        return if (newestAtBottom) ranged.reversed() else ranged
    }

    /** The items of [event] that are also in [visible], keeping [visible]'s order. Uses an id set, so it is one pass. */
    fun inEvent(
        visible: List<VaultItem>,
        event: TimelineEvent,
    ): List<VaultItem> {
        val ids = event.items.mapTo(HashSet(event.items.size)) { it.id }
        return visible.filter { it.id in ids }
    }
}
