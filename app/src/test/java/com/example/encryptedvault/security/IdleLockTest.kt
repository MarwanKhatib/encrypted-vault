package com.example.encryptedvault.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleLockTest {
    private val minute = 60_000L

    @Test fun notIdleRightAfterStart() {
        var now = 0L
        val idle = IdleLock { now }
        assertFalse(idle.isIdleFor(minute))
    }

    @Test fun idleOnceTheTimeoutHasPassed() {
        var now = 0L
        val idle = IdleLock { now }
        now = minute
        assertTrue(idle.isIdleFor(minute))
    }

    @Test fun touchRestartsThePeriod() {
        var now = 50_000L
        val idle = IdleLock { now }
        now = 50_000L + minute - 1
        idle.touch()
        now = 50_000L + minute * 2 - 2
        assertFalse(idle.isIdleFor(minute))
        now = 50_000L + minute * 2 - 1
        assertTrue(idle.isIdleFor(minute))
    }

    @Test fun checkIntervalIsOneMinute() {
        assertEquals(minute, IdleLock.CHECK_INTERVAL_MS)
    }
}
