package com.example.encryptedvault.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRulesTest {
    @Test fun newerTagIsAnUpdate() {
        assertTrue(UpdateRules.isNewer("0.2.0", "v0.3.0"))
        assertTrue(UpdateRules.isNewer("0.2.0", "v0.2.1"))
        assertTrue(UpdateRules.isNewer("0.9.0", "v0.10.0"))
    }

    @Test fun sameOrOlderTagIsNotAnUpdate() {
        assertFalse(UpdateRules.isNewer("0.2.0", "v0.2.0"))
        assertFalse(UpdateRules.isNewer("0.3.0", "v0.2.9"))
        assertFalse(UpdateRules.isNewer("0.10.0", "v0.9.0"))
    }

    @Test fun unreadableVersionsAreNeverNewer() {
        assertFalse(UpdateRules.isNewer("0.2.0", "latest"))
        assertFalse(UpdateRules.isNewer("0.2.0", "v1.2.3.4"))
        assertNull(UpdateRules.parse("../etc"))
    }

    @Test fun shortVersionsPadWithZeros() {
        assertEquals(listOf(1, 2, 0), UpdateRules.parse("1.2"))
    }

    @Test fun checksumFileMustNameTheSameDigest() {
        val hex = "a".repeat(64)
        assertTrue(UpdateRules.checksumMatches("$hex  app-release.apk\n", hex))
        assertTrue(UpdateRules.checksumMatches("${hex.uppercase()}  app-release.apk", hex))
        assertFalse(UpdateRules.checksumMatches("${"b".repeat(64)}  app-release.apk", hex))
        assertFalse(UpdateRules.checksumMatches("", hex))
    }
}
