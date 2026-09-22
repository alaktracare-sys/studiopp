package com.example.ui.screens.playlist

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioController
import com.example.audio.ContextType
import com.example.audio.PlaybackContext
import com.example.data.repository.MusicRepository
import com.example.model.Song
import com.example.ui.components.SongItemRow
import com.example.ui.components.SongMenuBottomSheet
import com.example.ui.theme.*
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
    val context = LocalContext.current
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
        containerColor = AlaktraBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .widthIn(max = 840.dp),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
            // Gradient Header with Playlist Title
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    if (playlistId == -1) Color(0xFF6366F1).copy(alpha = 0.5f) else AlaktraMint.copy(alpha = 0.35f),
                                    AlaktraSurface,
                                    AlaktraBackground
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .clip(CircleShape)
                            .background(AlaktraSurface.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AlaktraTextPrimary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 16.dp, start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (playlistId == -1) {
                                        Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF06B6D4), AlaktraMint))
                                    } else {
                                        Brush.linearGradient(listOf(AlaktraMint, AlaktraCyan))
                                    }
                                )
                        ) {
                            Icon(
                                imageVector = if (playlistId == -1) Icons.Default.Favorite else Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = if (playlistId == -1) Color.White else Color(0xFF041C12),
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = playlistName,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp
                                ),
                                color = AlaktraTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${songs.size} tracks • Alaktra Collection",
                                style = MaterialTheme.typography.bodySmall,
                                color = AlaktraTextSecondary
                            )
                        }
                    }
                }
            }

            // Action Buttons: Shuffle, Download all & Big Play Button
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Download all button
                        IconButton(
                            onClick = {
                                scope.launch {
                                    songs.forEach { song ->
                                        if (!song.isDownloaded) {
                                            repository.downloadManager.downloadSong(song)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(AlaktraSurface)
                                .border(1.dp, AlaktraBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DownloadForOffline,
                                contentDescription = "Download All",
                                tint = AlaktraMint,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Shuffle play button
                        IconButton(
                            onClick = {
                                if (songs.isNotEmpty()) {
                                    val ctx = PlaybackContext(
                                        uri = "alaktra:playlist:$playlistId",
                                        type = ContextType.PLAYLIST,
                                        name = playlistName,
                                        trackIds = songs.map { it.id },
                                        tracks = songs
                                    )
                                    audioController.playContext(ctx, startIndex = 0, autoShuffle = true)
                                }
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(AlaktraSurface)
                                .border(1.dp, AlaktraBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = AlaktraTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Large Play button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(AlaktraMint, AlaktraCyan)))
                            .clickable {
                                if (songs.isNotEmpty()) {
                                    val ctx = PlaybackContext(
                                        uri = "alaktra:playlist:$playlistId",
                                        type = ContextType.PLAYLIST,
                                        name = playlistName,
                                        trackIds = songs.map { it.id },
                                        tracks = songs
                                    )
                                    audioController.playContext(ctx, startIndex = 0, autoShuffle = false)
                                }
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play All",
                            tint = Color(0xFF041C12),
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
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AlaktraSurface)
                                .border(1.dp, AlaktraBorder, RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = AlaktraMint,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Text(
                            text = "Add tracks to playlist",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = AlaktraTextPrimary
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
                        CircularProgressIndicator(color = AlaktraMint)
                    }
                }
            } else if (songs.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.MusicOff,
                                contentDescription = null,
                                tint = AlaktraTextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Playlist is empty",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AlaktraTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (playlistId == -1) "Tap the heart icon on any song to add it here." else "Add tracks from your server library.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AlaktraTextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(songs) { index, song ->
                    val isCurrent = playbackState.currentSong?.id == song.id
                    SongItemRow(
                        song = song,
                        isPlaying = isCurrent && playbackState.isPlaying,
                        downloadProgress = downloadProgress[song.id],
                        onSongClick = {
                            val ctx = PlaybackContext(
                                uri = "alaktra:playlist:$playlistId",
                                type = ContextType.PLAYLIST,
                                name = playlistName,
                                trackIds = songs.map { it.id },
                                tracks = songs
                            )
                            audioController.playContext(ctx, startIndex = index, autoShuffle = false)
                        },
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
    }

    selectedSongForMenu?.let { song ->
        val isDownloaded = song.isDownloaded || (downloadProgress[song.id] == null && song.localPath != null)
        SongMenuBottomSheet(
            song = song,
            isDownloaded = isDownloaded,
            onDismiss = { selectedSongForMenu = null },
            onPlayNext = {
                audioController.playNext(song)
                Toast.makeText(context, "Playing next: ${song.title}", Toast.LENGTH_SHORT).show()
            },
            onAddToQueue = {
                audioController.addToQueue(song)
                Toast.makeText(context, "Added to queue: ${song.title}", Toast.LENGTH_SHORT).show()
            },
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

