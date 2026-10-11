package com.example.encryptedvault.ui.foryou

import com.example.encryptedvault.testItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ForYouTest {
    private val utc = ZoneOffset.UTC
    private val today = LocalDate.of(2025, 6, 12)

    @Test
    fun onThisDayGroupsPastYearsNearestFirst() {
        val items = listOf(
            testItem(1, LocalDate.of(2023, 6, 12)), testItem(2, LocalDate.of(2024, 6, 12)), testItem(3, LocalDate.of(2024, 6, 12)),
            testItem(4, LocalDate.of(2024, 6, 13)), testItem(5, LocalDate.of(2025, 6, 12)),
        )
        val groups = ForYou.onThisDay(items, today, utc)
        assertEquals(listOf(1, 2), groups.map { it.yearsAgo })
        assertEquals(listOf(2L, 3L), groups[0].items.map { it.id })
        assertEquals(listOf(1L), groups[1].items.map { it.id })
    }

    @Test fun onThisDayIgnoresTodayItself() = assertTrue(ForYou.onThisDay(listOf(testItem(1, today)), today, utc).isEmpty())

    @Test
    fun recentlyAddedIsHighestIdsFirst() {
        val items = (1L..30L).map { testItem(it) }
        assertEquals((30L downTo 11L).toList(), ForYou.recentlyAdded(items, 20).map { it.id })
    }

    @Test
    fun featuredPrefersFavoritesThenEditedCopiesAndSkipsVideos() {
        val plain = testItem(1, LocalDate.of(2025, 1, 1))
        val edited = testItem(2, LocalDate.of(2024, 1, 1), name = "b (edited).jpg")
        val favorite = testItem(3, LocalDate.of(2020, 1, 1), favorite = true)
        val video = testItem(4, mimeType = "video/mp4", favorite = true)
        assertEquals(listOf(3L, 2L, 1L), ForYou.featured(listOf(plain, edited, favorite, video)).map { it.id })
    }
}
