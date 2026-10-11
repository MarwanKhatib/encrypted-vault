package com.example.encryptedvault.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.encryptedvault.data.VaultItem
import com.example.encryptedvault.ui.common.EmptyState
import com.example.encryptedvault.ui.common.Formatters
import com.example.encryptedvault.ui.common.MediaGrid
import com.example.encryptedvault.ui.settings.AppSettings
import java.time.Instant
import java.time.ZoneId

/** Search tab: type to filter by name, folder, type, caption or date; suggestions appear while the box is empty. */
@Composable
fun SearchScreen(
    allItems: List<VaultItem>,
    settings: AppSettings,
    onOpenViewer: (ids: List<Long>, index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val live = remember(allItems) { allItems.filter { it.isInLibrary } }
    val results = remember(query, live) { if (query.isBlank()) emptyList() else live.filter { SearchMatcher.matches(it, query) } }

    Column(modifier.fillMaxSize()) {
        Text("Search", Modifier.padding(start = 20.dp, top = 12.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            query, { query = it }, singleLine = true, placeholder = { Text("Names, folders, dates, videos…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when {
            query.isBlank() -> Suggestions(live) { query = it }
            results.isEmpty() -> EmptyState(Icons.Filled.Search, "No results", "Nothing matches “$query”.")
            else -> {
                Text(Formatters.itemCount(results.size), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MediaGrid(
                    results, settings.columns, emptySet(), selectionMode = false,
                    onClick = { item -> val ids = results.map { it.id }; onOpenViewer(ids, ids.indexOf(item.id)) },
                    onLongClick = {},
                )
            }
        }
    }
}

/** Chips shown while the box is empty: categories, folders and years that exist in the library. */
@Composable
private fun Suggestions(items: List<VaultItem>, onPick: (String) -> Unit) {
    val years = items.map { Instant.ofEpochMilli(it.takenAt).atZone(ZoneId.systemDefault()).year }.distinct().sortedDescending().map { it.toString() }
    val folders = items.mapNotNull { it.sourceFolderLabel }.distinct().sorted()
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SuggestionRow("Categories", listOf("video", "photo", "screenshot", "favorite", "edited"), onPick)
        if (folders.isNotEmpty()) SuggestionRow("Folders", folders, onPick)
        if (years.isNotEmpty()) SuggestionRow("Years", years, onPick)
    }
}

/** A titled, horizontally scrolling row of chips; tapping one fills the query. */
@Composable
private fun SuggestionRow(title: String, values: List<String>, onPick: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { AssistChip(onClick = { onPick(it) }, label = { Text(it) }) }
    }
}
