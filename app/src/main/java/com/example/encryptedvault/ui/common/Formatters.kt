package com.example.encryptedvault.ui.common

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Human-readable text helpers shared by all screens. Pure functions, covered by unit tests. */
object Formatters {

    /** `1.5 MB`, `320 KB`, `12 B`. */
    fun fileSize(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", bytes / (1L shl 10).toDouble())
        else -> "$bytes B"
    }

    /** `0:45`, `12:03`, `1:02:09`. */
    fun duration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = totalSeconds % 3600 / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        else String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    /** Section header for a day: Today, Yesterday, or the full date. */
    fun dayHeader(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    }

    /** Month heading such as `March 2024`. */
    fun monthTitle(month: YearMonth): String = month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))

    /** `Mar 15, 2024`, `Mar 14–18, 2024`, `Mar 30 – Apr 2, 2024` or `Dec 30, 2023 – Jan 2, 2024`. */
    fun dateRange(first: LocalDate, last: LocalDate): String {
        val month = DateTimeFormatter.ofPattern("MMM", Locale.US)
        return when {
            first == last -> "${first.format(month)} ${first.dayOfMonth}, ${first.year}"
            first.year != last.year -> "${first.format(month)} ${first.dayOfMonth}, ${first.year} – ${last.format(month)} ${last.dayOfMonth}, ${last.year}"
            first.month != last.month -> "${first.format(month)} ${first.dayOfMonth} – ${last.format(month)} ${last.dayOfMonth}, ${last.year}"
            else -> "${first.format(month)} ${first.dayOfMonth}–${last.dayOfMonth}, ${first.year}"
        }
    }

    /** `Today`, `Yesterday`, or `Saturday, Nov 2` for the viewer title. */
    fun shortDay(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "EEEE, MMM d" else "EEEE, MMM d, yyyy", Locale.US))
    }

    /** `1 item` / `5 items`. */
    fun itemCount(count: Int): String = if (count == 1) "1 item" else "$count items"
}
