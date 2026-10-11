package com.example.encryptedvault.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.encryptedvault.VaultApp
import com.example.encryptedvault.crypto.VaultFiles
import com.example.encryptedvault.data.VaultItem
import com.example.encryptedvault.data.VaultStorage
import com.example.encryptedvault.media.TrimRange
import com.example.encryptedvault.media.buildVaultPlayer
import com.example.encryptedvault.media.stageTrim
import com.example.encryptedvault.ui.common.Formatters
import com.example.encryptedvault.work.ImportWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Video trimmer: two handles on a preview. Saving exports the selection as a new MP4 next to the original, which is never modified.
 * [onDone] gets a message for the caller's snackbar; the caller closes the screen.
 */
@Composable
fun TrimScreen(item: VaultItem, onDone: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val duration = item.durationMs
    var range by remember { mutableStateOf(TrimRange.whole(duration)) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(item.hash) {
        val ready = withContext(Dispatchers.IO) {
            runCatching { VaultFiles.upgradeToStreaming(checkNotNull(VaultApp.sessionKey), VaultStorage.content(context, item.hash)) }.isSuccess
        }
        if (ready) player = buildVaultPlayer(context, item).apply { prepare() } else failed = true
    }
    DisposableEffect(Unit) { onDispose { player?.release() } }

    fun save() {
        saving = true
        scope.launch {
            val staged = stageTrim(context, item, range)
            if (staged == null) { onDone("Couldn't trim this video."); return@launch }
            val imported = ImportWorker.importAndWait(context, staged)
            onDone(if (imported) "Saved a trimmed copy." else "Saving failed; the original is untouched.")
        }
    }

    Dialog(onDismissRequest = { if (!saving) onDone(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onDone(null) }, enabled = !saving) { Text("Cancel") }
                    Text("Trim", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Button(onClick = ::save, enabled = !range.covers(duration) && !saving && player != null) { Text(if (saving) "Saving…" else "Save") }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    val active = player
                    when {
                        failed -> Text("Couldn't open this video.")
                        active == null -> CircularProgressIndicator()
                        else -> AndroidView(
                            factory = { viewContext -> PlayerView(viewContext).apply { useController = true; this.player = active } },
                            update = { it.player = active },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Start ${Formatters.duration(range.startMs)} · End ${Formatters.duration(range.endMs)} · Length ${Formatters.duration(range.endMs - range.startMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TrimSlider("Trim start", range.startMs, duration, onChange = { range = range.withStart(it) }, onFinished = { player?.seekTo(range.startMs) })
                    TrimSlider("Trim end", range.endMs, duration, onChange = { range = range.withEnd(it, duration) }, onFinished = { player?.seekTo(range.endMs) })
                }
            }
        }
    }
}

/** One trim handle; the label doubles as its spoken name. [onFinished] seeks the preview to where the handle was left. */
@Composable
private fun TrimSlider(label: String, valueMs: Long, durationMs: Long, onChange: (Long) -> Unit, onFinished: () -> Unit) {
    Slider(
        value = valueMs.toFloat(),
        onValueChange = { onChange(it.toLong()) },
        onValueChangeFinished = onFinished,
        valueRange = 0f..durationMs.toFloat(),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
    )
}
