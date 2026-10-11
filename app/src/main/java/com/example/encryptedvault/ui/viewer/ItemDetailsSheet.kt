package com.example.encryptedvault.ui.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.encryptedvault.data.VaultItem
import com.example.encryptedvault.media.RestoreTargets
import com.example.encryptedvault.ui.common.Formatters
import java.text.DateFormat
import java.util.Date

/** Bottom sheet with everything the vault knows about one item, including where it will be restored to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailsSheet(
    item: VaultItem,
    onSaveCaption: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var caption by remember(item.id) { mutableStateOf(item.caption) }
    val restoreTarget = RestoreTargets.resolve(item.isVideo, item.sourceFolder)

    ModalBottomSheet(onDismissRequest = { if (caption != item.caption) onSaveCaption(caption); onDismiss() }) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                caption, { caption = it }, placeholder = { Text("Add a Caption") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onSaveCaption(caption) }),
            )
            Text(item.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            DetailRow("Taken", DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.takenAt)))
            DetailRow("Type", item.mimeType + if (item.isVideo && item.durationMs > 0) "  ·  ${Formatters.duration(item.durationMs)}" else "")
            if (item.width > 0) DetailRow("Dimensions", "${item.width} × ${item.height}")
            DetailRow("Size", Formatters.fileSize(item.sizeBytes))
            DetailRow("Came from", item.sourceFolder ?: "Unknown folder")
            DetailRow("Restores to", restoreTarget.relativePath)
            DetailRow("Encrypted ID", item.hash.take(16) + "…")

        }
    }
}

/** A small label with its value in the details sheet. */
@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
