package com.example.encryptedvault.security

import android.graphics.Bitmap
import android.util.LruCache
import com.example.encryptedvault.VaultApp

/** Process-wide lock state: the decoded-thumbnail cache and the one-shot flag that stops the next onStop from locking around system pickers. */
object SessionManager {
    /** Set right before launching a system picker/dialog we own; the next onStop must not lock. */
    @Volatile var suppressNextStopLock = false

    /** Decoded thumbnails (plaintext in RAM). Cleared on every lock. */
    val thumbCache =
        object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ) = value.byteCount
        }

    /** Drops the in-memory master key and any decoded plaintext. */
    fun forceLock() {
        VaultApp.sessionKey = null
        thumbCache.evictAll()
    }

    /** True if this stop should lock now (false once if a picker/dialog was just launched). */
    fun consumeStopLock(): Boolean {
        if (suppressNextStopLock) {
            suppressNextStopLock = false
            return false
        }
        return true
    }
}
