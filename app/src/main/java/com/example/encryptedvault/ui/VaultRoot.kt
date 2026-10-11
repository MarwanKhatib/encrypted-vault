package com.example.encryptedvault.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.example.encryptedvault.MainActivity
import com.example.encryptedvault.VaultApp
import com.example.encryptedvault.crypto.Bip39
import com.example.encryptedvault.crypto.KeyHierarchy
import com.example.encryptedvault.security.Biometric.findActivity
import com.example.encryptedvault.ui.auth.LockScreen
import com.example.encryptedvault.ui.auth.PhraseScreen
import com.example.encryptedvault.ui.auth.RecoverScreen
import com.example.encryptedvault.ui.auth.SetupScreen
import com.example.encryptedvault.ui.settings.AppSettings
import com.example.encryptedvault.ui.theme.VaultTheme

/** Top-level screens [VaultRoot] switches between. */
private enum class Stage { SETUP, PHRASE, LOCK, RECOVER, MAIN }

/** Top-level router: first-run setup → lock screen → unlocked app. Drops back to the lock screen whenever the session key is cleared. */
@Composable
fun VaultRoot(keys: KeyHierarchy, bip39: Bip39, settings: AppSettings) {
    var stage by remember { mutableStateOf(if (!keys.isSetUp()) Stage.SETUP else Stage.LOCK) }
    var phrase by remember { mutableStateOf<List<String>>(emptyList()) }
    val context = LocalContext.current
    val view = LocalView.current

    // Gap 6: discard touches while another window overlays this one.
    SideEffect { view.filterTouchesWhenObscured = true }
    LaunchedEffect(settings.secureScreen) { (context.findActivity() as? MainActivity)?.applySecure(settings.secureScreen) }

    val unlocked = VaultApp.sessionKey != null
    LaunchedEffect(unlocked) { if (!unlocked && stage == Stage.MAIN) stage = Stage.LOCK }

    VaultTheme(settings.themeMode) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (stage) {
                Stage.SETUP -> SetupScreen(keys, bip39) { words -> phrase = words; stage = Stage.PHRASE }
                Stage.PHRASE -> PhraseScreen(phrase) { phrase = emptyList(); stage = Stage.MAIN }
                Stage.LOCK -> LockScreen(keys, bip39, onRecover = { stage = Stage.RECOVER }) { stage = Stage.MAIN }
                Stage.RECOVER -> RecoverScreen(keys, bip39, onBack = { stage = Stage.LOCK }) { stage = Stage.MAIN }
                Stage.MAIN -> if (unlocked) MainScaffold(keys, settings)
            }
        }
    }
}
