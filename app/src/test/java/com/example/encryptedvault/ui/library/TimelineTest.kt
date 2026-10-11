package com.example.encryptedvault.ui.library

import com.example.encryptedvault.testItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class TimelineTest {
    private val utc = ZoneOffset.UTC
    private val items = listOf(
        testItem(1, LocalDate.of(2025, 1, 20)),
        testItem(2, LocalDate.of(2025, 1, 20)),
        testItem(3, LocalDate.of(2025, 1, 5)),
        testItem(4, LocalDate.of(2024, 12, 31)),
        testItem(5, LocalDate.of(2023, 6, 1)),
    )

    @Test
    fun groupsByDayKeepingOrder() {
        val groups = Timeline.byDay(items, utc)
        assertEquals(listOf(LocalDate.of(2025, 1, 20), LocalDate.of(2025, 1, 5), LocalDate.of(2024, 12, 31), LocalDate.of(2023, 6, 1)), groups.map { it.key })
        assertEquals(listOf(2, 1, 1, 1), groups.map { it.items.size })
        assertEquals(1L, groups.first().cover.id)
    }

    @Test
    fun groupsByMonthAndYear() {
        assertEquals(listOf(YearMonth.of(2025, 1), YearMonth.of(2024, 12), YearMonth.of(2023, 6)), Timeline.byMonth(items, utc).map { it.key })
        assertEquals(listOf(2025, 2024, 2023), Timeline.byYear(items, utc).map { it.key })
        assertEquals(3, Timeline.byYear(items, utc).first().items.size)
    }

    @Test
    fun emptyListGivesNoGroups() = assertTrue(Timeline.byDay(emptyList(), utc).isEmpty())

    @Test
    fun last7DaysIncludesSevenCalendarDaysEndingToday() {
        val today = LocalDate.of(2025, 1, 20)
        val range = Timeline.rangeFor(RangePreset.LAST_7_DAYS, today, utc)!!
        assertTrue(testItem(day = LocalDate.of(2025, 1, 14)).takenAt in range)
        assertFalse(testItem(day = LocalDate.of(2025, 1, 13)).takenAt in range)
        assertFalse(testItem(day = LocalDate.of(2025, 1, 21)).takenAt in range)
    }

    @Test
    fun thisYearStartsOnJanuaryFirst() {
        val range = Timeline.rangeFor(RangePreset.THIS_YEAR, LocalDate.of(2025, 6, 10), utc)!!
        assertTrue(testItem(day = LocalDate.of(2025, 1, 1)).takenAt in range)
        assertFalse(testItem(day = LocalDate.of(2024, 12, 31)).takenAt in range)
    }

    @Test
    fun anyAndCustomPresetsHaveNoFixedRange() {
        assertNull(Timeline.rangeFor(RangePreset.ANY))
        assertNull(Timeline.rangeFor(RangePreset.CUSTOM))
    }

    @Test
    fun filterKeepsOnlyItemsInRange() {
        val range = Timeline.rangeOfDays(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31), utc)
        assertEquals(listOf(1L, 2L, 3L), Timeline.filter(items, range).map { it.id })
        assertEquals(items, Timeline.filter(items, null))
    }

    @Test
    fun eventsSplitWhenTheGapExceedsTwoDays() {
        val list = listOf(
            testItem(1, LocalDate.of(2025, 3, 20)), testItem(2, LocalDate.of(2025, 3, 19)), testItem(3, LocalDate.of(2025, 3, 17)),
            testItem(4, LocalDate.of(2025, 3, 10)), testItem(5, LocalDate.of(2025, 3, 9)),
        )
        val events = Timeline.byEvent(list, zone = utc)
        assertEquals(2, events.size)
        assertEquals(LocalDate.of(2025, 3, 17), events[0].firstDay)
        assertEquals(LocalDate.of(2025, 3, 20), events[0].lastDay)
        assertEquals(listOf(1L, 2L, 3L), events[0].items.map { it.id })
        assertEquals(LocalDate.of(2025, 3, 9), events[1].firstDay)
    }

    @Test
    fun eventsWorkForOldestFirstOrderToo() {
        val list = listOf(testItem(1, LocalDate.of(2025, 3, 9)), testItem(2, LocalDate.of(2025, 3, 10)), testItem(3, LocalDate.of(2025, 3, 30)))
        assertEquals(listOf(2, 1), Timeline.byEvent(list, zone = utc).map { it.items.size })
    }

    @Test
    fun eventsOfNothingAreEmpty() = assertTrue(Timeline.byEvent(emptyList(), zone = utc).isEmpty())
}
