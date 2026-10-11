package com.example.encryptedvault.ui.albums

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.encryptedvault.data.Album
import com.example.encryptedvault.data.VaultItem
import com.example.encryptedvault.data.VaultRepository
import com.example.encryptedvault.ui.common.BarAction
import com.example.encryptedvault.ui.common.EmptyState
import com.example.encryptedvault.ui.common.Formatters
import com.example.encryptedvault.ui.common.ItemActionDialogs
import com.example.encryptedvault.ui.common.MediaGrid
import com.example.encryptedvault.ui.common.SelectionToolbar
import com.example.encryptedvault.ui.common.SelectionTopBar
import com.example.encryptedvault.ui.common.TextInputDialog
import com.example.encryptedvault.ui.common.rememberItemActions
import com.example.encryptedvault.ui.settings.AppSettings
import kotlinx.coroutines.launch

/**
 * Grid for one [CollectionSource] (a media type, folder, user album, person, Hidden or Recently Deleted),
 * with the actions that make sense for that source.
 */
@Composable
fun CollectionScreen(
    source: CollectionSource,
    allItems: List<VaultItem>,
    albums: List<Album>,
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenViewer: (ids: List<Long>, index: Int, includeTrashed: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { VaultRepository.get(context) }
    val snackbar = remember { SnackbarHostState() }
    val actions = rememberItemActions(settings, snackbar, albums)

    val items = remember(source, allItems, albums, settings.newestAtBottom) {
        Collections.items(source, allItems, albums).let { if (settings.newestAtBottom) it.reversed() else it }
    }
    val title = Collections.title(source, albums)
    val inTrash = source == CollectionSource.RecentlyDeleted
    val inHidden = source == CollectionSource.Hidden
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmRemoveCollection by remember { mutableStateOf(false) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    val selectedItems = items.filter { it.id in selectedIds }
    val hasSelection = selectedIds.isNotEmpty()

    /** Leaves multi-select mode and clears the selection. */
    fun endSelection() { selectionMode = false; selectedIds = emptySet() }
    /** Adds [item] to the selection, or removes it when already selected. */
    fun toggle(item: VaultItem) { selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id }
    BackHandler(selectionMode) { endSelection() }
    ItemActionDialogs(actions) { endSelection() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (selectionMode) {
                    SelectionTopBar(selectedIds.size, "Item", onSelectAll = { selectedIds = items.map { it.id }.toSet() }, onDone = ::endSelection)
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary) }
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(Formatters.itemCount(items.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (items.isNotEmpty()) TextButton(onClick = { selectionMode = true }) { Text("Select", fontWeight = FontWeight.SemiBold) }
                        if (inTrash || source is CollectionSource.UserAlbum) Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                            DropdownMenu(menuOpen, { menuOpen = false }) {
                                if (inTrash) {
                                    DropdownMenuItem(text = { Text("Recover all") }, onClick = { menuOpen = false; actions.restoreFromTrash(items) })
                                    DropdownMenuItem(text = { Text("Delete all permanently") }, onClick = { menuOpen = false; confirmEmptyTrash = true })
                                } else {
                                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renaming = true })
                                    DropdownMenuItem(
                                        text = { Text("Delete album") },
                                        onClick = { menuOpen = false; confirmRemoveCollection = true },
                                    )
                                }
                            }
                        }
                    }
                    if (inTrash) Text(
                        "Items are deleted permanently after ${VaultRepository.TRASH_RETENTION_DAYS} days.",
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (items.isEmpty()) EmptyState(Icons.Filled.Share, "No Items", if (inTrash) "Deleted items appear here for ${VaultRepository.TRASH_RETENTION_DAYS} days." else "Nothing in this collection yet.")
                else MediaGrid(
                    items, settings.columns, selectedIds, selectionMode,
                    onClick = { item ->
                        if (selectionMode) toggle(item)
                        else { val ids = items.map { it.id }; onOpenViewer(ids, ids.indexOf(item.id), inTrash || inHidden) }
                    },
                    onLongClick = { selectionMode = true; toggle(it) },
                    groupByDay = false,
                    onColumnsChange = settings::putColumns,
                    scrollToEndKey = if (settings.newestAtBottom) source else null,
                )
            }

            if (selectionMode) SelectionToolbar(
                primary = if (inTrash) listOf(
                    BarAction(Icons.Filled.Refresh, "Recover", enabled = hasSelection) { actions.restoreFromTrash(selectedItems); endSelection() },
                    BarAction(Icons.Filled.Delete, "Delete permanently", MaterialTheme.colorScheme.error, hasSelection) { actions.requestDeleteForever(selectedItems) },
                ) else listOf(
                    BarAction(Icons.Filled.Share, "Share", enabled = hasSelection) { actions.share(selectedItems) },
                    BarAction(if (hasSelection && selectedItems.all { it.isFavorite }) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite", enabled = hasSelection) { actions.toggleFavorite(selectedItems) },
                    BarAction(Icons.Filled.Add, "Add to album", enabled = hasSelection) { actions.requestAddToAlbum(selectedItems) },
                    BarAction(Icons.Filled.Delete, "Delete", MaterialTheme.colorScheme.error, hasSelection) { actions.moveToTrash(selectedItems); endSelection() },
                ),
                overflow = buildList {
                    if (!inTrash) {
                        add(
                            if (inHidden) BarAction(Icons.Filled.Lock, "Unhide", enabled = hasSelection) { actions.unhide(selectedItems); endSelection() }
                            else BarAction(Icons.Filled.Lock, "Hide", enabled = hasSelection) { actions.hide(selectedItems); endSelection() }
                        )
                        add(BarAction(Icons.AutoMirrored.Filled.ExitToApp, "Restore to gallery", enabled = hasSelection) { actions.requestRestore(selectedItems) })
                    }
                    if (source is CollectionSource.UserAlbum) {
                        add(BarAction(Icons.Filled.Close, "Remove from album", enabled = hasSelection) {
                            scope.launch { repository.removeFromAlbum(source.id, selectedIds.toList()) }
                            endSelection()
                        })
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (renaming) TextInputDialog("Rename", "Name", initial = title, onDismiss = { renaming = false }, onConfirm = { name ->
        renaming = false
        scope.launch { if (source is CollectionSource.UserAlbum) repository.renameAlbum(source.id, name) }
    })
    if (confirmRemoveCollection) AlertDialog(
        onDismissRequest = { confirmRemoveCollection = false },
        title = { Text("Delete album?") },
        text = { Text("Items stay in your library.") },
        confirmButton = {
            TextButton(onClick = {
                confirmRemoveCollection = false
                scope.launch {
                    if (source is CollectionSource.UserAlbum) repository.deleteAlbum(source.id)
                    onBack()
                }
            }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmRemoveCollection = false }) { Text("Cancel") } },
    )
    if (confirmEmptyTrash) AlertDialog(
        onDismissRequest = { confirmEmptyTrash = false },
        title = { Text("Delete all permanently?") },
        text = { Text("${Formatters.itemCount(items.size)} will be removed for good.") },
        confirmButton = { TextButton(onClick = { confirmEmptyTrash = false; actions.requestDeleteForever(items) }) { Text("Continue", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmEmptyTrash = false }) { Text("Cancel") } },
    )
}
