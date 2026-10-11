package com.example.encryptedvault.ui.foryou

import com.example.encryptedvault.data.VaultItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Items taken on today's calendar date, [yearsAgo] years ago. */
data class OnThisDay(val yearsAgo: Int, val items: List<VaultItem>)

/** Pure selection logic behind the For You tab. */
object ForYou {

    /** Items taken on the same month and day in earlier years, nearest year first. */
    fun onThisDay(items: List<VaultItem>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): List<OnThisDay> =
        items.groupBy { Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate() }
            .filterKeys { it.monthValue == today.monthValue && it.dayOfMonth == today.dayOfMonth && it.year < today.year }
            .map { (day, list) -> OnThisDay(today.year - day.year, list) }
            .sortedBy { it.yearsAgo }

    /** Most recently imported items (ids grow with every import). */
    fun recentlyAdded(items: List<VaultItem>, limit: Int = 20): List<VaultItem> = items.sortedByDescending { it.id }.take(limit)

    /** Favorites first, then edited copies, then the rest; ties broken by newest. */
    fun featured(items: List<VaultItem>, limit: Int = 8): List<VaultItem> =
        items.filter { !it.isVideo }
            .sortedWith(compareByDescending<VaultItem> { score(it) }.thenByDescending { it.takenAt })
            .take(limit)

    /** Ranking score for Featured Photos: favourites outrank edited copies, which outrank the rest. */
    private fun score(item: VaultItem) = (if (item.isFavorite) 2 else 0) + (if (item.isEdited) 1 else 0)
}
