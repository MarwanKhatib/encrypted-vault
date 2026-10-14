package com.example.encryptedvault.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ZipBackupTest {
    @get:Rule val tmp = TemporaryFolder()

    private val password = "correct horse battery".toCharArray()
    private val payload = ByteArray(300_000) { (it * 31).toByte() }

    @Test fun samePasswordRestoresTheBackupBytes() {
        val evbk = File(tmp.root, "backup.evbk").apply { writeBytes(payload) }
        val zip = File(tmp.root, "backup.zip")
        ZipBackup.write(evbk, zip, password)

        val out = tmp.newFolder("out")
        val restored = ZipBackup.read(zip, password, out)

        assertArrayEquals(payload, restored.readBytes())
    }

    @Test fun wrongPasswordIsRefused() {
        val evbk = File(tmp.root, "backup.evbk").apply { writeBytes(payload) }
        val zip = File(tmp.root, "backup.zip")
        ZipBackup.write(evbk, zip, password)

        assertThrows(Exception::class.java) { ZipBackup.read(zip, "wrong password".toCharArray(), tmp.newFolder("bad")) }
    }
}
