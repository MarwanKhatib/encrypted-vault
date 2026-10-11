package com.example.encryptedvault.ui.albums

import com.example.encryptedvault.data.Album
import com.example.encryptedvault.testItem
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionsTest {
    private val photo = testItem(1, name = "a.jpg", sourceFolder = "DCIM/Camera")
    private val video = testItem(2, mimeType = "video/mp4", name = "b.mp4", sourceFolder = "DCIM/Camera")
    private val favorite = testItem(3, name = "c.jpg", sourceFolder = "Pictures/Holiday", favorite = true)
    private val screenshot = testItem(4, name = "Screenshot_x.png", sourceFolder = "Pictures/Screenshots")
    private val deleted = testItem(5, name = "d.jpg", favorite = true, trashedAt = 123L)
    private val withAda = testItem(6, name = "e.jpg", sourceFolder = null)
    private val hiddenItem = testItem(7, name = "h.jpg", hidden = true, favorite = true)
    private val panorama = testItem(8, name = "pano.jpg", width = 6000, height = 1500)
    private val gif = testItem(9, mimeType = "image/gif", name = "anim.gif")
    private val recording = testItem(10, mimeType = "video/mp4", name = "Screen_Recording_1.mp4", sourceFolder = "Movies/Screen recordings")
    private val all = listOf(photo, video, favorite, screenshot, deleted, withAda, hiddenItem, panorama, gif, recording)
    private val albums = listOf(Album(10, "Trip", setOf(1L, 3L, 5L)))

    private fun ids(source: CollectionSource) = Collections.items(source, all, albums).map { it.id }

    @Test fun videosExcludeTrash() = assertEquals(listOf(2L, 10L), ids(CollectionSource.Videos))
    @Test fun hiddenShowsOnlyHiddenItems() = assertEquals(listOf(7L), ids(CollectionSource.Hidden))
    @Test fun hiddenItemsStayOutOfEveryOtherCollection() {
        assertEquals(false, 7L in ids(CollectionSource.Favorites))
        assertEquals(false, 7L in ids(CollectionSource.Recents))
    }
    @Test fun recentsIsTheWholeLibrary() = assertEquals(listOf(1L, 2L, 3L, 4L, 6L, 8L, 9L, 10L), ids(CollectionSource.Recents))
    @Test fun panoramasUseAspectRatio() = assertEquals(listOf(8L), ids(CollectionSource.Panoramas))
    @Test fun animatedMeansGif() = assertEquals(listOf(9L), ids(CollectionSource.Animated))
    @Test fun screenRecordingsAreVideosFromRecorderFolders() = assertEquals(listOf(10L), ids(CollectionSource.ScreenRecordings))
    @Test fun fixedCollectionKeepsOnlyLiveItemsWithThoseIds() = assertEquals(listOf(1L, 3L), ids(CollectionSource.Fixed("x", listOf(1, 3, 5, 7))))
    @Test fun favoritesExcludeTrashedAndHidden() = assertEquals(listOf(3L), ids(CollectionSource.Favorites))
    @Test fun screenshotsAreDetectedByFolder() = assertEquals(listOf(4L), ids(CollectionSource.Screenshots))
    @Test fun recentlyDeletedShowsOnlyTrashed() = assertEquals(listOf(5L), ids(CollectionSource.RecentlyDeleted))
    @Test fun folderCollectsItsItems() = assertEquals(listOf(1L, 2L, 8L, 9L), ids(CollectionSource.Folder("DCIM/Camera")))
    @Test fun userAlbumHidesTrashedMembers() = assertEquals(listOf(1L, 3L), ids(CollectionSource.UserAlbum(10)))
    @Test fun missingAlbumIsEmpty() = assertEquals(emptyList<Long>(), ids(CollectionSource.UserAlbum(99)))
    @Test fun recentlyEditedFindsEditorCopiesOnly() {
        val edited = testItem(11, name = "a (edited).jpg")
        assertEquals(listOf(11L), Collections.items(CollectionSource.RecentlyEdited, all + edited, albums).map { it.id })
        assertEquals(emptyList<Long>(), ids(CollectionSource.RecentlyEdited))
    }

    @Test
    fun titlesAreHumanReadable() {
        assertEquals("Recently Deleted", Collections.title(CollectionSource.RecentlyDeleted, albums))
        assertEquals("Camera", Collections.title(CollectionSource.Folder("DCIM/Camera"), albums))
        assertEquals("Trip", Collections.title(CollectionSource.UserAlbum(10), albums))
    }
}
