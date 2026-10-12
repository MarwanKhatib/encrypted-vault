package com.example.encryptedvault.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PanicWipeFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun File.write(
        name: String,
        bytes: Int = 16,
    ) = File(this, name).also { it.writeBytes(ByteArray(bytes)) }

    @Test fun removesFilesAndNestedDirectoriesButKeepsTheRoot() {
        val root = tmp.newFolder("vault")
        root.write("a.enc")
        root.write("a.thumb.enc")
        File(root, "nested/deeper").also { it.mkdirs() }.write("b.enc")

        assertEquals(0, PanicWipe.deleteContents(root))
        assertTrue(root.exists())
        assertEquals(0, root.listFiles()!!.size)
    }

    @Test fun emptyOrMissingDirectoriesAreNotAnError() {
        assertEquals(0, PanicWipe.deleteContents(tmp.newFolder("empty")))
        assertEquals(0, PanicWipe.deleteContents(File(tmp.root, "does-not-exist")))
    }

    @Test fun leavesSiblingDirectoriesAlone() {
        val vault = tmp.newFolder("vault").also { it.write("x.enc") }
        val other = tmp.newFolder("other").also { it.write("keep.txt") }
        PanicWipe.deleteContents(vault)
        assertFalse(File(vault, "x.enc").exists())
        assertTrue(File(other, "keep.txt").exists())
    }
}
