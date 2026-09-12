package com.example.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioController
import com.example.data.repository.MusicRepository
import com.example.model.Song
import com.example.ui.components.SongItemRow
import com.example.ui.components.SongMenuBottomSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: Int,
    playlistName: String,
    repository: MusicRepository,
    audioController: AudioController,
    onBack: () -> Unit,
    onNavigateToAddSongs: () -> Unit
) {
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedSongForMenu by remember { mutableStateOf<Song?>(null) }
    val scope = rememberCoroutineScope()

    val playbackState by audioController.playbackState.collectAsState()
    val downloadProgress by repository.downloadManager.downloadProgress.collectAsState()

    LaunchedEffect(playlistId) {
        songs = if (playlistId == -1) {
            repository.getLikedSongs()
        } else {
            repository.getPlaylistSongs(playlistId)
        }
        isLoading = false
    }

    Scaffold(
        containerColor = Color(0xFF121212)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            // Gradient Header with Playlist Title
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF3B82F6).copy(alpha = 0.6f), Color(0xFF1E1B4B), Color(0xFF121212))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 12.dp, start = 8.dp)
                    ) {
                        Text(
                            text = playlistName,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = "${songs.size} songs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
                        )
                    }
                }
            }

            // Action Buttons: Download all (Spotify circular button) & Big Play Button
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    // Download all button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1DB954))
                            .clickable {
                                scope.launch {
                                    songs.forEach { song ->
                                        if (!song.isDownloaded) {
                                            repository.downloadManager.downloadSong(song)
                                        }
                                    }
                                }
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Download Playlist",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Large Play button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1DB954))
                            .clickable {
                                if (songs.isNotEmpty()) {
                                    audioController.playQueue(songs, 0)
                                }
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play All",
                            tint = Color.Black,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }

            // Add Song Row (if not Liked Songs system playlist)
            if (playlistId != -1) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToAddSongs)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF282828))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Text(
                            text = "Add to this playlist",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFF1DB954))
                    }
                }
            } else {
                itemsIndexed(songs) { index, song ->
                    val isCurrent = playbackState.currentSong?.id == song.id
                    SongItemRow(
                        song = song,
                        isPlaying = isCurrent && playbackState.isPlaying,
                        downloadProgress = downloadProgress[song.id],
                        onSongClick = { audioController.playQueue(songs, index) },
                        onLikeToggle = {
                            scope.launch {
                                val isLiked = repository.toggleLike(song)
                                audioController.updateSongLiked(song.id, isLiked)
                                songs = songs.map { if (it.id == song.id) it.copy(isLiked = isLiked) else it }
                            }
                        },
                        onMoreClick = { selectedSongForMenu = song }
                    )
                }
            }
        }
    }

    selectedSongForMenu?.let { song ->
        val isDownloaded = song.isDownloaded || (downloadProgress[song.id] == null && song.localPath != null)
        SongMenuBottomSheet(
            song = song,
            isDownloaded = isDownloaded,
            onDismiss = { selectedSongForMenu = null },
            onAddToQueue = { audioController.addToUpNext(song) },
            onAddToPlaylist = {},
            onToggleDownload = {
                scope.launch {
                    if (isDownloaded) {
                        repository.downloadManager.removeDownload(song.id)
                        songs = songs.map { if (it.id == song.id) it.copy(isDownloaded = false, localPath = null) else it }
                    } else {
                        repository.downloadManager.downloadSong(song)
                        val path = repository.downloadManager.getLocalPath(song.id)
                        songs = songs.map { if (it.id == song.id) it.copy(isDownloaded = true, localPath = path) else it }
                    }
                }
            }
        )
    }
}
