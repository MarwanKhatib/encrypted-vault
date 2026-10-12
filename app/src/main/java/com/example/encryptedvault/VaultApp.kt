package com.example.encryptedvault

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import javax.crypto.SecretKey

/** Application object: holds the in-memory session key and cleans up artefacts of removed features at startup. */
class VaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The removed ML Kit dependency created a Google telemetry queue database; it holds nothing we want and never will again.
        LEGACY_DATABASES.forEach { deleteDatabase(it) }
    }

    companion object {
        private val LEGACY_DATABASES = listOf("com.google.android.datatransport.events")

        private var key by mutableStateOf<SecretKey?>(null)

        /**
         * The unlocked master key. In memory only (never persisted), cleared on lock, and observable so the UI drops back to the PIN pad
         * the moment it becomes null.
         */
        var sessionKey: SecretKey?
            get() = key
            set(v) {
                key = v
            }
    }
}
