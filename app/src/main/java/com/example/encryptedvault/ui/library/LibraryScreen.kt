package com.example.encryptedvault.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.encryptedvault.data.Album
import com.example.encryptedvault.data.VaultItem
import com.example.encryptedvault.ui.common.BarAction
import com.example.encryptedvault.ui.common.CoverCard
import com.example.encryptedvault.ui.common.EmptyState
import com.example.encryptedvault.ui.common.Formatters
import com.example.encryptedvault.ui.common.ItemActionDialogs
import com.example.encryptedvault.ui.common.MediaGrid
import com.example.encryptedvault.ui.common.OverflowMenuHeading
import com.example.encryptedvault.ui.common.OverflowMenuItem
import com.example.encryptedvault.ui.common.SegmentedPills
import com.example.encryptedvault.ui.common.SelectionToolbar
import com.example.encryptedvault.ui.common.SelectionTopBar
import com.example.encryptedvault.ui.common.rememberItemActions
import com.example.encryptedvault.ui.settings.AppSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Which media types the Library shows (the "Filter" section of the ⋯ menu). */
private enum class LibraryFilter(
    val label: String,
    val icon: ImageVector,
) {
    ALL("All items", Icons.Filled.PhotoLibrary),
    FAVORITES("Favorites", Icons.Filled.Favorite),
    PHOTOS("Photos", Icons.Filled.Image),
    VIDEOS("Videos", Icons.Filled.Videocam),
}

/**
 * The Library tab, modelled on iOS Photos: Years / Months / Days / All, newest at the bottom,
 * a title that follows the scroll position, Select mode, and a ⋯ menu for filters and settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    allItems: List<VaultItem>,
    albums: List<Album>,
    settings: AppSettings,
    snackbar: SnackbarHostState,
    onOpenViewer: (ids: List<Long>, index: Int) -> Unit,
    onOpenSettings: () -> Unit,
    onLock: () -> Unit,
    onSelectionModeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scope by remember { mutableStateOf(TimelineScope.ALL) }
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var rangePreset by remember { mutableStateOf(RangePreset.ANY) }
    var customRange by remember { mutableStateOf<TimeRange?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var rangeMenuOpen by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }
    var focusYear by remember { mutableStateOf<Int?>(null) }
    var focusEvent by remember { mutableStateOf<TimelineEvent?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var busyMessage by remember { mutableStateOf<String?>(null) }
    var visibleDay by remember { mutableStateOf<LocalDate?>(null) }

    val actions = rememberItemActions(settings, snackbar, albums)
    val startImport = rememberImportLauncher(settings, snackbar) { busyMessage = it }

    val activeRange = if (rangePreset == RangePreset.CUSTOM) customRange else Timeline.rangeFor(rangePreset)
    val newestAtBottom = settings.newestAtBottom
    val visible =
        remember(allItems, filter, activeRange, newestAtBottom) {
            val library = allItems.filter { it.isInLibrary }
            val byType =
                when (filter) {
                    LibraryFilter.ALL -> library
                    LibraryFilter.FAVORITES -> library.filter { it.isFavorite }
                    LibraryFilter.PHOTOS -> library.filter { !it.isVideo }
                    LibraryFilter.VIDEOS -> library.filter { it.isVideo }
                }
            Timeline.filter(byType, activeRange).let { if (newestAtBottom) it.reversed() else it }
        }
    val selectedItems = visible.filter { it.id in selectedIds }

    /** Leaves multi-select mode and clears the selection. */
    fun endSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }
    LaunchedEffect(selectionMode) { onSelectionModeChanged(selectionMode) }
    BackHandler(selectionMode) { endSelection() }
    BackHandler(!selectionMode && (focusYear != null || focusEvent != null)) {
        focusYear = null
        focusEvent = null
    }
    ItemActionDialogs(actions) { endSelection() }

    /** Adds [item] to the selection, or removes it when already selected. */
    fun toggle(item: VaultItem) {
        selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
    }

    /** Opens the viewer over [list], positioned on [item]. */
    fun open(
        item: VaultItem,
        list: List<VaultItem>,
    ) {
        val ids = list.map { it.id }
        onOpenViewer(ids, ids.indexOf(item.id))
    }

    val gridItems = focusEvent?.let { event -> visible.filter { it in event.items } } ?: visible
    val scrollKey = listOf(scope, focusYear, focusEvent, filter, rangePreset, newestAtBottom)
    val title =
        when {
            focusEvent != null -> Formatters.dateRange(focusEvent!!.firstDay, focusEvent!!.lastDay)
            scope == TimelineScope.YEARS -> "Years"
            scope == TimelineScope.MONTHS -> focusYear?.toString() ?: "Months"
            else -> visibleDay?.let { Formatters.monthTitle(java.time.YearMonth.from(it)) } ?: "Library"
        }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (selectionMode) {
                SelectionTopBar(selectedIds.size, "Item", onSelectAll = { selectedIds = gridItems.map { it.id }.toSet() }, onDone = ::endSelection)
            } else {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(mediaCountSummary(visible), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = startImport) { Icon(Icons.Filled.Add, "Import") }
                    TextButton(onClick = { selectionMode = true }) { Text("Select", fontWeight = FontWeight.SemiBold) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(
                            menuOpen,
                            { menuOpen = false },
                            modifier = Modifier.widthIn(min = 240.dp),
                        ) {
                            OverflowMenuHeading("Show")
                            LibraryFilter.entries.forEach { option ->
                                OverflowMenuItem(option.label, option.icon, selected = filter == option) {
                                    menuOpen = false
                                    filter = option
                                }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            OverflowMenuHeading("Time and order")
                            OverflowMenuItem(
                                "Time range",
                                Icons.Filled.DateRange,
                                trailing = rangePreset.label.takeIf { rangePreset != RangePreset.ANY },
                            ) {
                                menuOpen = false
                                rangeMenuOpen = true
                            }
                            OverflowMenuItem(if (newestAtBottom) "Newest at top" else "Newest at bottom", Icons.Filled.SwapVert) {
                                menuOpen = false
                                settings.putNewestAtBottom(!newestAtBottom)
                            }
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            OverflowMenuHeading("Vault")
                            OverflowMenuItem("Settings", Icons.Filled.Settings) {
                                menuOpen = false
                                onOpenSettings()
                            }
                            OverflowMenuItem("Lock now", Icons.Filled.Lock) {
                                menuOpen = false
                                onLock()
                            }
                        }
                        DropdownMenu(
                            rangeMenuOpen,
                            { rangeMenuOpen = false },
                            modifier = Modifier.widthIn(min = 240.dp),
                        ) {
                            OverflowMenuHeading("Time range")
                            RangePreset.entries.forEach { preset ->
                                OverflowMenuItem(preset.label, icon = null, selected = rangePreset == preset) {
                                    rangeMenuOpen = false
                                    if (preset == RangePreset.CUSTOM) showRangePicker = true else rangePreset = preset
                                }
                            }
                        }
                    }
                }
                if (rangePreset != RangePreset.ANY) {
                    InputChip(
                        selected = true,
                        onClick = { rangePreset = RangePreset.ANY },
                        modifier = Modifier.padding(horizontal = 16.dp),
                        label = { Text(if (rangePreset == RangePreset.CUSTOM) "Custom range" else rangePreset.label) },
                        trailingIcon = { Text("✕") },
                    )
                }
            }

            busyMessage?.let {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
            }

            when {
                visible.isEmpty() ->
                    EmptyState(
                        Icons.Filled.Home,
                        if (allItems.none { it.isInLibrary }) "Your vault is empty" else "No Results",
                        if (allItems.none { it.isInLibrary }) "Tap + to import photos and videos and encrypt them." else "No items match the current filter.",
                    )
                scope == TimelineScope.YEARS && focusYear == null ->
                    PeriodCards(
                        Timeline.byYear(visible).map { group ->
                            PeriodCard(group.key.toString(), Formatters.itemCount(group.items.size), group.cover) {
                                focusYear =
                                    group.key
                                ; scope = TimelineScope.MONTHS
                            }
                        },
                        scrollKey,
                        newestAtBottom,
                    )
                scope == TimelineScope.MONTHS && focusEvent == null ->
                    PeriodCards(
                        Timeline.byEvent(visible.filter { focusYear == null || Timeline.monthOf(it).year == focusYear }).map { event ->
                            PeriodCard(Formatters.dateRange(event.firstDay, event.lastDay), Formatters.itemCount(event.items.size), event.cover) {
                                focusEvent =
                                    event
                                ; scope = TimelineScope.DAYS
                            }
                        },
                        scrollKey,
                        newestAtBottom,
                    )
                else ->
                    MediaGrid(
                        gridItems,
                        settings.columns,
                        selectedIds,
                        selectionMode,
                        onClick = { if (selectionMode) toggle(it) else open(it, gridItems) },
                        onLongClick = {
                            selectionMode = true
                            toggle(it)
                        },
                        groupByDay = scope != TimelineScope.ALL,
                        onColumnsChange = settings::putColumns,
                        scrollToEndKey = if (newestAtBottom) scrollKey else null,
                        onVisibleDay = { visibleDay = it },
                        contentPadding = PaddingValues(bottom = 88.dp),
                    )
            }
        }

        if (selectionMode) {
            SelectionToolbar(
                primary =
                    listOf(
                        BarAction(Icons.Filled.Share, "Share", enabled = selectedIds.isNotEmpty()) { actions.share(selectedItems) },
                        BarAction(
                            if (selectedItems.isNotEmpty() &&
                                selectedItems.all { it.isFavorite }
                            ) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Filled.FavoriteBorder
                            },
                            "Favorite",
                            enabled = selectedIds.isNotEmpty(),
                        ) { actions.toggleFavorite(selectedItems) },
                        BarAction(Icons.Filled.Add, "Add to album", enabled = selectedIds.isNotEmpty()) { actions.requestAddToAlbum(selectedItems) },
                        BarAction(Icons.Filled.Delete, "Delete", MaterialTheme.colorScheme.error, selectedIds.isNotEmpty()) {
                            actions.moveToTrash(selectedItems)
                            endSelection()
                        },
                    ),
                overflow =
                    listOf(
                        BarAction(Icons.Filled.Lock, "Hide", enabled = selectedIds.isNotEmpty()) {
                            actions.hide(selectedItems)
                            endSelection()
                        },
                        BarAction(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            "Restore to gallery",
                            enabled = selectedIds.isNotEmpty(),
                        ) { actions.requestRestore(selectedItems) },
                    ),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else if (visible.isNotEmpty()) {
            // Floating segmented control above the tab bar, like iOS 17.
            Surface(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .96f),
                shadowElevation = 6.dp,
            ) {
                SegmentedPills(
                    TimelineScope.entries,
                    scope,
                    { it.label },
                    {
                        scope = it
                        focusYear = null
                        focusEvent = null
                    },
                    Modifier.width(300.dp).padding(2.dp),
                )
            }
        }
    }

    if (showRangePicker) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null && pickerState.selectedEndDateMillis != null,
                    onClick = {
                        // The picker reports UTC midnight of each chosen day.
                        val first = Instant.ofEpochMilli(pickerState.selectedStartDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                        val last = Instant.ofEpochMilli(pickerState.selectedEndDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                        customRange = Timeline.rangeOfDays(first, last)
                        rangePreset = RangePreset.CUSTOM
                        showRangePicker = false
                    },
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showRangePicker = false }) { Text("Cancel") } },
        ) { DateRangePicker(pickerState, Modifier.padding(top = 8.dp), headline = null, showModeToggle = false) }
    }
}

/** `123 Photos, 4 Videos` as shown under the title in iOS Photos. */
private fun mediaCountSummary(items: List<VaultItem>): String {
    val videos = items.count { it.isVideo }
    val photos = items.size - videos
    return listOfNotNull(
        "$photos Photo${if (photos == 1) "" else "s"}",
        if (videos >
            0
        ) {
            "$videos Video${if (videos == 1) "" else "s"}"
        } else {
            null
        },
    ).joinToString(", ")
}

/** A year or event card in the Years / Months views. */
private class PeriodCard(
    val title: String,
    val subtitle: String,
    val cover: VaultItem,
    val onClick: () -> Unit,
)

/** Two-column grid of year or event cards for the Years and Months views. */
@Composable
private fun PeriodCards(
    cards: List<PeriodCard>,
    scrollKey: Any,
    scrollToEnd: Boolean,
) {
    val state = rememberLazyGridState()
    if (scrollToEnd) LaunchedEffect(scrollKey) { if (cards.isNotEmpty()) state.scrollToItem(cards.lastIndex) }
    LazyVerticalGrid(
        GridCells.Fixed(2),
        Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(cards, key = { it.title + it.subtitle }) { card -> CoverCard(card.title, card.subtitle, card.cover, card.onClick, aspect = 1f) }
    }
}
