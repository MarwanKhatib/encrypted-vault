package com.example.encryptedvault.ui.common

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.encryptedvault.data.Album
import com.example.encryptedvault.data.VaultItem
import com.example.encryptedvault.data.VaultRepository
import com.example.encryptedvault.media.MediaTransfer
import com.example.encryptedvault.ui.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Operations that any screen can perform on a set of items (favorite, trash, restore, share, albums),
 * plus the confirmation dialogs they need. Create with [rememberItemActions] and render the dialogs
 * once per screen with [ItemActionDialogs].
 */
@Stable
class ItemActions(
    private val context: Context,
    private val scope: CoroutineScope,
    private val snackbar: SnackbarHostState,
    private val settings: AppSettings,
    private val repository: VaultRepository,
    val albums: List<Album>,
) {
    internal var restoreRequest by mutableStateOf<List<VaultItem>?>(null)
    internal var deleteForeverRequest by mutableStateOf<List<VaultItem>?>(null)
    internal var addToAlbumRequest by mutableStateOf<List<VaultItem>?>(null)

    /** Favourites every item, or un-favourites all of them when they already are all favourites (iOS multi-select behaviour). */
    fun toggleFavorite(items: List<VaultItem>) {
        val makeFavorite = !items.all { it.isFavorite }
        scope.launch { repository.setFavorite(items.map { it.id }, makeFavorite) }
    }

    /** Moves to Recently Deleted with an Undo snackbar. */
    fun moveToTrash(items: List<VaultItem>) {
        val ids = items.map { it.id }
        scope.launch {
            repository.moveToTrash(ids)
            val result = snackbar.showSnackbar("${Formatters.itemCount(ids.size)} moved to Recently Deleted", "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) repository.restoreFromTrash(ids)
        }
    }

    /** Recovers items from Recently Deleted and confirms with a snackbar. */
    fun restoreFromTrash(items: List<VaultItem>) {
        scope.launch {
            repository.restoreFromTrash(items.map { it.id })
            snackbar.showSnackbar("Recovered ${Formatters.itemCount(items.size)}")
        }
    }

    /** Moves items out of the Library into the Hidden album (and back with [unhide]). */
    fun hide(items: List<VaultItem>) {
        val ids = items.map { it.id }
        scope.launch {
            repository.setHidden(ids, true)
            val result = snackbar.showSnackbar("${Formatters.itemCount(ids.size)} hidden", "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) repository.setHidden(ids, false)
        }
    }

    /** Moves items from the Hidden collection back into the Library. */
    fun unhide(items: List<VaultItem>) {
        scope.launch {
            repository.setHidden(items.map { it.id }, false)
            snackbar.showSnackbar("${Formatters.itemCount(items.size)} unhidden")
        }
    }

    /** Decrypts to a temporary copy (location stripped when enabled) and opens the system share sheet. Decryption runs off the main thread. */
    fun share(items: List<VaultItem>) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { MediaTransfer.share(context, items, settings.stripExif) }
            if (!ok) snackbar.showSnackbar("Could not share")
        }
    }

    /** Asks the UI to show the restore-to-gallery confirmation for [items]. */
    fun requestRestore(items: List<VaultItem>) {
        restoreRequest = items
    }

    /** Asks the UI to confirm permanent deletion of [items]. */
    fun requestDeleteForever(items: List<VaultItem>) {
        deleteForeverRequest = items
    }

    /** Asks the UI to show the album picker for [items]. */
    fun requestAddToAlbum(items: List<VaultItem>) {
        addToAlbumRequest = items
    }

    /** Writes decrypted copies back to the gallery and, when [removeFromVault] is set, removes only the items that restored successfully. */
    internal fun performRestore(
        items: List<VaultItem>,
        removeFromVault: Boolean,
    ) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { MediaTransfer.restoreToGallery(context, items) }
            if (removeFromVault) repository.deleteForever(items.filter { it.id in result.restoredIds })
            val message =
                buildString {
                    append("Restored ${result.restoredIds.size} to original folders")
                    if (result.failed > 0) append("; ${result.failed} failed")
                }
            snackbar.showSnackbar(message)
        }
    }

    /** Permanently deletes [items]: rows, album links and encrypted files. Irreversible. */
    internal fun performDeleteForever(items: List<VaultItem>) {
        scope.launch {
            repository.deleteForever(items)
            snackbar.showSnackbar("Deleted ${Formatters.itemCount(items.size)} permanently")
        }
    }

    /** Adds [items] to [albumId], or to a newly created album when [newAlbumName] is set. */
    internal fun performAddToAlbum(
        items: List<VaultItem>,
        albumId: Long,
        newAlbumName: String?,
    ) {
        scope.launch {
            val targetId = if (newAlbumName != null) repository.createAlbum(newAlbumName) else albumId
            repository.addToAlbum(targetId, items.map { it.id })
            snackbar.showSnackbar("Added ${Formatters.itemCount(items.size)} to album")
        }
    }
}

/** Remembers an [ItemActions] for this composition; rebuilt when [albums] changes so the album picker lists the current albums. */
@Composable
fun rememberItemActions(
    settings: AppSettings,
    snackbar: SnackbarHostState,
    albums: List<Album>,
): ItemActions {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return remember(albums) { ItemActions(context, scope, snackbar, settings, VaultRepository.get(context), albums) }
}

/** Renders whichever confirmation dialog [actions] currently has pending. */
@Composable
fun ItemActionDialogs(
    actions: ItemActions,
    onDone: () -> Unit = {},
) {
    actions.restoreRequest?.let { items ->
        var removeFromVault by remember { mutableStateOf(true) }
        val folders = items.mapNotNull { it.sourceFolder }.distinct()
        AlertDialog(
            onDismissRequest = { actions.restoreRequest = null },
            title = { Text("Restore ${Formatters.itemCount(items.size)} to gallery?") },
            text = {
                Column {
                    Text(
                        "Each item returns to the folder it came from" +
                            (if (folders.isNotEmpty()) " (${folders.take(3).joinToString()}${if (folders.size > 3) "…" else ""})" else "") +
                            ". Missing folders are created. The restored copies are NOT encrypted.",
                    )
                    Row(
                        Modifier.fillMaxWidth().clickable { removeFromVault = !removeFromVault }.padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(removeFromVault, { removeFromVault = it })
                        Text("Remove from vault after restoring")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    actions.restoreRequest = null
                    actions.performRestore(items, removeFromVault)
                    onDone()
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { actions.restoreRequest = null }) { Text("Cancel") } },
        )
    }

    actions.deleteForeverRequest?.let { items ->
        AlertDialog(
            onDismissRequest = { actions.deleteForeverRequest = null },
            title = { Text("Delete ${Formatters.itemCount(items.size)} permanently?") },
            text = { Text("The encrypted files are removed for good. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    actions.deleteForeverRequest = null
                    actions.performDeleteForever(items)
                    onDone()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { actions.deleteForeverRequest = null }) { Text("Cancel") } },
        )
    }

    actions.addToAlbumRequest?.let { items ->
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { actions.addToAlbumRequest = null },
            title = { Text("Add to album") },
            text = {
                Column {
                    actions.albums.forEach { album ->
                        Text(
                            album.name,
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    actions.addToAlbumRequest = null
                                    actions.performAddToAlbum(items, album.id, null)
                                    onDone()
                                }.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    OutlinedTextField(
                        newName,
                        { newName = it },
                        label = { Text("New album") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = newName.isNotBlank(), onClick = {
                    actions.addToAlbumRequest = null
                    actions.performAddToAlbum(items, -1, newName)
                    onDone()
                }) { Text("Create & add") }
            },
            dismissButton = { TextButton(onClick = { actions.addToAlbumRequest = null }) { Text("Cancel") } },
        )
    }
}
