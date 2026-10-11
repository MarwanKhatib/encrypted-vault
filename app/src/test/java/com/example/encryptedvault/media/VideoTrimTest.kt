package com.example.encryptedvault.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTrimTest {
    @Test fun wholeVideoCoversItself() {
        assertTrue(TrimRange.whole(12_000).covers(12_000))
    }

    @Test fun movingAHandleChangesTheSelection() {
        assertFalse(TrimRange.whole(12_000).withEnd(8_000, 12_000).covers(12_000))
    }

    @Test fun startCannotPassEndMinusMinimumLength() {
        val range = TrimRange(0, 5_000).withStart(9_000)
        assertEquals(4_000L, range.startMs)
        assertEquals(TrimRange.MIN_LENGTH_MS, range.endMs - range.startMs)
    }

    @Test fun endCannotPassStartPlusMinimumLengthOrTheVideo() {
        assertEquals(3_000L, TrimRange(2_000, 5_000).withEnd(0, 12_000).endMs)
        assertEquals(12_000L, TrimRange(2_000, 5_000).withEnd(99_000, 12_000).endMs)
    }

    @Test fun startNeverGoesBelowZero() {
        assertEquals(0L, TrimRange(1_000, 5_000).withStart(-500).startMs)
    }

    @Test fun shortRangesAreRejected() {
        val rejected = runCatching { TrimRange(1_000, 1_500) }.isFailure
        assertTrue(rejected)
    }

    @Test fun onlyVideosLongerThanTheMinimumCanBeTrimmed() {
        assertFalse(TrimRange.trimmable(TrimRange.MIN_LENGTH_MS - 1))
        assertTrue(TrimRange.trimmable(TrimRange.MIN_LENGTH_MS))
    }

    @Test fun trimmedCopyIsNamedAsMp4() {
        assertEquals("clip (trimmed).mp4", trimmedName("clip.mov"))
        assertEquals("Video (trimmed).mp4", trimmedName(""))
    }
}
