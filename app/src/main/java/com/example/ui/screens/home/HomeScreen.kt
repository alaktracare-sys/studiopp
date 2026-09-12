package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioController
import com.example.data.repository.MusicRepository
import com.example.model.Song
import com.example.ui.components.SongCover
import com.example.ui.components.SongItemRow
import com.example.ui.components.SongMenuBottomSheet
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun HomeScreen(
    repository: MusicRepository,
    audioController: AudioController,
    onNavigateToProfile: () -> Unit,
    onNavigateToPlaylistSelect: (Song) -> Unit
) {
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedSongForMenu by remember { mutableStateOf<Song?>(null) }
    val scope = rememberCoroutineScope()

    val playbackState by audioController.playbackState.collectAsState()
    val downloadProgress by repository.downloadManager.downloadProgress.collectAsState()

    val user = remember { repository.authPreferences.getUser() }

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    LaunchedEffect(Unit) {
        songs = repository.getSongs()
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
            // Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Column {
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            ),
                            color = Color.White
                        )
                        if (user != null) {
                            Text(
                                text = user.username,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF1DB954)
                            )
                        }
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF282828))
                            .clickable(onClick = onNavigateToProfile)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Quick Featured Row
            if (songs.isNotEmpty()) {
                item {
                    Text(
                        text = "Jump Back In",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(start = 20.dp, bottom = 12.dp, top = 8.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(songs.take(6)) { song ->
                            FeaturedSongCard(
                                song = song,
                                isPlaying = playbackState.currentSong?.id == song.id && playbackState.isPlaying,
                                onClick = { audioController.playSong(song) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // All Tracks Header
            item {
                Text(
                    text = "Recommended for You",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
                )
            }

            if (isLoading) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
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

    // Bottom sheet for more menu
    selectedSongForMenu?.let { song ->
        val isDownloaded = song.isDownloaded || (downloadProgress[song.id] == null && song.localPath != null)
        SongMenuBottomSheet(
            song = song,
            isDownloaded = isDownloaded,
            onDismiss = { selectedSongForMenu = null },
            onAddToQueue = { audioController.addToUpNext(song) },
            onAddToPlaylist = { onNavigateToPlaylistSelect(song) },
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

@Composable
private fun FeaturedSongCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E1E))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        SongCover(
            imageUrl = song.coverUrl,
            size = 110.dp,
            cornerRadius = 10.dp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (isPlaying) Color(0xFF1DB954) else Color.White
        )
        Text(
            text = song.artist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = Color.LightGray
        )
    }
}
