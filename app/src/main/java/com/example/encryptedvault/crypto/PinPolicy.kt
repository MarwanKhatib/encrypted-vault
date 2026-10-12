package com.example.encryptedvault.crypto

/**
 * Rules for the unlock PIN and the duress PIN. Pure Kotlin (no Android types) so every rule is unit-tested off-device.
 *
 * Why a fixed length matters: the lock screen submits automatically once the entered digits reach the
 * length chosen at setup, so the PIN length is stored (it is not a secret; the dots on screen show it anyway).
 */
object PinPolicy {
    const val MIN_LENGTH = 6
    const val MAX_LENGTH = 12

    /** True when [pin] is only digits and between [MIN_LENGTH] and [MAX_LENGTH] long. */
    fun isValid(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    /**
     * True for PINs that are trivial to guess: one repeated digit (`111111`), or a strictly ascending /
     * descending run (`123456`, `987654`). Such PINs are refused when a PIN is chosen.
     */
    fun isWeak(pin: String): Boolean {
        if (pin.length < 2) return true
        val steps = pin.zipWithNext { a, b -> b - a }
        return steps.all { it == 0 } || steps.all { it == 1 } || steps.all { it == -1 }
    }

    /** True when the user has typed exactly the stored length, i.e. the lock screen should submit. A length of 0 (unknown, legacy vault) never auto-submits. */
    fun isComplete(
        entered: String,
        storedLength: Int,
    ): Boolean = storedLength > 0 && entered.length == storedLength

    /**
     * Why [candidate] cannot be the duress PIN, or null when it is acceptable. A duress PIN must be a valid,
     * non-weak PIN, differ from the real PIN, and have the same length as it (otherwise the auto-submit would never fire for it).
     */
    fun duressProblem(
        realPin: String,
        candidate: String,
    ): String? =
        when {
            !isValid(candidate) -> "Use $MIN_LENGTH–$MAX_LENGTH digits"
            isWeak(candidate) -> "That PIN is too easy to guess"
            candidate == realPin -> "Must differ from your normal PIN"
            candidate.length != realPin.length -> "Must have the same length as your normal PIN (${realPin.length} digits)"
            else -> null
        }
}
