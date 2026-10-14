package com.example.encryptedvault.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.example.encryptedvault.crypto.KeyHierarchy
import com.example.encryptedvault.data.VaultRepository
import com.example.encryptedvault.media.AutoImport
import com.example.encryptedvault.media.ExistingImport
import com.example.encryptedvault.security.SessionManager
import com.example.encryptedvault.ui.albums.AlbumsScreen
import com.example.encryptedvault.ui.albums.CollectionScreen
import com.example.encryptedvault.ui.albums.CollectionSource
import com.example.encryptedvault.ui.foryou.ForYouScreen
import com.example.encryptedvault.ui.library.LibraryScreen
import com.example.encryptedvault.ui.search.SearchScreen
import com.example.encryptedvault.ui.settings.AppSettings
import com.example.encryptedvault.ui.settings.SettingsScreen
import com.example.encryptedvault.ui.viewer.ViewerScreen

/** Bottom-navigation tabs of the unlocked app (the same four as iOS Photos); the filled icon marks the selected tab. */
private enum class MainTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    LIBRARY("Library", Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary),
    FOR_YOU("For You", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    ALBUMS("Albums", Icons.Outlined.PhotoAlbum, Icons.Filled.PhotoAlbum),
    SEARCH("Search", Icons.Outlined.Search, Icons.Filled.Search),
}

/** Screens pushed on top of the tabs. The last entry of the back stack is the one shown. */
private sealed interface Destination {
    /** One collection opened as a grid. */
    data class Collection(
        val source: CollectionSource,
    ) : Destination

    /** The full-screen viewer over [itemIds], starting at [index]. */
    data class Viewer(
        val itemIds: List<Long>,
        val index: Int,
        val includeTrashed: Boolean,
    ) : Destination

    /** The settings screen. */
    data object Settings : Destination
}

/** The unlocked app: four tabs plus a small back stack for collections, the viewer and settings. */
@Composable
fun MainScaffold(
    keys: KeyHierarchy,
    settings: AppSettings,
) {
    val context = LocalContext.current
    val repository = remember { VaultRepository.get(context) }
    val items by repository.items.collectAsState(emptyList())
    val albums by repository.albums.collectAsState(emptyList())

    var tab by remember { mutableStateOf(MainTab.LIBRARY) }
    var selecting by remember { mutableStateOf(false) } // the Library hides the tab bar while selecting, like iOS
    val stack = remember { mutableStateListOf<Destination>() }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { repository.purgeExpiredTrash() }

    // Default behaviour: when the vault opens, scan the gallery and add what the vault lacks (ADR-016). Asks for photo access once.
    val mediaAccess =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { ExistingImport.start(context, force = true) }
    LaunchedEffect(Unit) {
        if (!settings.autoImport) return@LaunchedEffect
        if (AutoImport.granted(context)) ExistingImport.start(context) else mediaAccess.launch(AutoImport.permissions())
    }
    BackHandler(stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

    /** Closes the top pushed screen. */
    fun pop() {
        stack.removeAt(stack.lastIndex)
    }

    /** Pushes the viewer over [ids] at [index]. */
    fun openViewer(
        ids: List<Long>,
        index: Int,
        includeTrashed: Boolean = false,
    ) {
        stack += Destination.Viewer(ids, index, includeTrashed)
    }

    /** Pushes a collection grid. */
    fun openCollection(source: CollectionSource) {
        stack += Destination.Collection(source)
    }

    // Material motion: pushed screens use a shared-axis slide + fade, tabs use fade-through.
    AnimatedContent(
        targetState = stack.lastOrNull(),
        transitionSpec = {
            val forward = targetState != null
            (fadeIn(tween(220, delayMillis = 90)) + slideInHorizontally(tween(300)) { if (forward) it / 12 else -it / 12 }) togetherWith fadeOut(tween(90))
        },
        label = "destination",
    ) { top ->
        when (top) {
            is Destination.Collection ->
                CollectionScreen(top.source, items, albums, settings, onBack = ::pop, onOpenViewer = {
                        ids,
                        index,
                        trashed,
                    ->
                    openViewer(ids, index, trashed)
                })
            is Destination.Viewer -> ViewerScreen(items, top.itemIds, top.index, top.includeTrashed, albums, settings, onClose = ::pop)
            Destination.Settings -> SettingsScreen(settings, keys, onBack = ::pop, onLockNow = { SessionManager.forceLock() })
            null ->
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        if (!(tab == MainTab.LIBRARY && selecting)) {
                            NavigationBar {
                                MainTab.entries.forEach { entry ->
                                    NavigationBarItem(tab == entry, { tab = entry }, icon = {
                                        Icon(
                                            if (tab ==
                                                entry
                                            ) {
                                                entry.selectedIcon
                                            } else {
                                                entry.icon
                                            },
                                            entry.label,
                                        )
                                    }, label = { Text(entry.label) })
                                }
                            }
                        }
                    },
                ) { padding ->
                    AnimatedContent(
                        tab,
                        Modifier.padding(padding),
                        transitionSpec = {
                            (fadeIn(tween(210, delayMillis = 90)) + scaleIn(tween(210, delayMillis = 90), initialScale = 0.92f)) togetherWith
                                fadeOut(tween(90))
                        },
                        label = "tab",
                    ) { current ->
                        when (current) {
                            MainTab.LIBRARY ->
                                LibraryScreen(
                                    items,
                                    albums,
                                    settings,
                                    snackbar,
                                    onOpenViewer = { ids, index -> openViewer(ids, index) },
                                    onOpenSettings = { stack += Destination.Settings },
                                    onLock = { SessionManager.forceLock() },
                                    onSelectionModeChanged = { selecting = it },
                                )
                            MainTab.FOR_YOU -> ForYouScreen(items, onOpenViewer = { ids, index -> openViewer(ids, index) }, onOpenCollection = ::openCollection)
                            MainTab.ALBUMS -> AlbumsScreen(items, albums, onOpen = ::openCollection)
                            MainTab.SEARCH -> SearchScreen(items, settings, onOpenViewer = { ids, index -> openViewer(ids, index) })
                        }
                    }
                }
        }
    }
}
