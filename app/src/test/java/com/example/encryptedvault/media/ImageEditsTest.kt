package com.example.encryptedvault.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageEditsTest {
    @Test fun rotationWrapsAroundFullTurn() {
        assertEquals(270, Edit().rotated(-90).rotation)
        assertEquals(0, Edit().rotated(90).rotated(270).rotation)
    }

    @Test fun identityIsDetected() {
        assertTrue(Edit().isIdentity)
        assertTrue(!Edit(flipHorizontal = true).isIdentity)
    }

    @Test fun editedNameReplacesExtensionAndHandlesBlank() {
        assertEquals("IMG_1 (edited).jpg", ImageEdits.editedName("IMG_1.png"))
        assertEquals("a.b (edited).jpg", ImageEdits.editedName("a.b.heic"))
        assertEquals("Photo (edited).jpg", ImageEdits.editedName(""))
    }

    @Test fun cropKeepsWholeImageWhenOriginal() {
        assertArrayEquals(intArrayOf(0, 0, 400, 300), ImageEdits.cropRect(400, 300, null))
    }

    @Test fun cropToSquareIsCentredOnLongEdge() {
        assertArrayEquals(intArrayOf(50, 0, 300, 300), ImageEdits.cropRect(400, 300, 1f))
        assertArrayEquals(intArrayOf(0, 50, 300, 300), ImageEdits.cropRect(300, 400, 1f))
    }

    @Test fun cropNeverExceedsSource() {
        val r = ImageEdits.cropRect(400, 300, 16f / 9f)
        assertTrue(r[2] <= 400 && r[3] <= 300 && r[0] >= 0 && r[1] >= 0)
        assertEquals(400, r[2])
    }
}
