package com.example.encryptedvault.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Non-secret UI/behaviour preferences. Observable by Compose. */
class AppSettings(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("vault_settings", Context.MODE_PRIVATE)

    /** 0 = follow system, 1 = light, 2 = dark */
    var themeMode by mutableIntStateOf(prefs.getInt("theme", 0)); private set
    var columns by mutableIntStateOf(prefs.getInt("columns", 4)); private set
    /** Seconds in background before auto-lock; 0 = lock immediately. */
    var autoLockSec by mutableIntStateOf(prefs.getInt("autolock", 0)); private set
    var stripExif by mutableStateOf(prefs.getBoolean("strip_exif", true)); private set
    var secureScreen by mutableStateOf(prefs.getBoolean("secure_screen", true)); private set
    var offerDeleteOriginals by mutableStateOf(prefs.getBoolean("offer_delete", true)); private set
    /** iPhone style: newest items at the bottom of the Library grid, opened scrolled to the end. */
    var newestAtBottom by mutableStateOf(prefs.getBoolean("newest_bottom", true)); private set

    /** Stores the theme: 0 follow system, 1 light, 2 dark. */
    fun putThemeMode(v: Int) { themeMode = v; prefs.edit().putInt("theme", v).apply() }
    /** Stores the grid column count (2-7). */
    fun putColumns(v: Int) { columns = v; prefs.edit().putInt("columns", v).apply() }
    /** Stores the background time, in seconds, before auto-lock (0 locks immediately). */
    fun putAutoLockSec(v: Int) { autoLockSec = v; prefs.edit().putInt("autolock", v).apply() }
    /** Stores whether shared copies have their location removed. */
    fun putStripExif(v: Boolean) { stripExif = v; prefs.edit().putBoolean("strip_exif", v).apply() }
    /** Stores whether screenshots and the recents preview are blocked (release builds). */
    fun putSecureScreen(v: Boolean) { secureScreen = v; prefs.edit().putBoolean("secure_screen", v).apply() }
    /** Stores whether to offer deleting the unencrypted originals after an import. */
    fun putOfferDeleteOriginals(v: Boolean) { offerDeleteOriginals = v; prefs.edit().putBoolean("offer_delete", v).apply() }
    /** Stores whether the Library puts the newest items at the bottom, like iOS. */
    fun putNewestAtBottom(v: Boolean) { newestAtBottom = v; prefs.edit().putBoolean("newest_bottom", v).apply() }

}
