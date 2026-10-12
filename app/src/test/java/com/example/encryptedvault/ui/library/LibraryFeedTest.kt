package com.example.encryptedvault.ui.library

import com.example.encryptedvault.testItem
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFeedTest {
    private val all =
        listOf(
            testItem(id = 1, favorite = true),
            testItem(id = 2, mimeType = "video/mp4"),
            testItem(id = 3, trashedAt = 1L),
            testItem(id = 4, hidden = true),
            testItem(id = 5, favorite = true, mimeType = "video/mp4"),
        )

    @Test fun allFilterShowsOnlyLiveItems() {
        assertEquals(listOf(1L, 2L, 5L), LibraryFeed.visible(all, LibraryFilter.ALL, null, newestAtBottom = false).map { it.id })
    }

    @Test fun favoritesAndTypeFiltersApplyToLiveItems() {
        assertEquals(listOf(1L, 5L), LibraryFeed.visible(all, LibraryFilter.FAVORITES, null, false).map { it.id })
        assertEquals(listOf(1L), LibraryFeed.visible(all, LibraryFilter.PHOTOS, null, false).map { it.id })
        assertEquals(listOf(2L, 5L), LibraryFeed.visible(all, LibraryFilter.VIDEOS, null, false).map { it.id })
    }

    @Test fun newestAtBottomReversesTheOrder() {
        assertEquals(listOf(5L, 2L, 1L), LibraryFeed.visible(all, LibraryFilter.ALL, null, newestAtBottom = true).map { it.id })
    }

    @Test fun eventKeepsVisibleOrderAndOnlyItsOwnItems() {
        val visible = LibraryFeed.visible(all, LibraryFilter.ALL, null, newestAtBottom = false)
        val day = Timeline.dayOf(visible[0])
        val event = TimelineEvent(day, day, listOf(visible[2], visible[0]))
        assertEquals(listOf(1L, 5L), LibraryFeed.inEvent(visible, event).map { it.id })
    }
}
