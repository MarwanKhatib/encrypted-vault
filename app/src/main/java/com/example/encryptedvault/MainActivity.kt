package com.example.encryptedvault

import android.content.res.Configuration
import android.database.ContentObserver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.encryptedvault.crypto.Bip39
import com.example.encryptedvault.crypto.KeyHierarchy
import com.example.encryptedvault.media.AutoImport
import com.example.encryptedvault.media.MediaTransfer
import com.example.encryptedvault.security.FaceDown
import com.example.encryptedvault.security.IdleLock
import com.example.encryptedvault.security.Integrity
import com.example.encryptedvault.security.SessionManager
import com.example.encryptedvault.ui.VaultRoot
import com.example.encryptedvault.ui.settings.AppSettings
import com.example.encryptedvault.work.ImportWorker
import com.example.encryptedvault.work.TrashPurgeWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** FragmentActivity (not ComponentActivity) because BiometricPrompt needs it. */
class MainActivity : FragmentActivity() {
    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hardened builds refuse to run when repackaged as debuggable or when a debugger is attached (see Integrity).
        if (Integrity.check(applicationContext, BuildConfig.HARDENED) != null) {
            finishAndRemoveTask()
            return
        }
        enableEdgeToEdge()
        // Screenshots, screen recording and the recents preview are always blocked, in every build (Gap 6).
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        settings = AppSettings(applicationContext)
        MediaTransfer.sweepTemporaryPlaintext(applicationContext, olderThanMs = 10 * 60_000L) // leftover share/playback plaintext
        TrashPurgeWorker.schedule(applicationContext)
        val words =
            assets
                .open("bip39_english.txt")
                .bufferedReader()
                .readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        val keys = KeyHierarchy(applicationContext)
        val bip39 = Bip39(words)
        setContent { VaultRoot(keys, bip39, settings) }
    }

    /** Locks immediately: drops the session key and removes temporary plaintext such as share copies. */
    private fun lockNow() {
        SessionManager.forceLock()
        MediaTransfer.sweepTemporaryPlaintext(applicationContext, olderThanMs = 0)
    }

    /** Idle timer for the opt-in auto-lock (ADR-010). Reset by every touch or key press. */
    private val idle = IdleLock(SystemClock::elapsedRealtime)
    private val idleHandler = Handler(Looper.getMainLooper())
    private val idleCheck =
        object : Runnable {
            override fun run() {
                val minutes = settings.idleLockMinutes
                if (minutes > 0 && VaultApp.sessionKey != null && idle.isIdleFor(minutes * 60_000L)) lockNow()
                idleHandler.postDelayed(this, IdleLock.CHECK_INTERVAL_MS)
            }
        }

    override fun onUserInteraction() {
        idle.touch()
        super.onUserInteraction()
    }

    /** Face-down lock (opt-in, Settings → Privacy). Listens only while the app is open; the flag is read on each reading. */
    private val faceDown =
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (settings.faceDownLock && FaceDown.isFaceDown(event.values[2]) && VaultApp.sessionKey != null) lockNow()
            }

            override fun onAccuracyChanged(
                sensor: Sensor?,
                accuracy: Int,
            ) = Unit
        }

    /** Gallery changes trigger auto-import (ADR-013). The callback checks the setting and that the vault is open. */
    private val galleryObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = importNewItems()
        }

    /** Copies gallery items added since the watermark into the vault. Does nothing while the vault is locked or auto-import is off. */
    private fun importNewItems() {
        if (!settings.autoImport || VaultApp.sessionKey == null) return
        lifecycleScope.launch(Dispatchers.IO) {
            val since = settings.autoImportSince
            val items = AutoImport.newItems(applicationContext, since).filterNot { MediaTransfer.exceedsLimit(applicationContext, it.first) }
            if (items.isEmpty()) return@launch
            val staged = items.mapNotNull { MediaTransfer.stage(applicationContext, it.first) }
            ImportWorker.enqueueBatches(applicationContext, staged)
            withContext(Dispatchers.Main) { settings.putAutoImportSince(AutoImport.nextWatermark(since, items.map { it.second })) }
        }
    }

    override fun onResume() {
        super.onResume()
        idle.touch()
        idleHandler.postDelayed(idleCheck, IdleLock.CHECK_INTERVAL_MS)
        val sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(faceDown, it, SensorManager.SENSOR_DELAY_UI) }
        contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, galleryObserver)
        contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, galleryObserver)
        importNewItems()
    }

    override fun onPause() {
        idleHandler.removeCallbacks(idleCheck)
        (getSystemService(SENSOR_SERVICE) as SensorManager).unregisterListener(faceDown)
        contentResolver.unregisterContentObserver(galleryObserver)
        super.onPause()
    }

    /** Leaving the screen, including the recents card, locks at once. System pickers and our own share sheet do not (see SessionManager). */
    override fun onStop() {
        super.onStop()
        if (SessionManager.consumeStopLock()) lockNow()
    }

    override fun onMultiWindowModeChanged(
        isInMultiWindowMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        Log.i(TAG, "onMultiWindowModeChanged: $isInMultiWindowMode")
        if (isInMultiWindowMode) lockNow()
    }

    /**
     * Fallback for Gap 6: the window can be resized into split-screen without a multi-window callback on some devices, and the
     * manifest handles size changes in place. Lock whenever the window is in multi-window mode at a configuration change.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.i(TAG, "onConfigurationChanged: inMultiWindow=$isInMultiWindowMode")
        if (isInMultiWindowMode) lockNow()
    }

    private companion object {
        const val TAG = "VaultLock"
    }
}
