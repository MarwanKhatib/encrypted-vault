package com.example.encryptedvault.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoImportTest {
    @Test fun onlyItemsAddedAfterTheWatermarkAreNew() {
        assertTrue(AutoImport.isNew(dateAddedSec = 10, watermarkSec = 5))
        assertFalse(AutoImport.isNew(dateAddedSec = 5, watermarkSec = 5))
        assertFalse(AutoImport.isNew(dateAddedSec = 4, watermarkSec = 5))
    }

    @Test fun watermarkMovesToTheNewestImportedItem() {
        assertEquals(7L, AutoImport.nextWatermark(current = 3, addedSec = listOf(4, 7, 6)))
    }

    @Test fun watermarkNeverMovesBackward() {
        assertEquals(9L, AutoImport.nextWatermark(current = 9, addedSec = listOf(7)))
        assertEquals(3L, AutoImport.nextWatermark(current = 3, addedSec = emptyList()))
    }
}
