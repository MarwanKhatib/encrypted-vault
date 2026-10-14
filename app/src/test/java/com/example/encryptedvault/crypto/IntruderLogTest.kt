package com.example.encryptedvault.crypto

import org.junit.Assert.assertEquals
import org.junit.Test

class IntruderLogTest {
    @Test fun appendsToAnEmptyLog() {
        assertEquals(listOf(1L), UnlockAttempts.appendCapped(emptyList(), 1L))
    }

    @Test fun keepsOnlyTheNewestEntries() {
        val full = (1L..UnlockAttempts.LOG_CAP.toLong()).toList()
        val capped = UnlockAttempts.appendCapped(full, 99L)
        assertEquals(UnlockAttempts.LOG_CAP, capped.size)
        assertEquals(2L, capped.first())
        assertEquals(99L, capped.last())
    }
}
