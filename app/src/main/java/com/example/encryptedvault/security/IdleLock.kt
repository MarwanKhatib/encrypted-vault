package com.example.encryptedvault.security

/**
 * Idle timer for the opt-in auto-lock (ADR-010). Times come from [clock], which must be monotonic (SystemClock.elapsedRealtime),
 * so changing the wall clock cannot shorten or extend an idle period.
 */
class IdleLock(
    private val clock: () -> Long,
) {
    private var lastInput = clock()

    /** Records user input (touch or key) and restarts the idle period. */
    fun touch() {
        lastInput = clock()
    }

    /** True once no input has arrived for at least [timeoutMs]. */
    fun isIdleFor(timeoutMs: Long): Boolean = clock() - lastInput >= timeoutMs

    companion object {
        /** How often the activity checks for idleness while it is visible. */
        const val CHECK_INTERVAL_MS = 60_000L
    }
}
