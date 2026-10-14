package com.example.encryptedvault.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.encryptedvault.VaultApp
import com.example.encryptedvault.appicon.AppIcon
import com.example.encryptedvault.appicon.AppIconManager
import com.example.encryptedvault.crypto.KeyHierarchy
import com.example.encryptedvault.crypto.PinPolicy
import com.example.encryptedvault.crypto.UnlockAttempts
import com.example.encryptedvault.data.VaultStorage
import com.example.encryptedvault.media.AutoImport
import com.example.encryptedvault.security.Biometric
import com.example.encryptedvault.security.Biometric.findActivity
import com.example.encryptedvault.ui.auth.MIN_PIN
import com.example.encryptedvault.ui.auth.PinField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Settings: unlock methods, appearance, privacy, and the home-screen icon (disguise). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    keys: KeyHierarchy,
    onBack: () -> Unit,
    onLockNow: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var biometricOn by remember { mutableStateOf(keys.biometricEnabled()) }
    val biometricAvailable = remember { Biometric.available(context) }
    var showChangePin by remember { mutableStateOf(false) }
    var showDuress by remember { mutableStateOf(false) }
    var duressSet by remember { mutableStateOf(keys.hasDuressPin()) }
    var currentIcon by remember { mutableStateOf(AppIconManager.current(context)) }
    val mediaPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.all { it }) {
                settings.putAutoImport(true, System.currentTimeMillis() / 1000)
            } else {
                scope.launch { snackbar.showSnackbar("Allow photo and video access to turn on auto-import") }
            }
        }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("Status") {
                val hardware = remember { keys.wrapKeyInSecureHardware() }
                val vaultBytes by produceState(initialValue = -1L) { value = withContext(Dispatchers.IO) { sumFileBytes(VaultStorage.directory(context)) } }
                StatusLine("Vault", if (VaultApp.sessionKey != null) "Unlocked" else "Locked")
                StatusLine(
                    "Auto-lock",
                    if (settings.idleLockMinutes == 0) "Off (locks when the app leaves the screen)" else "After ${settings.idleLockMinutes} min idle",
                )
                StatusLine("Face-down lock", if (settings.faceDownLock) "On (while the app is open)" else "Off")
                StatusLine("Auto-import", if (settings.autoImport) "On (while the vault is open)" else "Off")
                StatusLine(
                    "Fingerprint",
                    when {
                        !biometricAvailable -> "Not available"
                        biometricOn -> "On"
                        else -> "Off"
                    },
                )
                StatusLine(
                    "Key storage",
                    when (hardware) {
                        true -> "Secure hardware (TEE or StrongBox)"
                        false -> "Software only: this device does not report hardware-backed keys"
                        null -> "Not created yet"
                    },
                )
                val shownVault = if (vaultBytes < 0) "…" else formatBytes(vaultBytes)
                StatusLine("Vault size", "$shownVault encrypted · ${formatBytes(context.filesDir.usableSpace)} free")
            }

            Section("Unlock") {
                SwitchRow(
                    "Fingerprint unlock",
                    when {
                        !biometricAvailable -> "No enrolled fingerprint/strong biometric on this device"
                        biometricOn -> "Unlock with fingerprint; PIN and recovery phrase still work"
                        else -> "Use your fingerprint instead of typing the PIN"
                    },
                    checked = biometricOn,
                    enabled = biometricAvailable,
                ) { enable ->
                    if (!enable) {
                        keys.disableBiometric()
                        biometricOn = false
                        return@SwitchRow
                    }
                    val activity = context.findActivity()
                    val masterKey = VaultApp.sessionKey
                    if (activity == null || masterKey == null) return@SwitchRow
                    runCatching { keys.biometricEnableCipher() }
                        .onSuccess { cipher ->
                            Biometric.prompt(
                                activity,
                                "Enable fingerprint unlock",
                                "Touch the sensor to confirm",
                                cipher,
                                onSuccess = { authorised ->
                                    keys.completeEnableBiometric(authorised, masterKey)
                                    biometricOn = true
                                },
                                onFail = { message -> if (message != null) scope.launch { snackbar.showSnackbar(message) } },
                            )
                        }.onFailure { scope.launch { snackbar.showSnackbar("Could not enable fingerprint") } }
                }
                HorizontalDivider()
                ActionRow("Change PIN", "Re-wraps your key; photos are not re-encrypted", "Change") { showChangePin = true }
                HorizontalDivider()
                ActionRow(
                    "Duress PIN",
                    if (duressSet) {
                        "Armed: entering it erases every photo and video, then opens an empty vault"
                    } else {
                        "A second PIN that silently erases everything when entered"
                    },
                    if (duressSet) "Remove" else "Set up",
                ) { showDuress = true }
                Text(
                    "The duress PIN opens an empty vault. The PIN screen looks the same for every PIN, so nothing on screen shows which one was entered.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section("Failed PIN attempts") {
                val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
                val log = remember { keys.attempts.recentFailures().asReversed() }
                Text(
                    "Times of the last ${UnlockAttempts.LOG_CAP} wrong PINs, newest first. Only times are kept; no photo is taken.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (log.isEmpty()) {
                    Text("None recorded.", style = MaterialTheme.typography.bodySmall)
                } else {
                    log.forEach { Text(dateFormat.format(Date(it)), style = MaterialTheme.typography.bodySmall) }
                }
            }

            Section("Backup") {
                Text(
                    "Keep a copy of your vault off this phone. Backups are encrypted with a passphrase you choose; without it, the file cannot be opened.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BackupRows { message -> scope.launch { snackbar.showSnackbar(message) } }
            }

            Section("Recovery") {
                val due = RecoveryReminder.isDue(settings.recoveryCheckedAt, System.currentTimeMillis())
                Text(
                    when {
                        settings.recoveryCheckedAt <= 0 -> "Recovery file: never confirmed. Save it at setup, then re-check it here."
                        due -> "Recovery file: last confirmed more than 90 days ago. Open it and check it still has all 24 words."
                        else -> "Recovery file: confirmed recently."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (due || settings.recoveryCheckedAt <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ActionRow("Re-check recovery file", "Confirm you opened the saved file and it still has all 24 words", "Confirmed") {
                    settings.putRecoveryChecked(System.currentTimeMillis())
                }
            }

            Section("App icon") {
                Text(
                    "Change how the app looks on your home screen. Pick a disguise to hide that this is a vault.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "A disguise changes the name and icon only. It stops casual glances, not a determined person or a forensic check.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AppIcon.entries.forEach { icon ->
                        IconChoice(icon, selected = icon == currentIcon) {
                            AppIconManager.select(context, icon)
                            currentIcon = icon
                            scope.launch {
                                snackbar.showSnackbar(
                                    "Icon changed to ${context.getString(icon.labelRes)}. Your launcher may take a few seconds to refresh.",
                                )
                            }
                        }
                    }
                }
            }

            Section("Appearance") {
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                Choices(listOf("System" to 0, "Light" to 1, "Dark" to 2), settings.themeMode) { settings.putThemeMode(it) }
                Spacer(Modifier.height(4.dp))
                Text("Grid columns: ${settings.columns}", style = MaterialTheme.typography.titleSmall)
                Slider(settings.columns.toFloat(), { settings.putColumns(it.toInt()) }, valueRange = 2f..7f, steps = 4)
                SwitchRow(
                    "Newest at bottom",
                    "Like iPhone: the Library opens at your latest photos",
                    settings.newestAtBottom,
                ) { settings.putNewestAtBottom(it) }
            }

            Section("Privacy") {
                Text(
                    "Screenshots and the recents preview are always blocked. The vault locks as soon as the app leaves the screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                SwitchRow("Remove location when sharing", "Strips GPS EXIF from the temporary shared copy", settings.stripExif) { settings.putStripExif(it) }
                HorizontalDivider()
                Text("Lock after idle", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Locks when there is no touch or key input for the chosen time. Off keeps the instant lock only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Choices(listOf("Off" to 0, "1 min" to 1, "5 min" to 5, "15 min" to 15), settings.idleLockMinutes) { settings.putIdleLockMinutes(it) }
                HorizontalDivider()
                SwitchRow(
                    "Lock when face down",
                    "Locks when the screen points at the floor while the app is open",
                    settings.faceDownLock,
                ) { settings.putFaceDownLock(it) }
                HorizontalDivider()
                SwitchRow(
                    "Import new photos automatically",
                    "While the vault is open, new photos and videos are copied in. Originals stay in your gallery, unencrypted. " +
                        "After each import you can delete them with one tap.",
                    settings.autoImport,
                ) { on ->
                    when {
                        !on -> settings.putAutoImport(false, 0)
                        AutoImport.granted(context) -> settings.putAutoImport(true, System.currentTimeMillis() / 1000)
                        else -> mediaPermission.launch(AutoImport.permissions())
                    }
                }
                HorizontalDivider()
                SwitchRow("Offer to delete originals", "After import, ask Android to delete the unencrypted originals", settings.offerDeleteOriginals) {
                    settings.putOfferDeleteOriginals(it)
                }
            }

            Section("About") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Vault 0.2.0", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "AES-256-GCM · fully offline · no INTERNET permission",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "Your recovery phrase was shown once at setup and is never stored. " +
                        "If you lose both your PIN and the phrase, the photos cannot be recovered. " +
                        "Deleted items stay in Recently Deleted for 30 days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FilledTonalButton(onClick = onLockNow, Modifier.fillMaxWidth()) { Text("Lock now") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDuress) {
        DuressPinDialog(keys, armed = duressSet) { nowArmed ->
            showDuress = false
            if (nowArmed != null) {
                duressSet = nowArmed
                scope.launch { snackbar.showSnackbar(if (nowArmed) "Duress PIN armed" else "Duress PIN removed") }
            }
        }
    }
    if (showChangePin) {
        ChangePinDialog(keys) { result ->
            showChangePin = false
            if (result != null) scope.launch { snackbar.showSnackbar(if (result) "PIN changed" else "Current PIN is wrong") }
        }
    }
}

/** Label and value on one line of the status panel. */
@Composable
private fun StatusLine(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

/** Titled card that groups related settings. */
@Composable
private fun Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

/** A setting with a title, an explanatory line and an on/off switch. */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange, enabled = enabled)
    }
}

/** A setting that opens a dialog or action through a text button. */
@Composable
internal fun ActionRow(
    title: String,
    subtitle: String,
    buttonLabel: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick, enabled = enabled) { Text(buttonLabel) }
    }
}

/** Row of mutually exclusive chips; [selected] is the current value. */
@Composable
private fun Choices(
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) -> FilterChip(selected == value, { onSelect(value) }, { Text(label) }) }
    }
}

/** One launcher-icon option: a rounded preview with its home-screen label underneath. */
@Composable
private fun IconChoice(
    icon: AppIcon,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(Modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(60.dp)
                .background(colorResource(icon.backgroundRes), RoundedCornerShape(18.dp))
                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) { Image(painterResource(icon.previewRes), null, Modifier.size(60.dp)) }
        Text(
            stringResource(icon.labelRes),
            Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Dialog that changes the PIN: needs the current PIN and a valid, non-weak new PIN of the same length when a duress PIN is armed. */
@Composable
private fun ChangePinDialog(
    keys: KeyHierarchy,
    onDone: (changed: Boolean?) -> Unit,
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onDone(null) },
        title = { Text("Change PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField("Current PIN", oldPin) { oldPin = it }
                PinField("New PIN (min $MIN_PIN digits)", newPin) { newPin = it }
                PinField("Confirm new PIN", confirm) { confirm = it }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && PinPolicy.isValid(newPin) && !PinPolicy.isWeak(newPin) && newPin == confirm && oldPin.isNotEmpty(), onClick = {
                busy = true
                scope.launch { onDone(withContext(Dispatchers.Default) { keys.changePin(oldPin.toCharArray(), newPin.toCharArray()) }) }
            }) { Text(if (busy) "Working…" else "Change") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { onDone(null) }) { Text("Cancel") } },
    )
}

/**
 * Sets up or removes the duress PIN. Arming asks for the normal PIN (so a bystander cannot arm it) and a second PIN of the
 * same length; removing asks for the normal PIN. [onDone] receives true (armed), false (removed) or null (cancelled).
 */
@Composable
private fun DuressPinDialog(
    keys: KeyHierarchy,
    armed: Boolean,
    onDone: (armed: Boolean?) -> Unit,
) {
    var realPin by remember { mutableStateOf("") }
    var duress by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onDone(null) },
        title = { Text(if (armed) "Remove duress PIN" else "Set duress PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!armed) {
                    Text(
                        "Entering this PIN on the lock screen permanently erases every photo and video and opens an empty vault. " +
                            "It cannot be undone, and your recovery phrase will not bring the photos back.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                PinField("Your normal PIN", realPin) {
                    realPin = it
                    error = null
                }
                if (!armed) {
                    PinField("Duress PIN (same length)", duress) {
                        duress = it
                        error = null
                    }
                    PinField("Confirm duress PIN", confirm) {
                        confirm = it
                        error = null
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && realPin.isNotEmpty() && (armed || (duress.isNotEmpty() && duress == confirm)), onClick = {
                busy = true
                scope.launch {
                    val problem =
                        withContext(Dispatchers.Default) {
                            if (armed) {
                                if (keys.clearDuressPin(realPin.toCharArray())) null else "Your PIN is wrong"
                            } else {
                                keys.setDuressPin(realPin.toCharArray(), duress.toCharArray())
                            }
                        }
                    busy = false
                    if (problem == null) onDone(!armed) else error = problem
                }
            }) {
                Text(
                    if (busy) {
                        "Working…"
                    } else if (armed) {
                        "Remove"
                    } else {
                        "Arm"
                    },
                )
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { onDone(null) }) { Text("Cancel") } },
    )
}
