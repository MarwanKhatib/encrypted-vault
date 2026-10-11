package com.example.encryptedvault.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.encryptedvault.data.VaultItem
import com.example.encryptedvault.media.CropAspect
import com.example.encryptedvault.media.Edit
import com.example.encryptedvault.media.ImageEdits
import com.example.encryptedvault.media.MediaTransfer
import com.example.encryptedvault.media.PhotoFilter
import com.example.encryptedvault.media.SourceInfo
import com.example.encryptedvault.work.ImportWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREVIEW_EDGE = 1280
private const val SAVE_EDGE = 4096

/**
 * Photo editor (rotate, flip, crop presets, filters, brightness/contrast/saturation).
 * Saving encrypts the result as a new item next to the original; the original is never modified.
 * [onDone] gets a message for the caller's snackbar and the editor is expected to be closed by the caller.
 */
@Composable
fun EditScreen(item: VaultItem, onDone: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf(Edit()) }
    var saving by remember { mutableStateOf(false) }
    val base by produceState<Bitmap?>(null, item.id) { value = withContext(Dispatchers.IO) { MediaTransfer.loadFullImage(context, item, PREVIEW_EDGE) } }
    val preview by produceState<Bitmap?>(null, base, edit) { value = base?.let { b -> withContext(Dispatchers.Default) { ImageEdits.apply(b, edit) } } }

    /** Renders the edit at full size, stages it as a JPEG and runs it through the normal import worker, then reports the outcome through [onDone]. */
    fun save() {
        saving = true
        scope.launch {
            val staged = withContext(Dispatchers.IO) {
                MediaTransfer.loadFullImage(context, item, SAVE_EDGE)?.let { full ->
                    MediaTransfer.stageBitmap(context, ImageEdits.apply(full, edit), SourceInfo(ImageEdits.editedName(item.name), item.sourceFolder, item.takenAt))
                }
            }
            if (staged == null) { onDone("Couldn't edit this photo."); return@launch }
            val request = OneTimeWorkRequestBuilder<ImportWorker>().setInputData(ImportWorker.inputFor(listOf(staged))).build()
            val workManager = WorkManager.getInstance(context)
            workManager.enqueue(request)
            val info = workManager.getWorkInfoByIdFlow(request.id).first { it != null && it.state.isFinished }
            onDone(if (info?.state == WorkInfo.State.SUCCEEDED) "Saved an edited copy." else "Saving failed; the original is untouched.")
        }
    }

    Dialog(onDismissRequest = { if (!saving) onDone(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onDone(null) }, enabled = !saving) { Text("Cancel") }
                    Text("Edit", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Button(onClick = ::save, enabled = !edit.isIdentity && !saving && preview != null) { Text(if (saving) "Saving…" else "Save") }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    preview?.let { Image(it.asImageBitmap(), "Preview", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } ?: CircularProgressIndicator()
                }
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
                    Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { edit = edit.rotated(-90) }) { Icon(Icons.Rounded.RotateLeft, "Rotate left") }
                        IconButton(onClick = { edit = edit.rotated(90) }) { Icon(Icons.Rounded.RotateRight, "Rotate right") }
                        FilterChip(edit.flipHorizontal, { edit = edit.copy(flipHorizontal = !edit.flipHorizontal) }, { Text("Flip") }, leadingIcon = { Icon(Icons.Rounded.Flip, null, Modifier.size(18.dp)) })
                        TextButton(onClick = { edit = Edit() }, enabled = !edit.isIdentity) { Text("Reset") }
                    }
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CropAspect.entries) { a -> FilterChip(edit.aspect == a, { edit = edit.copy(aspect = a) }, { Text(a.label) }) }
                    }
                    LazyRow(Modifier.padding(top = 4.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PhotoFilter.entries) { f -> FilterChip(edit.filter == f, { edit = edit.copy(filter = f) }, { Text(f.label) }) }
                    }
                    EditSlider("Brightness", edit.brightness, -0.5f..0.5f) { edit = edit.copy(brightness = it) }
                    EditSlider("Contrast", edit.contrast, 0.5f..1.5f) { edit = edit.copy(contrast = it) }
                    EditSlider("Saturation", edit.saturation, 0f..2f) { edit = edit.copy(saturation = it) }
                }
            }
        }
    }
}

/** Label plus slider for one adjustment. */
@Composable
private fun EditSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.padding(end = 12.dp).weight(0.35f), style = MaterialTheme.typography.bodyMedium)
        Slider(value, onChange, Modifier.weight(0.65f), valueRange = range)
    }
}
