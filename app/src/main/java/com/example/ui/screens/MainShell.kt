package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioController
import com.example.data.repository.MusicRepository
import com.example.model.Playlist
import com.example.model.Song
import com.example.ui.components.MiniPlayer
import com.example.ui.responsive.rememberWindowSizeInfo
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.player.PlayerScreen
import com.example.ui.screens.playlist.AddSongToPlaylistScreen
import com.example.ui.screens.playlist.PlaylistScreen
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.search.SearchScreen
import com.example.ui.theme.*
import kotlinx.coroutines.launch

enum class Screen {
    HOME, SEARCH, LIBRARY, PLAYLIST, ADD_SONGS, PROFILE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    repository: MusicRepository,
    audioController: AudioController,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var currentTab by remember { mutableIntStateOf(0) }
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var playlistOriginScreen by remember { mutableStateOf(Screen.HOME) }
    var playlistOriginTab by remember { mutableIntStateOf(0) }
    var activePlaylistId by remember { mutableIntStateOf(1) }
    var activePlaylistName by remember { mutableStateOf("Playlist") }
    var showFullPlayer by remember { mutableStateOf(false) }

    // Playlist selector sheet (for "Add to Playlist" action)
    var songForPlaylistSelection by remember { mutableStateOf<Song?>(null) }
    var availablePlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    val scope = rememberCoroutineScope()

    val playbackState by audioController.playbackState.collectAsState()

    val navigateBackFromPlaylist: () -> Unit = {
        currentScreen = playlistOriginScreen
        currentTab = playlistOriginTab
    }

    // Back handling
    BackHandler(enabled = showFullPlayer || currentScreen != Screen.HOME || currentTab != 0) {
        when {
            showFullPlayer -> showFullPlayer = false
            currentScreen == Screen.ADD_SONGS -> currentScreen = Screen.PLAYLIST
            currentScreen == Screen.PLAYLIST -> navigateBackFromPlaylist()
            currentScreen == Screen.PROFILE -> {
                currentScreen = Screen.HOME
                currentTab = 0
            }
            currentTab != 0 -> {
                currentTab = 0
                currentScreen = Screen.HOME
            }
        }
    }

    fun openPlaylist(id: Int, name: String) {
        if (currentScreen != Screen.PLAYLIST && currentScreen != Screen.ADD_SONGS) {
            playlistOriginScreen = currentScreen
            playlistOriginTab = currentTab
        }
        activePlaylistId = id
        activePlaylistName = name
        currentScreen = Screen.PLAYLIST
    }

    val windowSize = rememberWindowSizeInfo()
    val useNavRail = !windowSize.isCompact

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AlaktraBackground)
    ) {
        Scaffold(
            containerColor = AlaktraBackground,
            bottomBar = {
                if (!useNavRail && !showFullPlayer && currentScreen in listOf(Screen.HOME, Screen.SEARCH, Screen.LIBRARY)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        NavigationBar(
                            containerColor = AlaktraSurface.copy(alpha = 0.65f),
                            contentColor = AlaktraTextPrimary,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .height(69.dp)
                                .border(width = 0.5.dp, color = Color.Black.copy(alpha = 0.12f), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        ) {
                            NavigationBarItem(
                                selected = currentTab == 0 && currentScreen == Screen.HOME,
                                onClick = {
                                    currentTab = 0
                                    currentScreen = Screen.HOME
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (currentTab == 0 && currentScreen == Screen.HOME) Icons.Default.Home else Icons.Outlined.Home,
                                        contentDescription = "Home"
                                    )
                                },
                                label = {
                                    Text(
                                        "Home",
                                        fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AlaktraMint,
                                    selectedTextColor = AlaktraMint,
                                    unselectedIconColor = AlaktraTextMuted,
                                    unselectedTextColor = AlaktraTextMuted,
                                    indicatorColor = AlaktraMint.copy(alpha = 0.15f)
                                )
                            )

                            NavigationBarItem(
                                selected = currentTab == 1 && currentScreen == Screen.SEARCH,
                                onClick = {
                                    currentTab = 1
                                    currentScreen = Screen.SEARCH
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (currentTab == 1 && currentScreen == Screen.SEARCH) Icons.Default.Search else Icons.Outlined.Search,
                                        contentDescription = "Search"
                                    )
                                },
                                label = {
                                    Text(
                                        "Search",
                                        fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AlaktraMint,
                                    selectedTextColor = AlaktraMint,
                                    unselectedIconColor = AlaktraTextMuted,
                                    unselectedTextColor = AlaktraTextMuted,
                                    indicatorColor = AlaktraMint.copy(alpha = 0.15f)
                                )
                            )

                            NavigationBarItem(
                                selected = currentTab == 2 && currentScreen == Screen.LIBRARY,
                                onClick = {
                                    currentTab = 2
                                    currentScreen = Screen.LIBRARY
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (currentTab == 2 && currentScreen == Screen.LIBRARY) Icons.Default.LibraryMusic else Icons.Outlined.LibraryMusic,
                                        contentDescription = "Library"
                                    )
                                },
                                label = {
                                    Text(
                                        "Library",
                                        fontWeight = if (currentTab == 2) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AlaktraMint,
                                    selectedTextColor = AlaktraMint,
                                    unselectedIconColor = AlaktraTextMuted,
                                    unselectedTextColor = AlaktraTextMuted,
                                    indicatorColor = AlaktraMint.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = padding.calculateTopPadding(),
                        bottom = if (!useNavRail && currentScreen in listOf(Screen.HOME, Screen.SEARCH, Screen.LIBRARY)) 0.dp else padding.calculateBottomPadding()
                    )
            ) {
                if (useNavRail && !showFullPlayer && currentScreen in listOf(Screen.HOME, Screen.SEARCH, Screen.LIBRARY, Screen.PLAYLIST, Screen.PROFILE)) {
                    NavigationRail(
                        containerColor = AlaktraSurface.copy(alpha = 0.25f),
                        contentColor = AlaktraTextPrimary,
                        header = {
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(AlaktraMint.copy(alpha = 0.15f))
                                    .border(1.dp, AlaktraMint.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Alaktra",
                                    tint = AlaktraMint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        },
                        modifier = Modifier
                            .fillMaxHeight()
                            .border(width = 0.5.dp, color = AlaktraBorder.copy(alpha = 0.30f))
                    ) {
                        NavigationRailItem(
                            selected = currentTab == 0 && currentScreen == Screen.HOME,
                            onClick = {
                                currentTab = 0
                                currentScreen = Screen.HOME
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == 0 && currentScreen == Screen.HOME) Icons.Default.Home else Icons.Outlined.Home,
                                    contentDescription = "Home"
                                )
                            },
                            label = {
                                Text(
                                    "Home",
                                    fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = AlaktraMint,
                                selectedTextColor = AlaktraMint,
                                unselectedIconColor = AlaktraTextMuted,
                                unselectedTextColor = AlaktraTextMuted,
                                indicatorColor = AlaktraMint.copy(alpha = 0.15f)
                            )
                        )

                        NavigationRailItem(
                            selected = currentTab == 1 && currentScreen == Screen.SEARCH,
                            onClick = {
                                currentTab = 1
                                currentScreen = Screen.SEARCH
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == 1 && currentScreen == Screen.SEARCH) Icons.Default.Search else Icons.Outlined.Search,
                                    contentDescription = "Search"
                                )
                            },
                            label = {
                                Text(
                                    "Search",
                                    fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = AlaktraMint,
                                selectedTextColor = AlaktraMint,
                                unselectedIconColor = AlaktraTextMuted,
                                unselectedTextColor = AlaktraTextMuted,
                                indicatorColor = AlaktraMint.copy(alpha = 0.15f)
                            )
                        )

                        NavigationRailItem(
                            selected = currentTab == 2 && currentScreen == Screen.LIBRARY,
                            onClick = {
                                currentTab = 2
                                currentScreen = Screen.LIBRARY
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentTab == 2 && currentScreen == Screen.LIBRARY) Icons.Default.LibraryMusic else Icons.Outlined.LibraryMusic,
                                    contentDescription = "Library"
                                )
                            },
                            label = {
                                Text(
                                    "Library",
                                    fontWeight = if (currentTab == 2) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = AlaktraMint,
                                selectedTextColor = AlaktraMint,
                                unselectedIconColor = AlaktraTextMuted,
                                unselectedTextColor = AlaktraTextMuted,
                                indicatorColor = AlaktraMint.copy(alpha = 0.15f)
                            )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    when (currentScreen) {
                        Screen.HOME -> {
                        HomeScreen(
                            repository = repository,
                            audioController = audioController,
                            onNavigateToProfile = { currentScreen = Screen.PROFILE },
                            onNavigateToPlaylistSelect = { song ->
                                scope.launch {
                                    availablePlaylists = repository.getPlaylists()
                                    songForPlaylistSelection = song
                                }
                            },
                            onNavigateToPlaylist = { id, name -> openPlaylist(id, name) },
                            onNavigateToLikedSongs = { openPlaylist(Playlist.ID_LIKED, "Liked Songs") },
                            onNavigateToDownloadedSongs = { openPlaylist(Playlist.ID_DOWNLOADED, "Downloaded Songs") }
                        )
                    }

                    Screen.SEARCH -> {
                        SearchScreen(
                            repository = repository,
                            audioController = audioController,
                            onNavigateToPlaylistSelect = { song ->
                                scope.launch {
                                    availablePlaylists = repository.getPlaylists()
                                    songForPlaylistSelection = song
                                }
                            }
                        )
                    }

                    Screen.LIBRARY -> {
                        LibraryScreen(
                            repository = repository,
                            onNavigateToPlaylist = { id, name -> openPlaylist(id, name) },
                            onNavigateToLikedSongs = { openPlaylist(Playlist.ID_LIKED, "Liked Songs") },
                            onNavigateToDownloadedSongs = { openPlaylist(Playlist.ID_DOWNLOADED, "Downloaded Songs") }
                        )
                    }

                    Screen.PLAYLIST -> {
                        PlaylistScreen(
                            playlistId = activePlaylistId,
                            playlistName = activePlaylistName,
                            repository = repository,
                            audioController = audioController,
                            onBack = navigateBackFromPlaylist,
                            onNavigateToAddSongs = { currentScreen = Screen.ADD_SONGS }
                        )
                    }

                    Screen.ADD_SONGS -> {
                        AddSongToPlaylistScreen(
                            playlistId = activePlaylistId,
                            repository = repository,
                            onBack = { currentScreen = Screen.PLAYLIST }
                        )
                    }

                    Screen.PROFILE -> {
                        ProfileScreen(
                            repository = repository,
                            audioController = audioController,
                            onBack = {
                                currentScreen = Screen.HOME
                                currentTab = 0
                            },
                            onLogout = onLogout
                        )
                    }
                }

                // Floating MiniPlayer over the scrolling content (no black square background)
                if (!showFullPlayer && playbackState.currentSong != null && currentScreen in listOf(Screen.HOME, Screen.SEARCH, Screen.LIBRARY, Screen.PLAYLIST)) {
                    val miniPlayerBottomPad = if (currentScreen in listOf(Screen.HOME, Screen.SEARCH, Screen.LIBRARY)) {
                        padding.calculateBottomPadding() + 6.dp
                    } else {
                        12.dp
                    }
                    MiniPlayer(
                        playbackState = playbackState,
                        onTogglePlay = { audioController.togglePlayPause() },
                        onNext = { audioController.next() },
                        onPrevious = { audioController.previous() },
                        onLikeToggle = {
                            playbackState.currentSong?.let { song ->
                                scope.launch {
                                    val isLiked = repository.toggleLike(song)
                                    audioController.updateSongLiked(song.id, isLiked)
                                }
                            }
                        },
                        onExpand = { showFullPlayer = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = 760.dp)
                            .padding(horizontal = 6.dp)
                            .padding(bottom = miniPlayerBottomPad)
                    )
                }
            }
        }
    }

        // Full Screen Player Modal / Overlay
        AnimatedVisibility(
            visible = showFullPlayer,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            val downloadProgressMap by repository.downloadManager.downloadProgress.collectAsState()
            var isDownloaded by remember(playbackState.currentSong?.id) { mutableStateOf(false) }
            LaunchedEffect(playbackState.currentSong?.id) {
                playbackState.currentSong?.let { s ->
                    isDownloaded = repository.downloadManager.isDownloaded(s.id)
                }
            }
            val currentDownloadProgress = playbackState.currentSong?.let { downloadProgressMap[it.id] }

            PlayerScreen(
                audioController = audioController,
                playbackState = playbackState,
                isDownloaded = isDownloaded,
                downloadProgress = currentDownloadProgress,
                onToggleDownload = {
                    playbackState.currentSong?.let { song ->
                        scope.launch {
                            if (isDownloaded) {
                                repository.downloadManager.removeDownload(song.id)
                                isDownloaded = false
                                android.widget.Toast.makeText(context, "Download removed", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, "Downloading ${song.title}...", android.widget.Toast.LENGTH_SHORT).show()
                                repository.downloadManager.downloadSong(song)
                                isDownloaded = repository.downloadManager.isDownloaded(song.id)
                            }
                        }
                    }
                },
                onAddToPlaylist = {
                    playbackState.currentSong?.let { song ->
                        scope.launch {
                            availablePlaylists = repository.getPlaylists()
                            songForPlaylistSelection = song
                        }
                    }
                },
                onClose = { showFullPlayer = false },
                onLikeToggle = {
                    playbackState.currentSong?.let { song ->
                        scope.launch {
                            val isLiked = repository.toggleLike(song)
                            audioController.updateSongLiked(song.id, isLiked)
                        }
                    }
                }
            )
        }

        // Add to Playlist Selection Modal Bottom Sheet
        songForPlaylistSelection?.let { song ->
            ModalBottomSheet(
                onDismissRequest = { songForPlaylistSelection = null },
                containerColor = AlaktraCard,
                contentColor = AlaktraTextPrimary,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Add to playlist",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = AlaktraTextPrimary,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                        ) {
                            items(availablePlaylists) { pl ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                repository.addSongToPlaylist(pl.id, song.id)
                                                songForPlaylistSelection = null
                                            }
                                        }
                                        .padding(vertical = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QueueMusic,
                                        contentDescription = null,
                                        tint = AlaktraMint,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = pl.name,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                            color = AlaktraTextPrimary
                                        )
                                        Text(
                                            text = "${pl.songCount} tracks",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AlaktraTextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

