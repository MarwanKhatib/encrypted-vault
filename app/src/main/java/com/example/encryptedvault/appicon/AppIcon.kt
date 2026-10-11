package com.example.encryptedvault.appicon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.encryptedvault.R

/**
 * The launcher icons the user can pick. Each one is an `<activity-alias>` in the manifest that points at
 * [com.example.encryptedvault.MainActivity]; exactly one alias is enabled at a time. [VAULT] is the real
 * icon, the rest are disguises with an unrelated name and picture.
 */
enum class AppIcon(
    /** Alias class name, relative to the package (see AndroidManifest.xml). */
    val aliasName: String,
    @StringRes val labelRes: Int,
    /** Foreground vector used for previews in Settings. */
    @DrawableRes val previewRes: Int,
    @ColorRes val backgroundRes: Int,
) {
    VAULT(".alias.VaultAlias", R.string.app_name, R.drawable.ic_icon_vault, R.color.icon_bg_vault),
    WEATHER(".alias.WeatherAlias", R.string.disguise_weather, R.drawable.ic_icon_weather, R.color.icon_bg_weather),
    CALCULATOR(".alias.CalculatorAlias", R.string.disguise_calculator, R.drawable.ic_icon_calculator, R.color.icon_bg_calculator),
    NOTES(".alias.NotesAlias", R.string.disguise_notes, R.drawable.ic_icon_notes, R.color.icon_bg_notes),
    CLOCK(".alias.ClockAlias", R.string.disguise_clock, R.drawable.ic_icon_clock, R.color.icon_bg_clock);
}

/** Reads and switches the active launcher alias through [PackageManager]. */
object AppIconManager {

    /** Component name of the launcher alias that belongs to [icon]. */
    private fun component(context: Context, icon: AppIcon) = ComponentName(context.packageName, context.packageName + icon.aliasName)

    /** The icon currently shown on the launcher ([AppIcon.VAULT] unless another alias was enabled). */
    fun current(context: Context): AppIcon = AppIcon.entries.firstOrNull { icon ->
        when (context.packageManager.getComponentEnabledSetting(component(context, icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.VAULT // manifest default
            else -> false
        }
    } ?: AppIcon.VAULT

    /** Enables [icon] and disables the others. The new alias is enabled first so the app never has zero launcher entries. */
    fun select(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(component(context, icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        AppIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(component(context, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
