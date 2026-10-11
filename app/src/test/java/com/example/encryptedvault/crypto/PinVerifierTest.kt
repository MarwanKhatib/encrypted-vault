package com.example.encryptedvault.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinVerifierTest {
    // Low iteration count keeps the unit tests fast; the algorithm is identical to production.
    private val iterations = 1_000
    private val salt = ByteArray(16) { it.toByte() }

    @Test fun sameInputsGiveTheSameKey() =
        assertArrayEquals(PinVerifier.derive("483915".toCharArray(), salt, iterations), PinVerifier.derive("483915".toCharArray(), salt, iterations))

    @Test fun differentSaltGivesADifferentKey() {
        val other = ByteArray(16) { (it + 1).toByte() }
        assertFalse(PinVerifier.derive("483915".toCharArray(), salt, iterations).contentEquals(PinVerifier.derive("483915".toCharArray(), other, iterations)))
    }

    @Test fun differentIterationsGiveADifferentKey() =
        assertFalse(PinVerifier.derive("483915".toCharArray(), salt, 1_000).contentEquals(PinVerifier.derive("483915".toCharArray(), salt, 1_001)))

    @Test fun derivedKeyIs32Bytes() = assertEquals(32, PinVerifier.derive("483915".toCharArray(), salt, iterations).size)

    @Test fun matchesAcceptsTheRightPinAndRejectsTheWrongOne() {
        val expected = PinVerifier.derive("483915".toCharArray(), salt, iterations)
        assertTrue(PinVerifier.matches("483915".toCharArray(), salt, iterations, expected))
        assertFalse(PinVerifier.matches("483916".toCharArray(), salt, iterations, expected))
    }

    @Test fun deriveDoesNotModifyTheCallersPinArray() {
        val pin = "483915".toCharArray()
        PinVerifier.derive(pin, salt, iterations)
        assertEquals("483915", String(pin))
    }

    @Test fun newSaltsAreRandomAndSixteenBytes() {
        val a = PinVerifier.newSalt(); val b = PinVerifier.newSalt()
        assertEquals(16, a.size)
        assertFalse(a.contentEquals(b))
    }
}
