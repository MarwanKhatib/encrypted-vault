package com.example.encryptedvault.ui.foryou

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.encryptedvault.data.VaultItem
import com.example.encryptedvault.ui.albums.CollectionSource
import com.example.encryptedvault.ui.common.AlbumTile
import com.example.encryptedvault.ui.common.CoverCard
import com.example.encryptedvault.ui.common.EmptyState
import com.example.encryptedvault.ui.common.rememberThumbnail

/** For You tab: On This Day, Featured Photos and Recently Added, computed on-device by [ForYou]. */
@Composable
fun ForYouScreen(
    allItems: List<VaultItem>,
    onOpenViewer: (ids: List<Long>, index: Int) -> Unit,
    onOpenCollection: (CollectionSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val library = remember(allItems) { allItems.filter { it.isInLibrary } }
    val onThisDay = remember(library) { ForYou.onThisDay(library) }
    val featured = remember(library) { ForYou.featured(library) }
    val recent = remember(library) { ForYou.recentlyAdded(library) }

    if (library.isEmpty()) {
        EmptyState(Icons.Filled.Favorite, "Nothing for you yet", "Import photos to see memories and featured photos here.", modifier)
        return
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text("For You", Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }

        if (onThisDay.isNotEmpty()) {
            item { Header("On This Day") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(onThisDay, key = { it.yearsAgo }) { group ->
                        val title = if (group.yearsAgo == 1) "1 year ago" else "${group.yearsAgo} years ago"
                        CoverCard(
                            title, "${group.items.size} item${if (group.items.size == 1) "" else "s"}", group.items.first(),
                            { onOpenCollection(CollectionSource.Fixed(title, group.items.map { it.id })) },
                            Modifier.width(220.dp), aspect = 0.8f,
                        )
                    }
                }
            }
        }

        if (featured.isNotEmpty()) {
            item { Header("Featured Photos") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(featured, key = { it.id }) { item ->
                        val bitmap by rememberThumbnail(item)
                        Box(
                            Modifier.width(260.dp).aspectRatio(0.8f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onOpenViewer(featured.map { it.id }, featured.indexOf(item)) },
                        ) { bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
                    }
                }
            }
        }

        item { Header("Recently Added") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(recent, key = { it.id }) { item ->
                    AlbumTile(item.displayName, null, item, { onOpenViewer(recent.map { it.id }, recent.indexOf(item)) }, size = 120.dp)
                }
            }
        }
    }
}

/** Section title of the For You tab. */
@Composable
private fun Header(title: String) =
    Text(title, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
