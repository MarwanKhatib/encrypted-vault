package com.example.encryptedvault.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FormattersTest {
    @Test fun fileSizes() {
        assertEquals("12 B", Formatters.fileSize(12))
        assertEquals("2 KB", Formatters.fileSize(2048))
        assertEquals("1.5 MB", Formatters.fileSize(1_572_864))
        assertEquals("2.0 GB", Formatters.fileSize(2L shl 30))
    }

    @Test fun durations() {
        assertEquals("0:45", Formatters.duration(45_000))
        assertEquals("12:03", Formatters.duration(723_000))
        assertEquals("1:02:09", Formatters.duration(3_729_000))
    }

    @Test fun itemCountPluralises() {
        assertEquals("1 item", Formatters.itemCount(1))
        assertEquals("0 items", Formatters.itemCount(0))
        assertEquals("5 items", Formatters.itemCount(5))
    }

    @Test fun dayHeaderNamesTodayAndYesterday() {
        val today = LocalDate.of(2025, 5, 10)
        assertEquals("Today", Formatters.dayHeader(today, today))
        assertEquals("Yesterday", Formatters.dayHeader(today.minusDays(1), today))
    }

    @Test fun monthTitle() = assertEquals("March 2024", Formatters.monthTitle(YearMonth.of(2024, 3)))

    @Test fun dateRanges() {
        assertEquals("Mar 15, 2024", Formatters.dateRange(LocalDate.of(2024, 3, 15), LocalDate.of(2024, 3, 15)))
        assertEquals("Mar 14–18, 2024", Formatters.dateRange(LocalDate.of(2024, 3, 14), LocalDate.of(2024, 3, 18)))
        assertEquals("Mar 30 – Apr 2, 2024", Formatters.dateRange(LocalDate.of(2024, 3, 30), LocalDate.of(2024, 4, 2)))
        assertEquals("Dec 30, 2023 – Jan 2, 2024", Formatters.dateRange(LocalDate.of(2023, 12, 30), LocalDate.of(2024, 1, 2)))
    }

    @Test fun shortDayForTheViewerTitle() {
        val today = LocalDate.of(2025, 5, 10)
        assertEquals("Today", Formatters.shortDay(today, today))
        assertEquals("Yesterday", Formatters.shortDay(today.minusDays(1), today))
        assertEquals("Saturday, Mar 15", Formatters.shortDay(LocalDate.of(2025, 3, 15), today))
        assertEquals("Friday, Mar 15, 2024", Formatters.shortDay(LocalDate.of(2024, 3, 15), today))
    }
}
