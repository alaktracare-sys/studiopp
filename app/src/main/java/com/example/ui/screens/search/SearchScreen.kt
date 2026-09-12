package com.example.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioController
import com.example.data.repository.MusicRepository
import com.example.model.Song
import com.example.ui.components.SongItemRow
import com.example.ui.components.SongMenuBottomSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    repository: MusicRepository,
    audioController: AudioController,
    onNavigateToPlaylistSelect: (Song) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedSongForMenu by remember { mutableStateOf<Song?>(null) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val playbackState by audioController.playbackState.collectAsState()
    val downloadProgress by repository.downloadManager.downloadProgress.collectAsState()

    LaunchedEffect(Unit) {
        results = repository.getSongs()
    }

    fun triggerSearch(q: String) {
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(250) // debounce
            isSearching = true
            results = repository.getSongs(query = q.ifBlank { null })
            isSearching = false
        }
    }

    Scaffold(
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )

            // Search Bar
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    triggerSearch(it)
                },
                placeholder = { Text("Songs, artists, or titles...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.Gray
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            triggerSearch("")
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = Color.Gray
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF242424),
                    unfocusedContainerColor = Color(0xFF242424),
                    focusedBorderColor = Color(0xFF1DB954),
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (isSearching) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator(color = Color(0xFF1DB954))
                }
            } else if (results.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = "No songs found for \"$query\"",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 140.dp)
                ) {
                    itemsIndexed(results) { index, song ->
                        val isCurrent = playbackState.currentSong?.id == song.id
                        SongItemRow(
                            song = song,
                            isPlaying = isCurrent && playbackState.isPlaying,
                            downloadProgress = downloadProgress[song.id],
                            onSongClick = { audioController.playQueue(results, index) },
                            onLikeToggle = {
                                scope.launch {
                                    val isLiked = repository.toggleLike(song)
                                    audioController.updateSongLiked(song.id, isLiked)
                                    results = results.map { if (it.id == song.id) it.copy(isLiked = isLiked) else it }
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
            onAddToQueue = { audioController.addToUpNext(song) },
            onAddToPlaylist = { onNavigateToPlaylistSelect(song) },
            onToggleDownload = {
                scope.launch {
                    if (isDownloaded) {
                        repository.downloadManager.removeDownload(song.id)
                        results = results.map { if (it.id == song.id) it.copy(isDownloaded = false, localPath = null) else it }
                    } else {
                        repository.downloadManager.downloadSong(song)
                        val path = repository.downloadManager.getLocalPath(song.id)
                        results = results.map { if (it.id == song.id) it.copy(isDownloaded = true, localPath = path) else it }
                    }
                }
            }
        )
    }
}
