package com.example.encryptedvault.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.encryptedvault.data.VaultItem
import com.example.encryptedvault.ui.library.Timeline
import java.time.LocalDate

/** One cell group of the grid: a date header spanning the row, or a thumbnail. */
private sealed interface GridEntry {
    /** A date header that spans the full row. */
    data class Header(
        val day: LocalDate,
    ) : GridEntry

    /** One thumbnail cell. */
    data class Tile(
        val item: VaultItem,
    ) : GridEntry
}

/**
 * Edge-to-edge square-tile grid in the style of iOS Photos.
 *
 * - [groupByDay] puts a date header before each day.
 * - [selectionMode] shows selection circles; [selectedIds] are the checked ones.
 * - [onColumnsChange] enables pinch-to-zoom between [minColumns] and [maxColumns].
 * - [scrollToEndKey] (when non-null) jumps to the last row whenever the key changes, so the newest items are visible first.
 * - [onVisibleDay] reports the date of the first visible row, for the dynamic title.
 */
@Composable
fun MediaGrid(
    items: List<VaultItem>,
    columns: Int,
    selectedIds: Set<Long>,
    selectionMode: Boolean,
    onClick: (VaultItem) -> Unit,
    onLongClick: (VaultItem) -> Unit,
    modifier: Modifier = Modifier,
    groupByDay: Boolean = false,
    onColumnsChange: ((Int) -> Unit)? = null,
    minColumns: Int = 2,
    maxColumns: Int = 7,
    scrollToEndKey: Any? = null,
    onVisibleDay: ((LocalDate?) -> Unit)? = null,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(bottom = 96.dp),
    onDragSelect: ((List<VaultItem>, Boolean) -> Unit)? = null,
) {
    val entries =
        remember(items, groupByDay) {
            if (groupByDay) {
                Timeline.byDay(items).flatMap { group -> listOf<GridEntry>(GridEntry.Header(group.key)) + group.items.map { GridEntry.Tile(it) } }
            } else {
                items.map { GridEntry.Tile(it) }
            }
        }

    // Bottom-anchored (iOS): when every item fits on screen, a leading spacer pushes them to the bottom edge.
    val density = LocalDensity.current
    val anchorBottom = scrollToEndKey != null
    val shift = if (anchorBottom) 1 else 0
    var fillerPx by remember { mutableIntStateOf(0) }
    if (anchorBottom) {
        LaunchedEffect(entries) {
            snapshotFlow { state.layoutInfo }.collect { info ->
                val visible = info.visibleItemsInfo.filter { it.key != FILLER_KEY }
                fillerPx =
                    if (visible.isNotEmpty() && visible.size == entries.size) {
                        val content = visible.last().offset.y + visible.last().size.height - visible.first().offset.y
                        (info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding - content).coerceAtLeast(0)
                    } else {
                        0
                    }
            }
        }
    }

    if (scrollToEndKey != null) LaunchedEffect(scrollToEndKey) { if (entries.isNotEmpty()) state.scrollToItem(entries.lastIndex + shift) }
    if (onVisibleDay != null) {
        val report by rememberUpdatedState(onVisibleDay)
        LaunchedEffect(entries, state) {
            snapshotFlow { state.firstVisibleItemIndex }.collect { index ->
                val entry = entries.getOrNull((index - shift).coerceAtLeast(0))
                report(
                    when (entry) {
                        is GridEntry.Header -> entry.day
                        is GridEntry.Tile -> Timeline.dayOf(entry.item)
                        null -> null
                    },
                )
            }
        }
    }

    LazyVerticalGrid(
        GridCells.Fixed(columns),
        modifier
            .fillMaxSize()
            .let {
                if (onColumnsChange !=
                    null
                ) {
                    it.pinchToChangeColumns(columns, minColumns, maxColumns, onColumnsChange)
                } else {
                    it
                }
            }.let {
                if (selectionMode && onDragSelect != null) {
                    it.dragToSelect(entries, state, shift, selectedIds, onDragSelect)
                } else {
                    it
                }
            },
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        if (anchorBottom) item(key = FILLER_KEY, span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(with(density) { fillerPx.toDp() })) }
        items(
            entries,
            key = { entry ->
                when (entry) {
                    is GridEntry.Header -> "header-${entry.day}"
                    is GridEntry.Tile -> entry.item.id
                }
            },
            span = { entry -> if (entry is GridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
        ) { entry ->
            when (entry) {
                is GridEntry.Header ->
                    Text(
                        Formatters.dayHeader(entry.day),
                        Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                is GridEntry.Tile -> MediaTile(entry.item, entry.item.id in selectedIds, selectionMode, { onClick(entry.item) }, { onLongClick(entry.item) })
            }
        }
    }
}

private val GAP = 1.dp
private const val FILLER_KEY = "bottom-anchor-filler"

/**
 * Google Photos-style drag selection. In selection mode, press a photo and drag across others: every photo the finger crosses
 * gets the state the first photo would get (selected if it was not). A press that never leaves its photo does nothing here,
 * so a plain tap still toggles that one photo through its own click handler.
 */
private fun Modifier.dragToSelect(
    entries: List<GridEntry>,
    state: LazyGridState,
    shift: Int,
    selectedIds: Set<Long>,
    onSelect: (List<VaultItem>, Boolean) -> Unit,
): Modifier =
    composed {
        val currentEntries by rememberUpdatedState(entries)
        val currentSelected by rememberUpdatedState(selectedIds)
        val currentOnSelect by rememberUpdatedState(onSelect)
        pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val start = entryAt(state, down.position, shift, currentEntries) ?: return@awaitEachGesture
                val first = (currentEntries[start] as? GridEntry.Tile)?.item ?: return@awaitEachGesture
                val wantSelected = first.id !in currentSelected
                var lastEnd = start
                var dragging = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.first()
                    val end = entryAt(state, change.position, shift, currentEntries)
                    if (end != null && end != start) dragging = true
                    if (dragging) {
                        // Taking the events over stops the grid from scrolling and stops the tile's click from firing on release.
                        change.consume()
                        if (end != null && end != lastEnd) {
                            lastEnd = end
                            val range = currentEntries.subList(minOf(start, end), maxOf(start, end) + 1)
                            currentOnSelect(range.filterIsInstance<GridEntry.Tile>().map { it.item }, wantSelected)
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    }

/** Index in [entries] of the tile under [position], or null for headers and empty space. */
private fun entryAt(
    state: LazyGridState,
    position: Offset,
    shift: Int,
    entries: List<GridEntry>,
): Int? {
    val hit =
        state.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            position.x >= info.offset.x &&
                position.x < info.offset.x + info.size.width &&
                position.y >= info.offset.y &&
                position.y < info.offset.y + info.size.height
        } ?: return null
    val index = hit.index - shift
    return index.takeIf { it in entries.indices && entries[it] is GridEntry.Tile }
}

/** Two-finger pinch changes the column count (zoom in = fewer, larger tiles), like iOS. */
private fun Modifier.pinchToChangeColumns(
    columns: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
): Modifier =
    composed {
        val currentColumns by rememberUpdatedState(columns)
        val currentOnChange by rememberUpdatedState(onChange)
        pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var accumulated = 1f
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.size >= 2) {
                        accumulated *= event.calculateZoom()
                        event.changes.forEach { it.consume() }
                        val next =
                            when {
                                accumulated > PINCH_STEP -> (currentColumns - 1).coerceAtLeast(min)
                                accumulated < 1f / PINCH_STEP -> (currentColumns + 1).coerceAtMost(max)
                                else -> currentColumns
                            }
                        if (next != currentColumns) {
                            currentOnChange(next)
                            accumulated = 1f
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    }

private const val PINCH_STEP = 1.3f

/** One square thumbnail: video duration, favorite heart, and the iOS-style selection circle. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaTile(
    item: VaultItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val bitmap by rememberThumbnail(item)
    // The label is formatted once per item, not on every scroll recomposition.
    val label = remember(item) { describe(item) }
    // Tiles animate only when selection changes; idle tiles keep no animation state.
    val scale =
        if (selectionMode) {
            androidx.compose.animation.core
                .animateFloatAsState(
                    if (selected) 0.88f else 1f,
                    androidx.compose.animation.core.spring(
                        androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        androidx.compose.animation.core.Spring.StiffnessMediumLow,
                    ),
                    label = "tile-scale",
                ).value
        } else {
            1f
        }
    Box(
        Modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                if (selectionMode) this.selected = selected
            },
    ) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        if (selected) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .25f)))

        if (item.isFavorite) {
            Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(5.dp).size(14.dp))
        }
        when {
            selectionMode -> SelectionCircle(selected, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            item.isVideo ->
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    if (item.durationMs > 0) Text(Formatters.duration(item.durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
        }
    }
}

/**
 * Spoken label for a tile: type, date, length and favourite. Never the file name or folder, which would leak through TalkBack (Gap 14).
 */
private fun describe(item: VaultItem): String =
    listOfNotNull(
        if (item.isVideo) "Video" else "Photo",
        Formatters.dayHeader(Timeline.dayOf(item)),
        if (item.isVideo && item.durationMs > 0) Formatters.duration(item.durationMs) else null,
        if (item.isFavorite) "Favorite" else null,
    ).joinToString(", ")

/** iOS-style selection indicator: a hollow ring, or a filled check when [selected]. */
@Composable
private fun SelectionCircle(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = .15f))
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
}
