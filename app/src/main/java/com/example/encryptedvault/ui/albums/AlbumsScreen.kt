package com.example.encryptedvault.ui.albums

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Panorama
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.encryptedvault.data.Album
import com.example.encryptedvault.data.VaultItem
import com.example.encryptedvault.data.VaultRepository
import com.example.encryptedvault.ui.common.AlbumTile
import com.example.encryptedvault.ui.common.TextInputDialog
import kotlinx.coroutines.launch

/** Albums tab in the iOS layout: My Albums, Original Folders, Media Types, Utilities. */
@Composable
fun AlbumsScreen(
    allItems: List<VaultItem>,
    albums: List<Album>,
    onOpen: (CollectionSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }

    /** Items of [source] for the current library and albums. */
    fun itemsOf(source: CollectionSource) = Collections.items(source, allItems, albums)
    val myAlbums = listOf<CollectionSource>(CollectionSource.Recents, CollectionSource.Favorites) + albums.sortedBy { it.name.lowercase() }.map { CollectionSource.UserAlbum(it.id) }
    val folders = allItems.filter { it.isInLibrary }.mapNotNull { it.sourceFolder }.distinct().sorted().map { CollectionSource.Folder(it) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Albums", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "New album") }
            }
        }

        item { SectionHeader("My Albums") }
        item {
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(myAlbums.chunked(2)) { pair ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { source ->
                            val list = itemsOf(source)
                            AlbumTile(Collections.title(source, albums), list.size, list.firstOrNull(), { onOpen(source) })
                        }
                    }
                }
            }
        }

        if (folders.isNotEmpty()) {
            item { SectionHeader("Original Folders") }
            item {
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(folders) { source ->
                        val list = itemsOf(source)
                        AlbumTile(Collections.title(source, albums), list.size, list.firstOrNull(), { onOpen(source) })
                    }
                }
            }
        }

        item { SectionHeader("Media Types") }
        item {
            val types = listOf(
                Triple(CollectionSource.Videos, Icons.Rounded.Videocam, true),
                Triple(CollectionSource.Screenshots, Icons.Rounded.Screenshot, false),
                Triple(CollectionSource.ScreenRecordings, Icons.Rounded.FiberManualRecord, false),
                Triple(CollectionSource.Panoramas, Icons.Rounded.Panorama, false),
                Triple(CollectionSource.Animated, Icons.Rounded.Animation, false),
            ).map { (source, icon, always) -> Triple(source, icon, itemsOf(source).size) to always }.filter { (row, always) -> always || row.third > 0 }.map { it.first }
            ListCard(types.map { (source, icon, count) -> ListRow(icon, Collections.title(source, albums), count) { onOpen(source) } })
        }

        item { SectionHeader("Utilities") }
        item {
            ListCard(
                listOfNotNull(
                    ListRow(Icons.Rounded.Favorite, "Favorites", itemsOf(CollectionSource.Favorites).size) { onOpen(CollectionSource.Favorites) },
                    itemsOf(CollectionSource.RecentlyEdited).size.takeIf { it > 0 }
                        ?.let { n -> ListRow(Icons.Rounded.Tune, "Recently Edited", n) { onOpen(CollectionSource.RecentlyEdited) } },
                    ListRow(Icons.Rounded.VisibilityOff, "Hidden", itemsOf(CollectionSource.Hidden).size) { onOpen(CollectionSource.Hidden) },
                    ListRow(Icons.Rounded.DeleteOutline, "Recently Deleted", itemsOf(CollectionSource.RecentlyDeleted).size) { onOpen(CollectionSource.RecentlyDeleted) },
                )
            )
        }
    }

    if (creating) TextInputDialog("New Album", "Album name", confirmLabel = "Create", onDismiss = { creating = false }, onConfirm = { name ->
        creating = false
        scope.launch { VaultRepository.get(context).createAlbum(name) }
    })
}

/** Bold section title with an optional text action on the right. */
@Composable
private fun SectionHeader(title: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

/** One row of a grouped list card: icon, title, count. */
private class ListRow(val icon: ImageVector, val title: String, val count: Int, val onClick: () -> Unit)

/** Grouped rounded list (icon, title, count, chevron), like the Media Types / Utilities lists in iOS. */
@Composable
private fun ListCard(rows: List<ListRow>) {
    Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        rows.forEachIndexed { index, row ->
            Row(Modifier.fillMaxWidth().clickable(onClick = row.onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(row.icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(row.title, Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                Text(row.count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (index < rows.lastIndex) HorizontalDivider(Modifier.padding(start = 56.dp))
        }
    }
}
