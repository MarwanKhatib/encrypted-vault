package com.example.encryptedvault.ui.library

import com.example.encryptedvault.data.VaultItem
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** How zoomed-out the library timeline is, like the Years / Months / Days / All tabs in iOS Photos. */
enum class TimelineScope(
    val label: String,
) {
    YEARS("Years"),
    MONTHS("Months"),
    DAYS("Days"),
    ALL("All"),
}

/** Items that share one period key (a day, month or year). */
data class TimelineGroup<K>(
    val key: K,
    val items: List<VaultItem>,
) {
    /** The newest item, used as the cover image of year/month cards. */
    val cover: VaultItem get() = items.first()
}

/** A run of items taken close together (like the date-range cards in iOS "Months"). Days are inclusive. */
data class TimelineEvent(
    val firstDay: LocalDate,
    val lastDay: LocalDate,
    val items: List<VaultItem>,
) {
    val cover: VaultItem get() = items.first()
}

/** Half-open time interval `[startMs, endMs)` in epoch millis. */
data class TimeRange(
    val startMs: Long,
    val endMs: Long,
) {
    /** True when [timeMs] falls inside the half-open interval. */
    operator fun contains(timeMs: Long) = timeMs in startMs until endMs
}

/** Quick choices offered by the time-range filter; [CUSTOM] opens a date-range picker. */
enum class RangePreset(
    val label: String,
) {
    ANY("Any time"),
    TODAY("Today"),
    LAST_7_DAYS("Last 7 days"),
    LAST_30_DAYS("Last 30 days"),
    THIS_YEAR("This year"),
    CUSTOM("Custom range…"),
}

/** Pure date logic for the library; no Android dependencies so it can be unit-tested directly. */
object Timeline {
    /** Items taken at most this many days apart belong to the same event. */
    const val EVENT_GAP_DAYS = 2L

    /** Resolves a [RangePreset] to a concrete range ending at the end of today; null for ANY/CUSTOM. */
    fun rangeFor(
        preset: RangePreset,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): TimeRange? {
        val tomorrow = today.plusDays(1)
        val start =
            when (preset) {
                RangePreset.TODAY -> today
                RangePreset.LAST_7_DAYS -> today.minusDays(6)
                RangePreset.LAST_30_DAYS -> today.minusDays(29)
                RangePreset.THIS_YEAR -> LocalDate.of(today.year, 1, 1)
                RangePreset.ANY, RangePreset.CUSTOM -> return null
            }
        return TimeRange(start.atStartOfDay(zone).toInstant().toEpochMilli(), tomorrow.atStartOfDay(zone).toInstant().toEpochMilli())
    }

    /** Range covering the inclusive calendar days [first]..[last]. */
    fun rangeOfDays(
        first: LocalDate,
        last: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ) = TimeRange(
        first.atStartOfDay(zone).toInstant().toEpochMilli(),
        last
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli(),
    )

    /** Items taken inside [range]; a null range keeps everything. */
    fun filter(
        items: List<VaultItem>,
        range: TimeRange?,
    ): List<VaultItem> = if (range == null) items else items.filter { it.takenAt in range }

    // Grouping keeps the order of [items] (the caller sorts), so groups come out newest- or oldest-first.

    /** Groups by local calendar day, keeping the order of [items]. */
    fun byDay(
        items: List<VaultItem>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<TimelineGroup<LocalDate>> = group(items) { dayOf(it, zone) }

    /** Groups by local month, keeping the order of [items]. */
    fun byMonth(
        items: List<VaultItem>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<TimelineGroup<YearMonth>> = group(items) { monthOf(it, zone) }

    /**
     * Splits [items] (already sorted, either direction) into events: a new event starts whenever the gap
     * between two consecutive items is more than [maxGapDays] days.
     */
    fun byEvent(
        items: List<VaultItem>,
        maxGapDays: Long = EVENT_GAP_DAYS,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<TimelineEvent> {
        val events = mutableListOf<TimelineEvent>()
        var current = mutableListOf<VaultItem>()
        var previousDay: LocalDate? = null

        /** Closes the event being built, if any, so the next item starts a new one. */
        fun flush() {
            if (current.isEmpty()) return
            val days = current.map { dayOf(it, zone) }
            events += TimelineEvent(days.min(), days.max(), current)
            current = mutableListOf()
        }
        for (item in items) {
            val day = dayOf(item, zone)
            if (previousDay != null &&
                kotlin.math.abs(
                    java.time.temporal.ChronoUnit.DAYS
                        .between(previousDay, day),
                ) > maxGapDays
            ) {
                flush()
            }
            current += item
            previousDay = day
        }
        flush()
        return events
    }

    /** Groups by local year, keeping the order of [items]. */
    fun byYear(
        items: List<VaultItem>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<TimelineGroup<Int>> = group(items) { dayOf(it, zone).year }

    /** Local calendar day on which [item] was taken. */
    fun dayOf(
        item: VaultItem,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalDate = Instant.ofEpochMilli(item.takenAt).atZone(zone).toLocalDate()

    /** Local year-month in which [item] was taken. */
    fun monthOf(
        item: VaultItem,
        zone: ZoneId = ZoneId.systemDefault(),
    ): YearMonth = YearMonth.from(dayOf(item, zone))

    private fun <K> group(
        items: List<VaultItem>,
        keyOf: (VaultItem) -> K,
    ): List<TimelineGroup<K>> = items.groupBy(keyOf).map { (key, list) -> TimelineGroup(key, list) }
}
