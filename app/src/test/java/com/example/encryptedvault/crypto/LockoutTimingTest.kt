package com.example.encryptedvault.crypto

import org.junit.Assert.assertEquals
import org.junit.Test

class LockoutTimingTest {
    private val minute = 60_000L

    @Test fun noLockoutMeansNoWait() {
        assertEquals(0L, UnlockAttempts.remainingLockMs(lockMs = 0, startedAtElapsed = 0, nowElapsed = 5 * minute, sameBoot = true))
    }

    @Test fun waitCountsDownWithinOneBoot() {
        assertEquals(20_000L, UnlockAttempts.remainingLockMs(lockMs = 30_000, startedAtElapsed = minute, nowElapsed = minute + 10_000, sameBoot = true))
    }

    @Test fun waitEndsWhenItHasPassed() {
        assertEquals(0L, UnlockAttempts.remainingLockMs(lockMs = 30_000, startedAtElapsed = minute, nowElapsed = minute + 45_000, sameBoot = true))
    }

    @Test fun rebootKeepsTheFullLockout() {
        // After a reboot the monotonic clock restarts; the old start time means nothing, so the lockout is enforced in full.
        assertEquals(30_000L, UnlockAttempts.remainingLockMs(lockMs = 30_000, startedAtElapsed = 10 * minute, nowElapsed = 12 * minute, sameBoot = false))
    }

    @Test fun clockThatSeemsToGoBackwardsKeepsTheFullLockout() {
        assertEquals(30_000L, UnlockAttempts.remainingLockMs(lockMs = 30_000, startedAtElapsed = 10 * minute, nowElapsed = 9 * minute, sameBoot = true))
    }
}
