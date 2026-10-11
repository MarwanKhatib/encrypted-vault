package com.example.encryptedvault.ui.viewer

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.alpha
import com.example.encryptedvault.ui.common.Formatters
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.animation.AnimatedContent
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.example.encryptedvault.crypto.VaultFiles
import com.example.encryptedvault.VaultApp
import com.example.encryptedvault.data.VaultStorage
import com.example.encryptedvault.media.VaultDataSource
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.encryptedvault.data.Album
import com.example.encryptedvault.data.VaultItem
import com.example.encryptedvault.data.VaultRepository
import com.example.encryptedvault.media.MediaTransfer
import com.example.encryptedvault.ui.common.ItemActionDialogs
import com.example.encryptedvault.ui.common.rememberItemActions
import com.example.encryptedvault.ui.common.rememberThumbnail
import com.example.encryptedvault.ui.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Full-screen viewer in the style of iOS Photos: date/time title, bottom toolbar (share, favorite, info,
 * delete), a filmstrip, tap to toggle white chrome / black background, swipe down to dismiss.
 * With [includeTrashed] it is browsing Recently Deleted.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(
    allItems: List<VaultItem>,
    ids: List<Long>,
    startIndex: Int,
    includeTrashed: Boolean,
    albums: List<Album>,
    settings: AppSettings,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { VaultRepository.get(context) }
    val snackbar = remember { SnackbarHostState() }
    val actions = rememberItemActions(settings, snackbar, albums)

    val byId = remember(allItems) { allItems.associateBy { it.id } }
    val items = remember(byId, ids, includeTrashed) { ids.mapNotNull { byId[it] }.filter { includeTrashed || it.isInLibrary } }
    if (items.isEmpty()) { LaunchedEffect(Unit) { onClose() }; return }

    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }
    var chromeVisible by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var slideshow by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]
    val showChrome = chromeVisible && !slideshow

    // Swipe down to dismiss: the page follows the finger and fades out.
    val dragY = remember { Animatable(0f) }
    val background by animateColorAsState(if (showChrome) MaterialTheme.colorScheme.surface else Color.Black, label = "viewer-background")

    BackHandler { onClose() }
    ItemActionDialogs(actions)
    LaunchedEffect(slideshow, pager.currentPage) {
        if (!slideshow) return@LaunchedEffect
        delay(SLIDESHOW_INTERVAL_MS)
        pager.animateScrollToPage((pager.currentPage + 1) % items.size)
    }

    Box(
        Modifier.fillMaxSize().background(background.copy(alpha = (1f - abs(dragY.value) / DISMISS_FADE_DISTANCE).coerceIn(0f, 1f)))
            .pointerInput(zoomed) {
                if (zoomed) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = { scope.launch { if (abs(dragY.value) > DISMISS_DISTANCE) onClose() else dragY.animateTo(0f) } },
                    onDragCancel = { scope.launch { dragY.animateTo(0f) } },
                    onVerticalDrag = { change, delta -> change.consume(); scope.launch { dragY.snapTo(dragY.value + delta) } },
                )
            },
    ) {
        HorizontalPager(
            pager, Modifier.fillMaxSize().graphicsLayer { translationY = dragY.value; val s = 1f - abs(dragY.value) / 3000f; scaleX = s; scaleY = s },
            userScrollEnabled = !zoomed, key = { items[it].id },
        ) { page ->
            val item = items[page]
            val isCurrent = pager.currentPage == page
            if (item.isVideo) VideoPage(item, isCurrent) { chromeVisible = !chromeVisible }
            else ImagePage(item, isCurrent, onTap = { chromeVisible = !chromeVisible; slideshow = false }, onZoomChanged = { if (isCurrent) zoomed = it })
        }

        AnimatedVisibility(showChrome, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.primary) }
                val taken = Instant.ofEpochMilli(current.takenAt).atZone(ZoneId.systemDefault())
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Formatters.shortDay(taken.toLocalDate()), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(taken), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More", tint = MaterialTheme.colorScheme.primary) }
                    DropdownMenu(menuOpen, { menuOpen = false }) {
                        if (!current.isTrashed) {
                            DropdownMenuItem(text = { Text("Add to album") }, leadingIcon = { Icon(Icons.Filled.Add, null) }, onClick = { menuOpen = false; actions.requestAddToAlbum(listOf(current)) })
                            DropdownMenuItem(text = { Text("Slideshow") }, leadingIcon = { Icon(Icons.Filled.PlayArrow, null) }, onClick = { menuOpen = false; slideshow = true })
                            DropdownMenuItem(
                                text = { Text(if (current.isHidden) "Unhide" else "Hide") }, leadingIcon = { Icon(Icons.Filled.Lock, null) },
                                onClick = { menuOpen = false; if (current.isHidden) actions.unhide(listOf(current)) else actions.hide(listOf(current)) },
                            )
                            DropdownMenuItem(text = { Text("Restore to gallery") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) }, onClick = { menuOpen = false; actions.requestRestore(listOf(current)) })
                        } else {
                            DropdownMenuItem(text = { Text("Recover") }, leadingIcon = { Icon(Icons.Filled.Refresh, null) }, onClick = { menuOpen = false; actions.restoreFromTrash(listOf(current)) })
                        }
                    }
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            AnimatedVisibility(showChrome, enter = fadeIn(), exit = fadeOut()) {
                Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                    Filmstrip(items, pager.currentPage) { index -> scope.launch { pager.scrollToPage(index) } }
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        if (current.isTrashed) {
                            IconButton(onClick = { actions.restoreFromTrash(listOf(current)) }) { Icon(Icons.Filled.Refresh, "Recover", tint = MaterialTheme.colorScheme.primary) }
                            IconButton(onClick = { showDetails = true }) { Icon(Icons.Filled.Info, "Info", tint = MaterialTheme.colorScheme.primary) }
                            IconButton(onClick = { actions.requestDeleteForever(listOf(current)) }) { Icon(Icons.Filled.Delete, "Delete permanently", tint = MaterialTheme.colorScheme.error) }
                        } else {
                            IconButton(onClick = { actions.share(listOf(current)) }) { Icon(Icons.Outlined.IosShare, "Share", tint = MaterialTheme.colorScheme.primary) }
                            IconButton(onClick = { actions.toggleFavorite(listOf(current)) }) {
                                AnimatedContent(
                                    current.isFavorite, label = "favorite",
                                    transitionSpec = { scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) + fadeIn() togetherWith fadeOut(tween(90)) },
                                ) { fav -> Icon(if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Favorite", tint = MaterialTheme.colorScheme.primary) }
                            }
                            if (!current.isVideo) IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Tune, "Edit", tint = MaterialTheme.colorScheme.primary) }
                            IconButton(onClick = { showDetails = true }) { Icon(Icons.Outlined.Info, "Info", tint = MaterialTheme.colorScheme.primary) }
                            IconButton(onClick = { actions.moveToTrash(listOf(current)) }) { Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
            if (slideshow) Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp), contentAlignment = Alignment.Center) {
                TextButton(onClick = { slideshow = false }) { Text("Stop slideshow", color = Color.White) }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 120.dp))
    }

    if (editing) EditScreen(current) { message -> editing = false; if (message != null) scope.launch { snackbar.showSnackbar(message) } }
    if (showDetails) ItemDetailsSheet(
        current, onSaveCaption = { text -> scope.launch { repository.setCaption(current.id, text) } },
        onDismiss = { showDetails = false },
    )
}

/** Row of small thumbnails under the photo; tap one to jump there. The current one is wider, like iOS. */
@Composable
private fun Filmstrip(items: List<VaultItem>, currentIndex: Int, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) { listState.animateScrollToItem((currentIndex - 3).coerceAtLeast(0)) }
    LazyRow(
        state = listState, modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp), contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            val bitmap by rememberThumbnail(item)
            val selected = index == currentIndex
            Box(
                Modifier.fillMaxHeight().width(if (selected) 36.dp else 28.dp).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onSelect(index) },
            ) { bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
        }
    }
}

private const val DISMISS_DISTANCE = 260f
private const val DISMISS_FADE_DISTANCE = 900f

private const val SLIDESHOW_INTERVAL_MS = 3000L
private const val MAX_ZOOM = 6f
private const val DOUBLE_TAP_ZOOM = 2.5f

/** A zoomable image page. Gestures only consume touches while zoomed so the pager can still swipe at 1x. */
@Composable
private fun ImagePage(item: VaultItem, isCurrent: Boolean, onTap: () -> Unit, onZoomChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val full by produceState<Bitmap?>(null, item.hash) { value = withContext(Dispatchers.IO) { MediaTransfer.loadFullImage(context, item) } }
    val thumbnail by rememberThumbnail(item)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(isCurrent) { if (!isCurrent) { scale = 1f; offset = Offset.Zero } }

    /** Keeps a zoomed image from being panned past its edges for the given scale. */
    fun clampOffset(value: Offset, atScale: Float): Offset {
        val maxX = size.width * (atScale - 1f) / 2f
        val maxY = size.height * (atScale - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier.fillMaxSize().onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = DOUBLE_TAP_ZOOM
                        onZoomChanged(scale > 1f)
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (scale > 1f || event.changes.size > 1) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                            offset = if (scale <= 1f) Offset.Zero else clampOffset(offset + event.calculatePan(), scale)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale <= 1.02f) { scale = 1f; offset = Offset.Zero }
                    onZoomChanged(scale > 1f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        (full ?: thumbnail)?.let {
            Image(
                it.asImageBitmap(), null,
                Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                contentScale = ContentScale.Fit,
            )
        }
        if (full == null) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

/**
 * Video page: the thumbnail with a play button. Tapping it starts ExoPlayer on an encrypted [VaultDataSource], so the video is
 * decrypted segment by segment in memory and no plaintext file is ever created. Videos stored in the legacy format are
 * upgraded in place first (a one-off cost per old video).
 */
@OptIn(UnstableApi::class)
@Composable
private fun VideoPage(item: VaultItem, isCurrent: Boolean, onTap: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val thumbnail by rememberThumbnail(item)
    var player by remember(item.hash) { mutableStateOf<ExoPlayer?>(null) }
    var loading by remember(item.hash) { mutableStateOf(false) }
    var failed by remember(item.hash) { mutableStateOf(false) }
    DisposableEffect(item.hash) { onDispose { player?.release(); player = null } }
    LaunchedEffect(isCurrent) { if (!isCurrent) { player?.release(); player = null } }

    /** Prepares and starts playback: upgrades a legacy file in place if needed, then builds ExoPlayer on the encrypted data source. */
    fun start() {
        loading = true; failed = false
        scope.launch {
            val ready = withContext(Dispatchers.IO) {
                runCatching { VaultFiles.upgradeToStreaming(checkNotNull(VaultApp.sessionKey), VaultStorage.content(context, item.hash)) }.isSuccess
            }
            if (!ready) { failed = true; loading = false; return@launch }
            val source = DataSource.Factory { VaultDataSource({ VaultApp.sessionKey }, { hash -> VaultStorage.content(context, hash) }) }
            player = ExoPlayer.Builder(context).build().apply {
                setMediaSource(ProgressiveMediaSource.Factory(source).createMediaSource(MediaItem.Builder().setUri("vault://${item.hash}").setMimeType(item.mimeType).build()))
                addListener(object : Player.Listener { override fun onPlayerError(error: PlaybackException) { failed = true } })
                prepare()
                playWhenReady = true
            }
            loading = false
        }
    }

    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }, contentAlignment = Alignment.Center) {
        val active = player
        if (active != null && !failed) {
            AndroidView(factory = { viewContext -> PlayerView(viewContext).apply { useController = true; this.player = active } }, update = { it.player = active }, modifier = Modifier.fillMaxSize())
        } else {
            thumbnail?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            if (loading) CircularProgressIndicator(color = Color.White)
            else FilledIconButton(
                onClick = { player?.release(); player = null; start() },
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(.55f)),
            ) { Icon(if (failed) Icons.Filled.Refresh else Icons.Filled.PlayArrow, if (failed) "Retry" else "Play", Modifier.size(40.dp), tint = Color.White) }
            if (failed) Text("Couldn't play this video", Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp), color = Color.White)
        }
    }
}
