package com.example.encryptedvault.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class PlainExportTest {
    @Test fun plainFieldIsNotQuoted() {
        assertEquals("beach", PlainExport.csvField("beach"))
    }

    @Test fun fieldWithCommaIsQuoted() {
        assertEquals("\"Paris, 2024\"", PlainExport.csvField("Paris, 2024"))
    }

    @Test fun quotesInsideAFieldAreDoubled() {
        assertEquals("\"say \"\"hi\"\"\"", PlainExport.csvField("say \"hi\""))
    }

    @Test fun lineBreaksForceQuoting() {
        assertEquals("\"line\nbreak\"", PlainExport.csvField("line\nbreak"))
    }

    @Test fun manifestHasHeaderAndCrLfRows() {
        val row = listOf("a.jpg", "IMG 1.jpg", "DCIM/Camera", "1700000000000", "true", "false", "sunset, lake", "Trips")
        assertEquals(
            "file,original_name,folder,taken_at_epoch_ms,favorite,hidden,caption,albums\r\n" +
                "a.jpg,IMG 1.jpg,DCIM/Camera,1700000000000,true,false,\"sunset, lake\",Trips\r\n",
            PlainExport.manifest(listOf(row)),
        )
    }

    @Test fun manifestWithNoItemsIsHeaderOnly() {
        assertEquals("file,original_name,folder,taken_at_epoch_ms,favorite,hidden,caption,albums\r\n", PlainExport.manifest(emptyList()))
    }

    @Test fun slashesInNamesBecomeUnderscores() {
        assertEquals("a_b_c.jpg", PlainExport.safeName("a/b\\c.jpg"))
    }

    @Test fun blankNameFallsBackToItem() {
        assertEquals("item", PlainExport.safeName("   "))
    }
}
