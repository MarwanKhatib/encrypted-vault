package com.example.encryptedvault.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.encryptedvault.backup.VaultBackup
import com.example.encryptedvault.crypto.BackupCrypto
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

/** Export and restore rows for Settings (ADR-007). [onMessage] shows the outcome to the user. */
@Composable
internal fun BackupRows(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExport by remember { mutableStateOf<Uri?>(null) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { pendingExport = it }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pendingRestore = it }

    ActionRow(
        "Export encrypted backup",
        "Writes every photo and video, with albums and captions, to a file you choose.",
        "Export",
    ) { createFile.launch("evault-backup-${LocalDate.now()}.evbk") }
    HorizontalDivider()
    ActionRow(
        "Restore from backup",
        "Adds the items from a backup file to this vault. Items already here are skipped.",
        "Restore",
    ) { openFile.launch(arrayOf("*/*")) }

    pendingExport?.let { uri ->
        PassphraseDialog(
            title = "Set a backup passphrase",
            creating = true,
            onDismiss = { pendingExport = null },
        ) { passphrase ->
            pendingExport = null
            scope.launch { onMessage(exportTo(context, uri, passphrase)) }
        }
    }
    pendingRestore?.let { uri ->
        PassphraseDialog(
            title = "Enter the backup passphrase",
            creating = false,
            onDismiss = { pendingRestore = null },
        ) { passphrase ->
            pendingRestore = null
            scope.launch { onMessage(restoreFrom(context, uri, passphrase)) }
        }
    }
}

/** Writes the backup and returns the message to show. The passphrase is wiped afterwards. */
private suspend fun exportTo(
    context: Context,
    uri: Uri,
    passphrase: CharArray,
): String {
    try {
        val out = context.contentResolver.openOutputStream(uri) ?: return "Couldn't open the file for writing."
        return "Exported ${VaultBackup.export(context, passphrase, out)} item(s)."
    } catch (e: IOException) {
        return "Export failed: ${e.message ?: "the file could not be written"}."
    } catch (e: IllegalStateException) {
        return "Unlock the vault before exporting."
    } finally {
        passphrase.fill('\u0000')
    }
}

/** Restores the backup and returns the message to show. The passphrase is wiped afterwards. */
private suspend fun restoreFrom(
    context: Context,
    uri: Uri,
    passphrase: CharArray,
): String {
    try {
        val input = context.contentResolver.openInputStream(uri) ?: return "Couldn't open the file."
        val report = input.use { VaultBackup.restore(context, passphrase, it) }
        return "Restored ${report.imported}, skipped ${report.skipped} already here, failed ${report.failed}."
    } catch (e: IOException) {
        return "Restore failed: wrong passphrase or a damaged file."
    } catch (e: IllegalStateException) {
        return "Unlock the vault before restoring."
    } finally {
        passphrase.fill('\u0000')
    }
}

/** Asks for a passphrase. When [creating], it must be typed twice and meet the minimum length, and the user is warned it cannot be recovered. */
@Composable
private fun PassphraseDialog(
    title: String,
    creating: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (CharArray) -> Unit,
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val tooShort = creating && first.length < BackupCrypto.MIN_PASSPHRASE_LENGTH
    val mismatch = creating && second != first
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (creating) {
                    Text(
                        "Keep this passphrase somewhere safe. The backup cannot be opened without it, and it cannot be recovered.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    value = first,
                    onValueChange = { first = it },
                    label = { Text(if (creating) "Passphrase (at least ${BackupCrypto.MIN_PASSPHRASE_LENGTH} characters)" else "Passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = tooShort && first.isNotEmpty(),
                )
                if (creating) {
                    OutlinedTextField(
                        value = second,
                        onValueChange = { second = it },
                        label = { Text("Repeat passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = mismatch && second.isNotEmpty(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(first.toCharArray()) }, enabled = first.isNotEmpty() && !tooShort && !mismatch) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
