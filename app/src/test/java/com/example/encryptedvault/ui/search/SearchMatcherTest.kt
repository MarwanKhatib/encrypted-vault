package com.example.encryptedvault.ui.search

import com.example.encryptedvault.testItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SearchMatcherTest {
    private val utc = ZoneOffset.UTC
    private val beach = testItem(1, LocalDate.of(2024, 7, 4), name = "beach.jpg", sourceFolder = "Pictures/Holiday", favorite = true)
    private val clip = testItem(2, LocalDate.of(2023, 1, 2), mimeType = "video/mp4", name = "clip.mp4", sourceFolder = "DCIM/Camera")
    private val shot = testItem(3, LocalDate.of(2023, 1, 2), name = "Screenshot_1.png", sourceFolder = "Pictures/Screenshots")

    private fun matches(
        item: com.example.encryptedvault.data.VaultItem,
        q: String,
    ) = SearchMatcher.matches(item, q, utc)

    @Test fun blankQueryMatchesEverything() = assertTrue(matches(beach, "   "))

    @Test fun matchesFileNameCaseInsensitively() = assertTrue(matches(beach, "BEACH"))

    @Test fun matchesFolder() = assertTrue(matches(beach, "holiday"))

    @Test fun matchesYearAndMonthName() {
        assertTrue(matches(beach, "2024"))
        assertTrue(matches(beach, "july"))
    }

    @Test fun matchesMediaKind() {
        assertTrue(matches(clip, "video"))
        assertFalse(matches(beach, "video"))
        assertTrue(matches(beach, "photo"))
    }

    @Test fun matchesFavorites() {
        assertTrue(matches(beach, "favorite"))
        assertFalse(matches(clip, "favorite"))
    }

    @Test fun matchesScreenshotsByFolderOrName() {
        assertTrue(matches(shot, "screenshot"))
        assertFalse(matches(clip, "screenshot"))
    }

    @Test fun allWordsMustMatch() {
        assertTrue(matches(beach, "beach 2024"))
        assertFalse(matches(beach, "beach 2023"))
    }

    @Test fun matchesEditedCopies() {
        assertTrue(matches(testItem(12, name = "a (edited).jpg"), "edited"))
        assertFalse(matches(beach, "edited"))
    }

    @Test fun matchesCaptions() = assertTrue(matches(testItem(11, caption = "Grandma's birthday"), "birthday"))
}
