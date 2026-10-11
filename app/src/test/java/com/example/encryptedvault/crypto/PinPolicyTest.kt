package com.example.encryptedvault.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinPolicyTest {
    @Test fun validPinsAreSixToTwelveDigits() {
        assertTrue(PinPolicy.isValid("483915"))
        assertTrue(PinPolicy.isValid("483915204817"))
        assertFalse(PinPolicy.isValid("48391"))
        assertFalse(PinPolicy.isValid("4839152048172"))
    }

    @Test fun nonDigitsAreRejected() {
        assertFalse(PinPolicy.isValid("48391a"))
        assertFalse(PinPolicy.isValid("48 915"))
        assertFalse(PinPolicy.isValid("٤٨٣٩١٥")) // Arabic-Indic digits are digits for Char.isDigit but not for the keypad
    }

    @Test fun repeatedAndSequentialPinsAreWeak() {
        listOf("111111", "000000", "123456", "234567", "987654", "543210").forEach { assertTrue(it, PinPolicy.isWeak(it)) }
    }

    @Test fun ordinaryPinsAreNotWeak() {
        listOf("483915", "121212", "135791", "123465").forEach { assertFalse(it, PinPolicy.isWeak(it)) }
    }

    @Test fun autoSubmitFiresOnlyAtTheStoredLength() {
        assertFalse(PinPolicy.isComplete("48391", 6))
        assertTrue(PinPolicy.isComplete("483915", 6))
        assertFalse(PinPolicy.isComplete("4839152", 6))
    }

    @Test fun unknownLengthNeverAutoSubmits() {
        assertFalse(PinPolicy.isComplete("483915", 0))
        assertFalse(PinPolicy.isComplete("", 0))
    }

    @Test fun duressPinMustDifferFromRealPin() = assertNotNull(PinPolicy.duressProblem("483915", "483915"))

    @Test fun duressPinMustHaveTheSameLength() {
        assertNotNull(PinPolicy.duressProblem("483915", "4839152"))
        assertEquals("Must have the same length as your normal PIN (6 digits)", PinPolicy.duressProblem("483915", "729164".dropLast(1) + "40"))
    }

    @Test fun duressPinMustNotBeWeak() = assertNotNull(PinPolicy.duressProblem("483915", "111111"))

    @Test fun acceptableDuressPinHasNoProblem() = assertNull(PinPolicy.duressProblem("483915", "729164"))
}
