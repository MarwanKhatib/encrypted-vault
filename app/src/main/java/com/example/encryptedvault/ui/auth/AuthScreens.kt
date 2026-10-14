package com.example.encryptedvault.ui.auth

import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.encryptedvault.VaultApp
import com.example.encryptedvault.crypto.Bip39
import com.example.encryptedvault.crypto.KeyHierarchy
import com.example.encryptedvault.crypto.PinPolicy
import com.example.encryptedvault.crypto.UnlockAttempts
import com.example.encryptedvault.security.Biometric
import com.example.encryptedvault.security.Biometric.findActivity
import com.example.encryptedvault.security.PanicWipe
import com.example.encryptedvault.ui.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val MIN_PIN = PinPolicy.MIN_LENGTH
private const val MAX_PIN = PinPolicy.MAX_LENGTH
private const val UNLOCK_MS = 380
private const val UNLOCK_SWELL = 0.08f
private const val BREATH_MS = 2400

/** Short horizontal shake for a wrong PIN: the entry field rejects the input without a dialog. */
private suspend fun Animatable<Float, AnimationVector1D>.shakeOnce() {
    for (x in SHAKE_DP) animateTo(x, tween(durationMillis = SHAKE_STEP_MS))
}

/** Horizontal offsets of the shake, in dp, from left to right and back to the centre. */
private val SHAKE_DP = listOf(-14f, 14f, -10f, 10f, -4f, 0f)
private const val SHAKE_STEP_MS = 45

// ---------------------------------------------------------------------------------------------
// Shared bits

/** Masked numeric field limited to digits and [MAX_PIN] characters, for places that use the system keyboard (recovery, settings dialogs). */
@Composable
fun PinField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value,
        { if (it.all(Char::isDigit) && it.length <= MAX_PIN) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Scrollable, padded column that lays out the full-screen auth pages. */
@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) =
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )

/** Round lock icon shown at the top of the auth screens. */
@Composable
private fun Badge(size: Dp = 72.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Lock, null, Modifier.size(size / 2), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** One dot per digit; [total] is the stored PIN length on the lock screen (the minimum length elsewhere). */
@Composable
private fun PinDots(
    len: Int,
    total: Int = MIN_PIN,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(20.dp)) {
        repeat(maxOf(total, len)) { i ->
            val filled = i < len
            val size by animateDpAsState(if (filled) 14.dp else 11.dp, label = "dot")
            Box(
                Modifier.size(size).clip(CircleShape).background(
                    if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = .5f),
                ),
            )
        }
    }
}

/** Simple fingerprint-like glyph (concentric arcs) so we don't need the extended icon set. */
@Composable
fun FingerprintGlyph(
    color: Color,
    size: Dp = 32.dp,
) {
    Canvas(Modifier.size(size)) {
        val w = this.size.minDimension
        val stroke = Stroke(width = w * 0.075f, cap = StrokeCap.Round)
        listOf(0.18f to 200f, 0.30f to 230f, 0.42f to 240f, 0.54f to 215f, 0.66f to 190f).forEachIndexed { i, (r, sweep) ->
            val d = w * r * 2 * 0.74f
            drawArc(
                color,
                startAngle = 180f + (360f - sweep) / 2f + (if (i % 2 == 0) 8f else -8f),
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset((w - d) / 2f, (w - d) / 2f - w * 0.02f),
                size = Size(d, d),
                style = stroke,
            )
        }
    }
}

/** One round key of the custom PIN pad. [onLongClick] lets the delete key clear the whole entry. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Key(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Android key codes of the digits the PIN pad accepts, from the top row and the numpad. */
private val PIN_DIGIT_CODES: Map<Int, Char> =
    buildMap {
        for (d in 0..9) {
            put(KeyEvent.KEYCODE_0 + d, '0' + d)
            put(KeyEvent.KEYCODE_NUMPAD_0 + d, '0' + d)
        }
    }

/**
 * 3x4 numeric keypad. [bottomLeft] slot is used for the fingerprint button.
 * A physical keyboard works too: digits add, Backspace/Delete removes the last digit, Enter calls [onSubmit].
 */
@Composable
private fun PinPad(
    pin: String,
    enabled: Boolean,
    onPin: (String) -> Unit,
    bottomLeft: (@Composable () -> Unit)? = null,
    maxLength: Int = MAX_PIN,
    onSubmit: (() -> Unit)? = null,
) {
    /** Appends a digit unless the entry already has [maxLength] digits. */
    fun add(d: Char) {
        if (pin.length < maxLength) onPin(pin + d)
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(enabled) { if (enabled) focus.requestFocus() }
    Column(
        Modifier
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val code = event.nativeKeyEvent.keyCode
                val digit = PIN_DIGIT_CODES[code]
                when {
                    digit != null -> {
                        add(digit)
                        true
                    }
                    code == KeyEvent.KEYCODE_DEL || code == KeyEvent.KEYCODE_FORWARD_DEL -> {
                        if (pin.isNotEmpty()) onPin(pin.dropLast(1))
                        true
                    }
                    code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        onSubmit?.invoke()
                        true
                    }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { d -> Key(enabled = enabled, onClick = { add(d) }) { Text(d.toString(), style = MaterialTheme.typography.headlineSmall) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (bottomLeft != null) Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) { bottomLeft() } else Spacer(Modifier.size(76.dp))
            Key(enabled = enabled, onClick = { add('0') }) { Text("0", style = MaterialTheme.typography.headlineSmall) }
            Key(enabled = enabled && pin.isNotEmpty(), onClick = { onPin(pin.dropLast(1)) }, onLongClick = { onPin("") }) {
                Icon(Icons.Filled.Clear, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Setup

/** Steps of first-run setup: loss warning, choose a PIN, confirm it. */
private enum class SetupStep { INTRO, PIN, CONFIRM }

/**
 * First-run flow: loss warning, PIN choice (weak PINs refused) and confirmation.
 * Creates the vault with [KeyHierarchy.setUp] and passes the recovery words to [onDone].
 */
@Composable
fun SetupScreen(
    keys: KeyHierarchy,
    bip39: Bip39,
    onDone: (List<String>) -> Unit,
) {
    var step by remember { mutableStateOf(SetupStep.INTRO) }
    var ack by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    when (step) {
        SetupStep.INTRO ->
            Page {
                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Badge(96.dp) }
                Text(
                    "Create your vault",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Photos are encrypted with AES-256 and never leave this phone. There is no cloud, no account and no backup.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            "If you forget your PIN and lose your recovery phrase, your photos are permanently unrecoverable.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { ack = !ack }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(ack, { ack = it })
                    Text("I understand")
                }
                Button(enabled = ack, onClick = { step = SetupStep.PIN }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Get started") }
            }

        SetupStep.PIN, SetupStep.CONFIRM -> {
            val confirming = step == SetupStep.CONFIRM
            val cur = if (confirming) confirm else pin

            /** Checks the confirmation against the first PIN, then creates the vault. Runs from the button or as soon as the length matches. */
            fun createVault() {
                if (confirm != pin) {
                    error = "PINs don't match"
                    confirm = ""
                    pin = ""
                    step = SetupStep.PIN
                    return
                }
                busy = true
                scope.launch {
                    val words =
                        withContext(Dispatchers.Default) {
                            val chars = pin.toCharArray()
                            try {
                                keys.setUp(chars, bip39).also { VaultApp.sessionKey = keys.unlockWithPin(chars) }
                            } finally {
                                chars.fill('0')
                            }
                        }
                    onDone(words)
                }
            }

            /** The Next button and Enter: checks the first PIN, or creates the vault from the confirmation. */
            fun nextStep() {
                if (!confirming) {
                    if (PinPolicy.isWeak(pin)) {
                        error = "That PIN is too easy to guess"
                        pin = ""
                    } else {
                        step = SetupStep.CONFIRM
                    }
                } else {
                    createVault()
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Badge()
                Text(if (confirming) "Confirm your PIN" else "Choose a PIN", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("$MIN_PIN–$MAX_PIN digits", color = MaterialTheme.colorScheme.onSurfaceVariant)
                PinDots(cur.length)
                Text(error ?: " ", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                PinPad(
                    cur,
                    !busy,
                    onPin = { v ->
                        error = null
                        if (!confirming) {
                            pin = v
                        } else {
                            confirm = v
                            // The confirmation stops at the length of the first PIN and verifies by itself; no button press needed.
                            if (v.length == pin.length) createVault()
                        }
                    },
                    maxLength = if (confirming) pin.length else MAX_PIN,
                    onSubmit = { if (!busy && cur.length >= MIN_PIN) nextStep() },
                )
                Button(
                    enabled = !busy && cur.length >= MIN_PIN,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    onClick = { nextStep() },
                ) {
                    Text(
                        if (busy) {
                            "Securing…"
                        } else if (confirming) {
                            "Create vault"
                        } else {
                            "Next"
                        },
                    )
                }
            }
        }
    }
}

/** Recovery words as a numbered text file, one word per line. */
internal fun recoveryPhraseText(words: List<String>): String = words.withIndex().joinToString("\n") { (i, w) -> "${i + 1}. $w" } + "\n"

/** Shows the 24 recovery words once and requires the user to confirm they were written down. The words are never stored. */
@Composable
fun PhraseScreen(
    words: List<String>,
    onDone: () -> Unit,
) {
    var saved by remember { mutableStateOf(false) }
    var fileStatus by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val saveFile =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) {
                val text = recoveryPhraseText(words).toByteArray()
                val written = runCatching { checkNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(text) } }
                if (written.isSuccess) AppSettings(context.applicationContext).putRecoveryChecked(System.currentTimeMillis())
                fileStatus = if (written.isSuccess) "Saved. Anyone with this file can open your vault." else "Could not save the file."
            }
        }
    Page {
        Text("Recovery phrase", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Write these 24 words on paper and keep them somewhere safe. They are shown ONCE and never stored. Anyone with them can open your vault.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                words.chunked(2).forEachIndexed { i, pair ->
                    Row(Modifier.fillMaxWidth()) {
                        pair.forEachIndexed { j, w ->
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${i * 2 + j + 1}.", color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
                                Text(w, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().clickable { saved = !saved }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(saved, { saved = it })
            Text("I wrote it down")
        }
        OutlinedButton(onClick = { saveFile.launch("evault-recovery-phrase.txt") }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Save to file")
        }
        Text(
            "Saving writes the words as plain text. Keep the file offline, not in a cloud-synced folder.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        fileStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Button(enabled = saved, onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Open vault") }
    }
}

// ---------------------------------------------------------------------------------------------
// Lock

/**
 * PIN pad shown while the vault is locked. Submits itself at the stored PIN length.
 * Handles the real PIN, the duress PIN (silent wipe, see PanicWipe), biometrics, lockout and the recovery link.
 */
@Composable
fun LockScreen(
    keys: KeyHierarchy,
    bip39: Bip39,
    onRecover: () -> Unit,
    onUnlocked: () -> Unit,
) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var remaining by remember { mutableLongStateOf(keys.attempts.lockedForMs()) }
    val scope = rememberCoroutineScope()
    val bio = remember { keys.biometricEnabled() && Biometric.available(ctx) }
    val pinLength = remember { keys.pinLength() } // 0 for vaults created before the length was stored
    val haptics = LocalHapticFeedback.current
    val unlockProgress = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    val breath by rememberInfiniteTransition(label = "lock-breath").animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(BREATH_MS), RepeatMode.Reverse),
        label = "breath",
    )

    LaunchedEffect(remaining > 0) {
        while (remaining > 0) {
            delay(500)
            remaining = keys.attempts.lockedForMs()
        }
    }

    /** Completes an unlock: clears the failure counter and publishes the session key, which switches the app to the unlocked UI. */
    fun finish(key: javax.crypto.SecretKey) {
        keys.attempts.resetFailures()
        // Success: a short haptic and the lock card swells and fades before the library appears.
        scope.launch {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            unlockProgress.animateTo(1f, tween(durationMillis = UNLOCK_MS))
            VaultApp.sessionKey = key
            onUnlocked()
        }
    }

    /**
     * Checks [entered] on a background thread. A correct PIN unlocks; the duress PIN silently wipes the vault and opens a
     * fresh empty one (see [PanicWipe]); anything else counts as a failed attempt. Both paths cost one PBKDF2 run so timing does not reveal which PIN was used.
     */
    fun submit(entered: String) {
        if (busy) return
        busy = true
        scope.launch {
            val chars = entered.toCharArray()
            val key =
                withContext(Dispatchers.Default) {
                    keys.unlockWithPin(chars) ?: if (keys.isDuressPin(chars)) PanicWipe.execute(ctx, keys, chars, bip39) else null
                }
            chars.fill('0')
            busy = false
            if (key != null) {
                finish(key)
            } else {
                keys.attempts.recordFailure()
                remaining = keys.attempts.lockedForMs()
                pin = ""
                scope.launch { shake.shakeOnce() }
                val left = UnlockAttempts.FREE_TRIES - keys.attempts.failCount()
                error = if (left > 0) "Wrong PIN ($left tries before delay)" else "Wrong PIN"
            }
        }
    }

    // Auto-unlock: submit as soon as the stored number of digits has been entered (iOS style).
    LaunchedEffect(pin) { if (PinPolicy.isComplete(pin, pinLength) && remaining == 0L) submit(pin) }

    /** Starts the biometric prompt bound to the Keystore cipher; reports a changed fingerprint set so the user falls back to the PIN. */
    fun tryBiometric() {
        val cipher =
            keys.biometricUnlockCipher() ?: run {
                error = "Fingerprint changed — use your PIN"
                return
            }
        val act = ctx.findActivity() ?: return
        Biometric.prompt(
            act,
            "Unlock Vault",
            "Touch the fingerprint sensor",
            cipher,
            onSuccess = { c -> keys.unlockWithBiometric(c)?.let(::finish) ?: run { error = "Biometric unlock failed" } },
            onFail = { m -> if (m != null) error = m },
        )
    }
    // Prompt automatically on arrival, like other apps.
    LaunchedEffect(Unit) { if (bio) tryBiometric() }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .offset(x = shake.value.dp)
            .graphicsLayer {
                val p = unlockProgress.value
                alpha = 1f - p
                scaleX = 1f + UNLOCK_SWELL * p
                scaleY = scaleX
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        // Slow breathing while locked, so the screen reads as alive; stops once unlocking starts.
        Box(
            Modifier.graphicsLayer {
                scaleX = breath
                scaleY = breath
            },
        ) { Badge() }
        Text("Vault locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        PinDots(pin.length, if (pinLength > 0) pinLength else MIN_PIN)
        val secs = (remaining + 999) / 1000
        Text(
            when {
                remaining > 0 -> "Too many attempts. Try again in ${secs}s"
                error != null -> error!!
                else -> " "
            },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        PinPad(
            pin,
            enabled = !busy && remaining == 0L,
            onPin = {
                error = null
                pin = it
            },
            bottomLeft =
                if (bio) {
                    (
                        {
                            IconButton(
                                onClick = ::tryBiometric,
                                modifier =
                                    Modifier.semantics {
                                        contentDescription = "Unlock with fingerprint"
                                    },
                            ) { FingerprintGlyph(MaterialTheme.colorScheme.primary, 36.dp) }
                        }
                    )
                } else {
                    null
                },
            maxLength = if (pinLength > 0) pinLength else MAX_PIN,
        )
        // Legacy vaults without a stored length keep the explicit Unlock button.
        if (pinLength == 0) {
            Button(
                enabled = !busy && remaining == 0L && pin.length >= MIN_PIN,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                onClick = { submit(pin) },
            ) { Text(if (busy) "Checking…" else "Unlock") }
        } else {
            Text(if (busy) "Checking…" else " ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onRecover) { Text("Forgot PIN? Use recovery phrase") }
    }
}

/** Recovery flow: the 24 words plus a new PIN re-derive the master key and wrap it again. */
@Composable
fun RecoverScreen(
    keys: KeyHierarchy,
    bip39: Bip39,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Page {
        Text("Recover access", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Enter your 24 recovery words, then choose a new PIN.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(text, {
            text = it
            error = null
        }, label = { Text("24 words") }, minLines = 4, modifier = Modifier.fillMaxWidth())
        PinField("New PIN (min $MIN_PIN digits)", pin) { pin = it }
        PinField("Confirm new PIN", confirm) { confirm = it }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = !busy && PinPolicy.isValid(pin) && pin == confirm,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            onClick = {
                if (PinPolicy.isWeak(pin)) {
                    error = "That PIN is too easy to guess"
                    return@Button
                }
                busy = true
                scope.launch {
                    val words = text.trim().lowercase().split(Regex("\\s+"))
                    val key = withContext(Dispatchers.Default) { keys.recover(words, pin.toCharArray(), bip39) }
                    busy = false
                    if (key != null) {
                        keys.attempts.resetFailures()
                        VaultApp.sessionKey = key
                        onDone()
                    } else {
                        error = "That phrase isn't valid"
                    }
                }
            },
        ) { Text(if (busy) "Recovering…" else "Recover") }
        TextButton(onClick = onBack) { Text("Back") }
    }
}
