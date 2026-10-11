package com.example.encryptedvault.ui.albums

import com.example.encryptedvault.data.Album
import com.example.encryptedvault.data.VaultItem

/** A named subset of the library that can be opened as its own grid. */
sealed interface CollectionSource {
    /** Every item in the Library (the "Recents" album). */
    data object Recents : CollectionSource
    // Media types
    data object Videos : CollectionSource
    /** Items taken from a Screenshots folder or named like a screenshot. */
    data object Screenshots : CollectionSource
    /** Videos from a screen-recording folder or with a recorder-style name. */
    data object ScreenRecordings : CollectionSource
    /** Still images at least twice as wide as they are tall (or the reverse). */
    data object Panoramas : CollectionSource
    /** Animated GIFs. */
    data object Animated : CollectionSource
    // Utilities
    data object Favorites : CollectionSource
    /** Copies saved by the photo editor (their name ends in `(edited).jpg`). */
    data object RecentlyEdited : CollectionSource
    /** Items the user hid. Shown only here, never in the Library or in other collections. */
    data object Hidden : CollectionSource
    /** Items awaiting permanent deletion (kept for 30 days). */
    data object RecentlyDeleted : CollectionSource
    /** Everything imported from one original folder, e.g. `DCIM/Camera`. */
    data class Folder(val path: String) : CollectionSource
    /** A user-created album, addressed by its database id. */
    data class UserAlbum(val id: Long) : CollectionSource
    /** An arbitrary, already-chosen list of items (e.g. "On This Day"); [title] is shown in the header. */
    data class Fixed(val title: String, val itemIds: List<Long>) : CollectionSource
}

/** Resolves a [CollectionSource] against live data. Pure, so it is unit-tested. */
object Collections {

    /**
     * Items belonging to [source], in the order of [all]. Hidden and Recently Deleted are the only sources
     * that show hidden/trashed items; every other source shows only items that are [VaultItem.isInLibrary].
     */
    fun items(source: CollectionSource, all: List<VaultItem>, albums: List<Album>): List<VaultItem> = when (source) {
        CollectionSource.RecentlyDeleted -> all.filter { it.isTrashed }
        CollectionSource.Hidden -> all.filter { it.isHidden && !it.isTrashed }
        else -> all.filter { it.isInLibrary }.let { live -> select(source, live, albums) }
    }

    /** Picks the items of [source] from [live] (already limited to library items). */
    private fun select(source: CollectionSource, live: List<VaultItem>, albums: List<Album>): List<VaultItem> = when (source) {
        CollectionSource.Recents -> live
        CollectionSource.Videos -> live.filter { it.isVideo }
        CollectionSource.Screenshots -> live.filter { it.isScreenshot }
        CollectionSource.ScreenRecordings -> live.filter { it.isScreenRecording }
        CollectionSource.Panoramas -> live.filter { it.isPanorama }
        CollectionSource.Animated -> live.filter { it.isAnimated }
        CollectionSource.Favorites -> live.filter { it.isFavorite }
        CollectionSource.RecentlyEdited -> live.filter { it.isEdited }
        is CollectionSource.Folder -> live.filter { it.sourceFolder == source.path }
        is CollectionSource.UserAlbum -> albums.firstOrNull { it.id == source.id }?.let { a -> live.filter { it.id in a.itemIds } }.orEmpty()
        is CollectionSource.Fixed -> live.filter { it.id in source.itemIds }
        CollectionSource.RecentlyDeleted, CollectionSource.Hidden -> emptyList()
    }

    /** Display title of [source]; user albums resolve their name from [albums]. */
    fun title(source: CollectionSource, albums: List<Album>): String = when (source) {
        CollectionSource.Recents -> "Recents"
        CollectionSource.Videos -> "Videos"
        CollectionSource.Screenshots -> "Screenshots"
        CollectionSource.ScreenRecordings -> "Screen Recordings"
        CollectionSource.Panoramas -> "Panoramas"
        CollectionSource.Animated -> "Animated"
        CollectionSource.Favorites -> "Favorites"
        CollectionSource.RecentlyEdited -> "Recently Edited"
        CollectionSource.Hidden -> "Hidden"
        CollectionSource.RecentlyDeleted -> "Recently Deleted"
        is CollectionSource.Folder -> source.path.substringAfterLast('/').ifEmpty { source.path }
        is CollectionSource.UserAlbum -> albums.firstOrNull { it.id == source.id }?.name.orEmpty()
        is CollectionSource.Fixed -> source.title
    }
}
