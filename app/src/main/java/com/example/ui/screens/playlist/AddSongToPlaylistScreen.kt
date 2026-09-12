package com.example.ui.screens.playlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.repository.MusicRepository
import com.example.model.Song
import com.example.ui.components.SongCover
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongToPlaylistScreen(
    playlistId: Int,
    repository: MusicRepository,
    onBack: () -> Unit
) {
    var allSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var playlistSongIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(playlistId) {
        allSongs = repository.getSongs()
        val playlistSongs = repository.getPlaylistSongs(playlistId)
        playlistSongIds = playlistSongs.map { it.id }.toSet()
    }

    val filteredSongs = remember(allSongs, searchQuery) {
        if (searchQuery.isBlank()) {
            allSongs
        } else {
            allSongs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Songs", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search songs...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = Color.Gray)
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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(filteredSongs) { song ->
                    val isAdded = playlistSongIds.contains(song.id)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        SongCover(imageUrl = song.coverUrl, size = 50.dp)

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = song.artist,
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (isAdded) {
                                        repository.removeSongFromPlaylist(playlistId, song.id)
                                        playlistSongIds = playlistSongIds - song.id
                                    } else {
                                        repository.addSongToPlaylist(playlistId, song.id)
                                        playlistSongIds = playlistSongIds + song.id
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isAdded) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = if (isAdded) "Added" else "Add",
                                tint = if (isAdded) Color(0xFF1DB954) else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
