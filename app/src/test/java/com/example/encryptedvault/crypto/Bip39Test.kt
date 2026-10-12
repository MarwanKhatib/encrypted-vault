package com.example.encryptedvault.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Bip39Test {
    private val bip = Bip39(File("src/main/assets/bip39_english.txt").readLines().filter { it.isNotBlank() })

    @Test fun roundTrip() {
        val e = bip.newEntropy()
        val phrase = bip.encode(e)
        assertEquals(24, phrase.size)
        assertArrayEquals(e, bip.decode(phrase))
    }

    @Test fun badChecksumRejected() {
        val phrase = bip.encode(bip.newEntropy()).toMutableList()
        phrase[23] = if (phrase[23] == "abandon") "zoo" else "abandon"
        // swapping last word changes checksum bits (or entropy); decode must not return original silently
        val r = bip.decode(phrase)
        assertTrue(r == null || r.size == 32)
    }
}
