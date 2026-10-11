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

    @Test fun rotatingAndFlippingResetTheCrop() {
        val cropped = Edit(crop = CropBox(0.1f, 0.1f, 0.9f, 0.9f))
        assertTrue(cropped.rotated(90).crop.isFull)
        assertTrue(cropped.flipped().crop.isFull)
    }

    @Test fun ratioPresetCentresTheLargestFrame() {
        assertFrame(CropBox(0.125f, 0f, 0.875f, 1f), ImageEdits.presetCrop(1f, 4f / 3f))
        assertFrame(CropBox(0f, 0.125f, 1f, 0.875f), ImageEdits.presetCrop(1f, 0.75f))
    }

    @Test fun ratioPresetMatchingTheImageIsFullFrame() {
        assertFrame(CropBox(), ImageEdits.presetCrop(4f / 3f, 4f / 3f))
    }

    @Test fun selectingAFreePresetKeepsTheCurrentCrop() {
        val drawn = CropBox(0.2f, 0.2f, 0.8f, 0.8f)
        assertFrame(drawn, Edit(crop = drawn).withAspect(CropAspect.FREE, 1f).crop)
    }

    @Test fun orientedAspectSwapsOnQuarterTurns() {
        assertEquals(0.75f, ImageEdits.orientedAspect(400, 300, 90), 1e-6f)
        assertEquals(4f / 3f, ImageEdits.orientedAspect(400, 300, 180), 1e-6f)
    }

    @Test fun pixelRectOfFullCropIsTheWholeImage() {
        assertArrayEquals(intArrayOf(0, 0, 400, 300), CropBox().pixelRect(400, 300))
    }

    @Test fun pixelRectOfSquarePresetIsCentred() {
        val square = ImageEdits.presetCrop(1f, 2f)
        assertArrayEquals(intArrayOf(100, 0, 200, 200), square.pixelRect(400, 200))
    }

    @Test fun pixelRectIsNeverEmpty() {
        assertArrayEquals(intArrayOf(5, 5, 1, 1), CropBox(0.5f, 0.5f, 0.5f, 0.5f).pixelRect(10, 10))
    }

    @Test fun draggingBottomRightShrinksFromTheOppositeCorner() {
        assertFrame(CropBox(0f, 0f, 0.5f, 1f), CropBox().dragCorner(CropCorner.BOTTOM_RIGHT, -0.5f, 0f, 1f, null))
    }

    @Test fun draggingPastTheImageStopsAtTheEdge() {
        assertFrame(CropBox(), CropBox().dragCorner(CropCorner.TOP_LEFT, -2f, -2f, 1f, null))
    }

    @Test fun frameNeverShrinksBelowMinimumSide() {
        val frame = CropBox(0f, 0f, 0.5f, 1f).dragCorner(CropCorner.TOP_LEFT, 0.9f, 0f, 1f, null)
        assertEquals(0.45f, frame.left, 1e-5f)
        assertEquals(0.5f, frame.right, 1e-5f)
    }

    @Test fun lockedSquareOnSquareImageStaysSquare() {
        val frame = CropBox().dragCorner(CropCorner.BOTTOM_RIGHT, -0.2f, 0f, 1f, 1f)
        assertFrame(CropBox(0f, 0f, 0.8f, 0.8f), frame)
    }

    @Test fun lockedSquareOnWideImageStaysSquareInPixels() {
        // Image is 2:1, so a square frame spans half the width for the full height.
        val frame = CropBox().dragCorner(CropCorner.BOTTOM_RIGHT, -0.2f, 0f, 2f, 1f)
        assertFrame(CropBox(0f, 0f, 0.5f, 1f), frame)
    }

    @Test fun editedNameReplacesExtensionAndHandlesBlank() {
        assertEquals("IMG_1 (edited).jpg", ImageEdits.editedName("IMG_1.png"))
        assertEquals("a.b (edited).jpg", ImageEdits.editedName("a.b.heic"))
        assertEquals("Photo (edited).jpg", ImageEdits.editedName(""))
    }

    private fun assertFrame(expected: CropBox, actual: CropBox) {
        assertEquals(expected.left.toDouble(), actual.left.toDouble(), 1e-5)
        assertEquals(expected.top.toDouble(), actual.top.toDouble(), 1e-5)
        assertEquals(expected.right.toDouble(), actual.right.toDouble(), 1e-5)
        assertEquals(expected.bottom.toDouble(), actual.bottom.toDouble(), 1e-5)
    }
}
