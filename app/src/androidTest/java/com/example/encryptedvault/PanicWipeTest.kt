package com.example.encryptedvault

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.encryptedvault.crypto.Bip39
import com.example.encryptedvault.crypto.KeyHierarchy
import com.example.encryptedvault.crypto.VaultFiles
import com.example.encryptedvault.data.VaultDatabase
import com.example.encryptedvault.data.VaultStorage
import com.example.encryptedvault.security.PanicWipe
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.io.ByteArrayInputStream

/** End-to-end check of the duress wipe on a real device: files, database, cache and keys all go; a new empty vault opens. */
@RunWith(AndroidJUnit4::class)
class PanicWipeTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var keys: KeyHierarchy
    private lateinit var bip39: Bip39
    private val realPin = "483915".toCharArray()
    private val duressPin = "729164".toCharArray()

    @Before fun setUp() {
        keys = KeyHierarchy(context)
        keys.destroyAllKeyMaterial()
        VaultDatabase.destroy(context)
        VaultStorage.directory(context).listFiles()?.forEach { it.delete() }
        bip39 = Bip39(context.assets.open("bip39_english.txt").bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() })
    }

    @After fun tearDown() {
        keys.destroyAllKeyMaterial()
        VaultDatabase.destroy(context)
        VaultStorage.directory(context).listFiles()?.forEach { it.delete() }
    }

    /** Creates a vault holding [count] encrypted files and a staged plaintext file in the cache; returns the old key and one file. */
    private fun populate(count: Int = 3): Pair<javax.crypto.SecretKey, File> {
        keys.setUp(realPin, bip39)
        val oldKey = keys.unlockWithPin(realPin)!!
        val files = (1..count).map { i -> VaultStorage.content(context, "hash$i").also { VaultFiles.encryptAtomic(oldKey, ByteArrayInputStream(ByteArray(2048) { i.toByte() }), it) } }
        File(context.cacheDir, "import").apply { mkdirs() }.let { File(it, "staged.mp4").writeBytes(ByteArray(100)) }
        VaultDatabase.get(context).openHelper.writableDatabase // make sure vault.db exists on disk
        return oldKey to files.first()
    }

    @Test fun wipeRemovesEveryEncryptedFileAndTheCache() {
        populate()
        PanicWipe.execute(context, keys, duressPin, bip39)
        assertEquals(0, VaultStorage.directory(context).listFiles()!!.size)
        assertEquals(0, context.cacheDir.listFiles()!!.count { it.name != "vault.db.lck" && !it.name.endsWith(".lck") })
    }

    @Test fun oldKeyAndOldPinAreUselessAfterTheWipe() {
        val (oldKey, oldFile) = populate()
        val survivor = File(context.cacheDir, "copy.enc").also { VaultStorage.content(context, "hash1").copyTo(it, overwrite = true) }
        PanicWipe.execute(context, keys, duressPin, bip39)
        assertNull("old PIN must not unlock the new vault", keys.unlockWithPin(realPin))
        // Even a copy of the ciphertext made before the wipe cannot be read with the new master key.
        val newKey = keys.unlockWithPin(duressPin)!!
        assertFalse(oldKey.encoded.contentEquals(newKey.encoded))
        assertThrows(IOException::class.java) { VaultFiles.decryptStream(newKey, survivor).use { it.readBytes() } }
        assertFalse(oldFile.exists())
    }

    @Test fun theEnteredDuressPinBecomesTheNormalPinOfTheEmptyVault() {
        populate()
        val newKey = PanicWipe.execute(context, keys, duressPin, bip39)
        assertNotNull(newKey)
        assertTrue(keys.isSetUp())
        assertArrayEquals(newKey.encoded, keys.unlockWithPin(duressPin)!!.encoded)
        assertEquals(6, keys.pinLength())
    }

    @Test fun theDuressPinIsDisarmedAfterTheWipe() {
        populate()
        keys.setDuressPin(realPin, duressPin)
        PanicWipe.execute(context, keys, duressPin, bip39)
        assertFalse(keys.hasDuressPin())
    }

    @Test fun theDatabaseIsEmptyAfterTheWipe() {
        populate()
        PanicWipe.execute(context, keys, duressPin, bip39)
        val count = VaultDatabase.get(context).openHelper.readableDatabase.query("SELECT count(*) FROM photos").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(0, count)
    }
}
