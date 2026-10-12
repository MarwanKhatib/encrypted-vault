package com.example.encryptedvault.security

import android.content.Context
import androidx.work.WorkManager
import com.example.encryptedvault.crypto.Bip39
import com.example.encryptedvault.crypto.KeyHierarchy
import com.example.encryptedvault.data.VaultDatabase
import com.example.encryptedvault.data.VaultRepository
import com.example.encryptedvault.data.VaultStorage
import java.io.File
import javax.crypto.SecretKey

/**
 * Destroys every photo and video in the vault when the duress PIN is entered, then opens a brand-new empty vault
 * under that same PIN so the screen looks like a normal (just empty) vault instead of an obviously wiped one.
 *
 * Order matters:
 *  1. **Crypto-erase first.** Deleting the wrapped master key and both Keystore keys makes all ciphertext
 *     unrecoverable immediately, even if the process is killed during the slower steps below. This is the real guarantee:
 *     overwriting flash files is not reliable on modern phones (wear levelling keeps old blocks), so we do not pretend to.
 *  2. Cancel background work so nothing re-creates rows or files.
 *  3. Delete the database, the encrypted files and the cache (which may hold staged plaintext).
 *  4. Create a fresh vault with [newPin] and return its session key.
 *
 * The recovery phrase of the new vault is intentionally discarded: whoever forced the duress PIN must not be handed a usable phrase.
 */
object PanicWipe {
    /**
     * Wipes everything and returns the session key of the new empty vault.
     * Blocking and CPU heavy (two PBKDF2 runs): call from a background dispatcher.
     */
    fun execute(
        context: Context,
        keys: KeyHierarchy,
        newPin: CharArray,
        bip39: Bip39,
    ): SecretKey {
        val app = context.applicationContext
        SessionManager.forceLock()
        keys.destroyAllKeyMaterial()

        WorkManager.getInstance(app).apply {
            cancelAllWork()
            pruneWork()
        }
        VaultRepository.reset()
        VaultDatabase.destroy(app)
        deleteContents(VaultStorage.directory(app))
        deleteContents(app.cacheDir)

        keys.setUp(newPin, bip39)
        return checkNotNull(keys.unlockWithPin(newPin)) { "fresh vault could not be unlocked" }
    }

    /**
     * Deletes everything inside [dir] (files and sub-directories) but keeps [dir] itself.
     * @return the number of entries that could not be deleted, 0 on full success.
     */
    internal fun deleteContents(dir: File): Int = dir.listFiles().orEmpty().count { !it.deleteRecursively() }
}
