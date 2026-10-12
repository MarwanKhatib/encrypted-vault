package com.example.encryptedvault.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTransferMimeTest {
    @Test fun photosAndVideosOfAnyFormatAreAccepted() {
        listOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/heic", "image/heif", "image/x-adobe-dng",
            "video/mp4", "video/quicktime", "video/x-matroska", "video/webm", "video/3gpp").forEach {
            assertTrue(it, MediaTransfer.isPhotoOrVideo(it))
        }
    }

    @Test fun documentsAndUnknownTypesAreRefused() {
        listOf("text/plain", "application/pdf", "application/zip", "application/octet-stream", "audio/mpeg", "").forEach {
            assertFalse(it, MediaTransfer.isPhotoOrVideo(it))
        }
        assertFalse("no reported type", MediaTransfer.isPhotoOrVideo(null))
    }
}
