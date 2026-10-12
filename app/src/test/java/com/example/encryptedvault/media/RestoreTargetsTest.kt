package com.example.encryptedvault.media

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreTargetsTest {
    @Test
    fun cameraPhotoReturnsToItsFolder() =
        assertEquals(RestoreTarget(RestoreCollection.IMAGES, "DCIM/Camera/"), RestoreTargets.resolve(isVideo = false, sourceFolder = "DCIM/Camera"))

    @Test
    fun picturesSubfolderIsKept() =
        assertEquals(RestoreTarget(RestoreCollection.IMAGES, "Pictures/Screenshots/"), RestoreTargets.resolve(false, "Pictures/Screenshots/"))

    @Test
    fun videoInCameraGoesToVideoCollection() = assertEquals(RestoreTarget(RestoreCollection.VIDEO, "DCIM/Camera/"), RestoreTargets.resolve(true, "DCIM/Camera"))

    @Test
    fun videoCannotGoUnderPictures_soItIsKeptUnderMoviesRestored() =
        assertEquals(RestoreTarget(RestoreCollection.VIDEO, "Movies/Restored/Pictures/Clips/"), RestoreTargets.resolve(true, "Pictures/Clips"))

    @Test
    fun downloadsUseTheDownloadsCollection() {
        assertEquals(RestoreTarget(RestoreCollection.DOWNLOADS, "Download/Trip/"), RestoreTargets.resolve(false, "Download/Trip"))
        assertEquals(RestoreTarget(RestoreCollection.DOWNLOADS, "Download/"), RestoreTargets.resolve(false, "Download"))
    }

    @Test
    fun appPrivateOrUnknownRootsFallBackButKeepTheFolderName() =
        assertEquals(
            RestoreTarget(RestoreCollection.IMAGES, "Pictures/Restored/Android/media/WhatsApp/"),
            RestoreTargets.resolve(false, "Android/media/WhatsApp"),
        )

    @Test
    fun missingFolderUsesDefaultRestoredFolder() {
        assertEquals(RestoreTarget(RestoreCollection.IMAGES, "Pictures/Restored/"), RestoreTargets.resolve(false, null))
        assertEquals(RestoreTarget(RestoreCollection.VIDEO, "Movies/Restored/"), RestoreTargets.resolve(true, "  "))
    }

    @Test
    fun rootMatchingIsCaseInsensitive() = assertEquals(RestoreTarget(RestoreCollection.IMAGES, "dcim/Camera/"), RestoreTargets.resolve(false, "dcim/Camera"))
}
