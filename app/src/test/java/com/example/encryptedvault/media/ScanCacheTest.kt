package com.example.encryptedvault.media

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanCacheTest {
    @Test fun roundTripKeepsKeysThatContainPipes() {
        val map = mapOf("content://media/external/images/media/7|1024|1700000000" to "abc123")
        assertEquals(map, ScanCache.decode(ScanCache.encode(map)))
    }

    @Test fun badLinesAreSkipped() {
        assertEquals(mapOf("k|1" to "h"), ScanCache.decode("k|1|h\nnopipe\n|x"))
    }
}
