package com.example.encryptedvault

import com.example.encryptedvault.data.VaultItem
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Builds [VaultItem]s for unit tests; every field has a sensible default so tests only set what they check.
 * It takes many parameters on purpose: a fixture with named defaults is clearer than a builder for a test-only factory.
 */
@Suppress("LongParameterList")
fun testItem(
    id: Long = 1,
    day: LocalDate = LocalDate.of(2024, 3, 15),
    mimeType: String = "image/jpeg",
    name: String = "IMG_$id.jpg",
    sourceFolder: String? = "DCIM/Camera",
    favorite: Boolean = false,
    trashedAt: Long? = null,
    hidden: Boolean = false,
    width: Int = 0,
    height: Int = 0,
    caption: String = "",
) = VaultItem(
    id = id,
    hash = "hash$id",
    mimeType = mimeType,
    takenAt = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
    sizeBytes = 1000,
    durationMs = 0,
    isFavorite = favorite,
    trashedAt = trashedAt,
    name = name,
    sourceFolder = sourceFolder,
    isHidden = hidden,
    width = width,
    height = height,
    caption = caption,
)
