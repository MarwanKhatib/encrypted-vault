package com.example.encryptedvault.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceDownTest {
    @Test fun screenPointingAtTheFloorLocks() {
        assertTrue(FaceDown.isFaceDown(-9.8f))
        assertTrue(FaceDown.isFaceDown(-7f))
    }

    @Test fun uprightOrFaceUpDoesNotLock() {
        assertFalse(FaceDown.isFaceDown(0f))
        assertFalse(FaceDown.isFaceDown(9.8f))
        assertFalse(FaceDown.isFaceDown(-5f)) // tilted, not face down
    }
}
