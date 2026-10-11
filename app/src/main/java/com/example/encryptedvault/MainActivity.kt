package com.example.encryptedvault

import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.example.encryptedvault.crypto.Bip39
import com.example.encryptedvault.crypto.KeyHierarchy
import com.example.encryptedvault.security.Integrity
import com.example.encryptedvault.security.SessionManager
import com.example.encryptedvault.media.MediaTransfer
import com.example.encryptedvault.ui.VaultRoot
import com.example.encryptedvault.ui.settings.AppSettings

/** FragmentActivity (not ComponentActivity) because BiometricPrompt needs it. */
class MainActivity : FragmentActivity() {
    private lateinit var settings: AppSettings
    private val handler = Handler(Looper.getMainLooper())
    private var stoppedAt = 0L
    private val lockRunnable = Runnable { lockNow() }

    private val debuggable get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hardened builds refuse to run when repackaged as debuggable or when a debugger is attached (see Integrity).
        if (Integrity.check(applicationContext, BuildConfig.HARDENED) != null) { finishAndRemoveTask(); return }
        enableEdgeToEdge()
        settings = AppSettings(applicationContext)
        applySecure(settings.secureScreen)
        MediaTransfer.sweepTemporaryPlaintext(applicationContext, olderThanMs = 10 * 60_000L) // leftover share/playback plaintext
        val words = assets.open("bip39_english.txt").bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }
        val keys = KeyHierarchy(applicationContext)
        val bip39 = Bip39(words)
        setContent { VaultRoot(keys, bip39, settings) }
    }

    /** Hide from recents / screenshots. Debug builds skip it so adb screencap works on the emulator. */
    fun applySecure(on: Boolean) {
        if (on && !debuggable) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /** Locks immediately: drops the session key and removes temporary plaintext such as share copies. */
    private fun lockNow() {
        SessionManager.forceLock()
        MediaTransfer.sweepTemporaryPlaintext(applicationContext, olderThanMs = 0)
    }

    override fun onStop() {
        super.onStop()
        if (!SessionManager.consumeStopLock()) return
        val s = settings.autoLockSec
        if (s <= 0) lockNow() else {
            stoppedAt = SystemClock.elapsedRealtime()
            handler.postDelayed(lockRunnable, s * 1000L)
        }
    }

    override fun onStart() {
        super.onStart()
        handler.removeCallbacks(lockRunnable)
        // Handler time stops in deep sleep; enforce the timeout on return too.
        val s = settings.autoLockSec
        if (stoppedAt > 0 && s > 0 && SystemClock.elapsedRealtime() - stoppedAt >= s * 1000L) lockNow()
        stoppedAt = 0
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (isInMultiWindowMode) lockNow()
    }
}
