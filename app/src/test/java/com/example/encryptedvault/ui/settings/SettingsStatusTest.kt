package com.example.encryptedvault.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsStatusTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun sumsEveryFileRecursively() {
        val dir = tmp.newFolder("vault")
        File(dir, "a.enc").writeBytes(ByteArray(3))
        File(File(dir, "thumbs").apply { mkdirs() }, "b.thumb.enc").writeBytes(ByteArray(5))
        assertEquals(8L, sumFileBytes(dir))
    }

    @Test fun formatsBytesWithUnits() {
        assertEquals("500 B", formatBytes(500))
        assertEquals("2.0 KB", formatBytes(2048))
        assertEquals("3.0 MB", formatBytes(3L * 1024 * 1024))
        assertEquals("1.5 GB", formatBytes(1536L * 1024 * 1024))
    }

    @Test fun recoveryFileIsDueWhenNeverConfirmed() {
        assertTrue(RecoveryReminder.isDue(checkedAt = 0, now = 1_000_000L))
    }

    @Test fun recoveryFileIsNotDueWithinNinetyDays() {
        val now = 200L * 24 * 60 * 60 * 1000
        assertFalse(RecoveryReminder.isDue(checkedAt = now - 89L * 24 * 60 * 60 * 1000, now = now))
        assertTrue(RecoveryReminder.isDue(checkedAt = now - 91L * 24 * 60 * 60 * 1000, now = now))
    }
}
