package com.example.encryptedvault.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Non-secret UI/behaviour preferences. Observable by Compose. */
class AppSettings(
    ctx: Context,
) {
    private val prefs = ctx.getSharedPreferences("vault_settings", Context.MODE_PRIVATE)

    /** 0 = follow system, 1 = light, 2 = dark */
    var themeMode by mutableIntStateOf(prefs.getInt("theme", 0))
        private set
    var columns by mutableIntStateOf(prefs.getInt("columns", 4))
        private set
    var stripExif by mutableStateOf(prefs.getBoolean("strip_exif", true))
        private set
    var offerDeleteOriginals by mutableStateOf(prefs.getBoolean("offer_delete", true))
        private set

    /** Minutes of no input before the vault locks; 0 (the default) turns idle lock off (ADR-010). */
    var idleLockMinutes by mutableIntStateOf(prefs.getInt("idle_lock_minutes", 0))
        private set

    /** Copies new gallery photos and videos into the vault while it is open (ADR-013). Off by default. */
    var autoImport by mutableStateOf(prefs.getBoolean("auto_import", false))
        private set

    /** Epoch seconds: gallery items added after this are auto-imported. Set when auto-import is turned on. */
    var autoImportSince by mutableLongStateOf(prefs.getLong("auto_import_since", 0L))
        private set

    /** Locks when the phone lies face down while the app is open (see FaceDown). */
    var faceDownLock by mutableStateOf(prefs.getBoolean("face_down_lock", false))
        private set

    /** Epoch millis when the recovery file was last confirmed; 0 means never. */
    var recoveryCheckedAt by mutableLongStateOf(prefs.getLong("recovery_checked_at", 0L))
        private set

    /** iPhone style: newest items at the bottom of the Library grid, opened scrolled to the end. */
    var newestAtBottom by mutableStateOf(prefs.getBoolean("newest_bottom", true))
        private set

    /** Stores the theme: 0 follow system, 1 light, 2 dark. */
    fun putThemeMode(v: Int) {
        themeMode = v
        prefs.edit().putInt("theme", v).apply()
    }

    /** Stores the grid column count (2-7). */
    fun putColumns(v: Int) {
        columns = v
        prefs.edit().putInt("columns", v).apply()
    }

    /** Stores whether shared copies have their location removed. */
    fun putStripExif(v: Boolean) {
        stripExif = v
        prefs.edit().putBoolean("strip_exif", v).apply()
    }

    /** Stores whether to offer deleting the unencrypted originals after an import. */
    fun putOfferDeleteOriginals(v: Boolean) {
        offerDeleteOriginals = v
        prefs.edit().putBoolean("offer_delete", v).apply()
    }

    /** Turns auto-import on (imports only items added from [nowSec] on) or off. Turning it off keeps the watermark. */
    fun putAutoImport(
        enabled: Boolean,
        nowSec: Long,
    ) {
        autoImport = enabled
        if (enabled) autoImportSince = nowSec
        prefs
            .edit()
            .putBoolean("auto_import", enabled)
            .putLong("auto_import_since", autoImportSince)
            .apply()
    }

    /** Moves the auto-import watermark forward after a batch was queued (epoch seconds). */
    fun putAutoImportSince(v: Long) {
        autoImportSince = v
        prefs.edit().putLong("auto_import_since", v).apply()
    }

    /** Stores the idle-lock timeout in minutes; 0 turns it off. */
    fun putIdleLockMinutes(v: Int) {
        idleLockMinutes = v
        prefs.edit().putInt("idle_lock_minutes", v).apply()
    }

    /** Stores whether the phone locks when turned face down. */
    fun putFaceDownLock(v: Boolean) {
        faceDownLock = v
        prefs.edit().putBoolean("face_down_lock", v).apply()
    }

    /** Stores when the recovery file was last confirmed (epoch millis). */
    fun putRecoveryChecked(now: Long) {
        recoveryCheckedAt = now
        prefs.edit().putLong("recovery_checked_at", now).apply()
    }

    /** Stores whether the Library puts the newest items at the bottom, like iOS. */
    fun putNewestAtBottom(v: Boolean) {
        newestAtBottom = v
        prefs.edit().putBoolean("newest_bottom", v).apply()
    }
}
