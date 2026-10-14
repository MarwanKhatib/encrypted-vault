package com.example.encryptedvault.crypto

import android.content.SharedPreferences
import android.os.SystemClock
import java.io.File

/**
 * Wrong-PIN counter and the lockout that follows it. Both survive app restarts.
 *
 * The lockout is timed on the monotonic clock ([SystemClock.elapsedRealtime]), so changing the wall clock cannot shorten it.
 * A reboot restarts the monotonic clock, so a lockout that spans a reboot is enforced in full again. The KDF cost and the
 * Keystore layer still make every guess run on this device.
 */
class UnlockAttempts(
    private val prefs: SharedPreferences,
) {
    /** Wrong PIN attempts since the last success. */
    fun failCount(): Int = prefs.getInt(KEY_FAILS, 0)

    /** Milliseconds until the next attempt is allowed (0 when not locked out). */
    fun lockedForMs(): Long =
        remainingLockMs(
            lockMs = prefs.getLong(KEY_LOCK_MS, 0),
            startedAtElapsed = prefs.getLong(KEY_LOCK_STARTED, 0),
            nowElapsed = SystemClock.elapsedRealtime(),
            sameBoot = prefs.getString(KEY_LOCK_BOOT, null) == currentBootId(),
        )

    /** Epoch millis of the most recent wrong PINs, oldest first, at most [LOG_CAP] (the intruder log). Times only, no photo. */
    fun recentFailures(): List<Long> =
        prefs
            .getString(KEY_LOG, "")
            .orEmpty()
            .split(',')
            .mapNotNull { it.toLongOrNull() }

    /** Counts a wrong PIN and, after [FREE_TRIES], starts an exponentially growing delay (30 s doubling, capped at 15 min). */
    fun recordFailure() {
        val n = failCount() + 1
        val log = appendCapped(recentFailures(), System.currentTimeMillis()).joinToString(",")
        val edit =
            prefs
                .edit()
                .putInt(KEY_FAILS, n)
                .putString(KEY_LOG, log)
        if (n >= FREE_TRIES) {
            val seconds = minOf(LOCKOUT_MAX_SECONDS, LOCKOUT_BASE_SECONDS shl (n - FREE_TRIES).coerceAtMost(LOCKOUT_MAX_DOUBLINGS))
            edit
                .putLong(KEY_LOCK_MS, seconds * MILLIS_PER_SECOND)
                .putLong(KEY_LOCK_STARTED, SystemClock.elapsedRealtime())
                .putString(KEY_LOCK_BOOT, currentBootId())
        }
        edit.commit()
    }

    /** Clears the failure counter and any lockout (called after a successful unlock). */
    fun resetFailures() =
        prefs
            .edit()
            .remove(KEY_FAILS)
            .remove(KEY_LOCK_MS)
            .remove(KEY_LOCK_STARTED)
            .remove(KEY_LOCK_BOOT)
            .commit()

    companion object {
        /** Wrong PIN attempts allowed before the first lockout. */
        const val FREE_TRIES = 5
        private const val KEY_FAILS = "fails"
        private const val KEY_LOCK_MS = "lock_ms"
        private const val KEY_LOCK_STARTED = "lock_started"
        private const val KEY_LOCK_BOOT = "lock_boot"
        private const val KEY_LOG = "log"

        /** Number of failed-PIN times kept for the intruder log. */
        const val LOG_CAP = 20

        /** [log] with [now] appended, keeping only the newest [cap] entries. */
        internal fun appendCapped(
            log: List<Long>,
            now: Long,
            cap: Int = LOG_CAP,
        ): List<Long> = (log + now).takeLast(cap)

        /** First lockout after [FREE_TRIES] wrong PINs; doubles with each further failure up to [LOCKOUT_MAX_SECONDS]. */
        private const val LOCKOUT_BASE_SECONDS = 30L
        private const val LOCKOUT_MAX_SECONDS = 900L
        private const val LOCKOUT_MAX_DOUBLINGS = 5
        private const val MILLIS_PER_SECOND = 1000L

        /**
         * Time left in a lockout of [lockMs] that started at [startedAtElapsed] (monotonic). Only time measured within the same boot
         * counts down; a different boot, or a clock that appears to go backwards, keeps the full lockout.
         */
        internal fun remainingLockMs(
            lockMs: Long,
            startedAtElapsed: Long,
            nowElapsed: Long,
            sameBoot: Boolean,
        ): Long {
            if (lockMs <= 0) return 0
            val passed = if (sameBoot) (nowElapsed - startedAtElapsed).coerceAtLeast(0) else 0L
            return (lockMs - passed).coerceAtLeast(0)
        }

        /** Kernel boot ID: changes on every reboot. Unknown when the file cannot be read, which then counts as the same boot. */
        internal fun currentBootId(): String = runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim() }.getOrDefault("unknown")
    }
}
