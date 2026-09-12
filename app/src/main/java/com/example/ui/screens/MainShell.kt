package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.AudioController
import com.example.data.repository.MusicRepository
import com.example.model.Playlist
import com.example.model.Song
import com.example.ui.components.MiniPlayer
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.player.PlayerScreen
import com.example.ui.screens.playlist.AddSongToPlaylistScreen
import com.example.ui.screens.playlist.PlaylistScreen
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.search.SearchScreen
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
    var currentTab by remember { mutableIntStateOf(0) }
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var activePlaylistId by remember { mutableIntStateOf(1) }
    var activePlaylistName by remember { mutableStateOf("Playlist") }
    var showFullPlayer by remember { mutableStateOf(false) }

    // Playlist selector sheet (for "Add to Playlist" action)
    var songForPlaylistSelection by remember { mutableStateOf<Song?>(null) }
    var availablePlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    val scope = rememberCoroutineScope()

    val playbackState by audioController.playbackState.collectAsState()

    fun openPlaylist(id: Int, name: String) {
        activePlaylistId = id
        activePlaylistName = name
        currentScreen = Screen.PLAYLIST
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Scaffold(
            containerColor = Color(0xFF121212),
            bottomBar = {
                if (currentScreen in listOf(Screen.HOME, Screen.SEARCH, Screen.LIBRARY)) {
                    Column {
                        // MiniPlayer pinned right above bottom navigation bar
                        if (playbackState.currentSong != null) {
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
                                onExpand = { showFullPlayer = true }
                            )
                        }

                        NavigationBar(
                            containerColor = Color(0xFF121212),
                            contentColor = Color.White
                        ) {
                            NavigationBarItem(
                                selected = currentTab == 0 && currentScreen == Screen.HOME,
                                onClick = {
                                    currentTab = 0
                                    currentScreen = Screen.HOME
                                },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                label = { Text("Home") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = Color.White,
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray,
                                    indicatorColor = Color(0xFF242424)
                                )
                            )

                            NavigationBarItem(
                                selected = currentTab == 1 && currentScreen == Screen.SEARCH,
                                onClick = {
                                    currentTab = 1
                                    currentScreen = Screen.SEARCH
                                },
                                icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                                label = { Text("Search") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = Color.White,
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray,
                                    indicatorColor = Color(0xFF242424)
                                )
                            )

                            NavigationBarItem(
                                selected = currentTab == 2 && currentScreen == Screen.LIBRARY,
                                onClick = {
                                    currentTab = 2
                                    currentScreen = Screen.LIBRARY
                                },
                                icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                                label = { Text("Library") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = Color.White,
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray,
                                    indicatorColor = Color(0xFF242424)
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
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
                            }
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
                            onNavigateToLikedSongs = { openPlaylist(-1, "Liked Songs") }
                        )
                    }

                    Screen.PLAYLIST -> {
                        PlaylistScreen(
                            playlistId = activePlaylistId,
                            playlistName = activePlaylistName,
                            repository = repository,
                            audioController = audioController,
                            onBack = { currentScreen = Screen.LIBRARY },
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
                            onBack = { currentScreen = Screen.HOME },
                            onLogout = onLogout
                        )
                    }
                }
            }
        }

        // Full Screen Player Modal / Overlay
        AnimatedVisibility(
            visible = showFullPlayer,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            PlayerScreen(
                audioController = audioController,
                playbackState = playbackState,
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
                containerColor = Color(0xFF1E1E1E),
                contentColor = Color.White,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Add to playlist",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
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
                                    tint = Color(0xFF1DB954),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = pl.name,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${pl.songCount} songs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.LightGray
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
