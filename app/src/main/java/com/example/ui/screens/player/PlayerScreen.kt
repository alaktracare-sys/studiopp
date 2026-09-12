package com.example.ui.screens.player

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioController
import com.example.audio.PlaybackState
import com.example.ui.components.SongCover
import com.example.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    audioController: AudioController,
    playbackState: PlaybackState,
    isDownloaded: Boolean = false,
    downloadProgress: Float? = null,
    onToggleDownload: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onClose: () -> Unit,
    onLikeToggle: () -> Unit
) {
    val song = playbackState.currentSong ?: run {
        onClose()
        return
    }

    val context = LocalContext.current
    var showQueueSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }
    val sleepTimerSeconds by audioController.sleepTimerRemainingSeconds.collectAsState()

    // Dynamic atmospheric gradient extracted from song picture (matching screenshot):
    // Pure pitch black at top (behind header and upper artwork),
    // blooming into the rich song-dependent dominant color across the middle (track info, seekbar, controls),
    // then smoothly deepening into a dark shade at the bottom.
    val dominant = playbackState.dominantColor
    val midTone = Color(
        red = (dominant.red * 0.72f).coerceIn(0f, 1f),
        green = (dominant.green * 0.72f).coerceIn(0f, 1f),
        blue = (dominant.blue * 0.72f).coerceIn(0f, 1f),
        alpha = 1f
    )
    val bottomTone = Color(
        red = (dominant.red * 0.22f).coerceIn(0f, 1f),
        green = (dominant.green * 0.22f).coerceIn(0f, 1f),
        blue = (dominant.blue * 0.22f).coerceIn(0f, 1f),
        alpha = 1f
    )

    val backgroundBrush = Brush.verticalGradient(
        0.0f to Color.Black,
        0.32f to Color.Black,
        0.50f to midTone.copy(alpha = 0.65f),
        0.68f to midTone,
        0.88f to bottomTone,
        1.0f to Color(0xFF080808)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .background(backgroundBrush)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimize Player",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Playing from Playlist",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = if (song.isLiked) "Liked Songs" else "Alaktra Stream",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 3-dot menu button at top right
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(AlaktraSurface)
                    ) {
                        // Download
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isDownloaded) "Remove download" else "Download",
                                    color = AlaktraTextPrimary
                                )
                            },
                            leadingIcon = {
                                if (downloadProgress != null) {
                                    CircularProgressIndicator(
                                        progress = { downloadProgress },
                                        color = AlaktraMint,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                                        contentDescription = null,
                                        tint = if (isDownloaded) AlaktraMint else AlaktraTextPrimary
                                    )
                                }
                            },
                            onClick = {
                                showMenu = false
                                onToggleDownload()
                            }
                        )

                        // Add to playlist
                        DropdownMenuItem(
                            text = { Text("Add to playlist", color = AlaktraTextPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PlaylistAdd,
                                    contentDescription = null,
                                    tint = AlaktraTextPrimary
                                )
                            },
                            onClick = {
                                showMenu = false
                                onAddToPlaylist()
                            }
                        )

                        // Queue
                        DropdownMenuItem(
                            text = { Text("Queue", color = AlaktraTextPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = AlaktraTextPrimary
                                )
                            },
                            onClick = {
                                showMenu = false
                                showQueueSheet = true
                            }
                        )

                        // Sleep timer
                        DropdownMenuItem(
                            text = {
                                val label = if (sleepTimerSeconds != null) {
                                    "Sleep timer (${sleepTimerSeconds!! / 60}m)"
                                } else {
                                    "Sleep timer"
                                }
                                Text(label, color = AlaktraTextPrimary)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = if (sleepTimerSeconds != null) AlaktraMint else AlaktraTextPrimary
                                )
                            },
                            onClick = {
                                showMenu = false
                                showSleepTimerDialog = true
                            }
                        )
                    }
                }
            }

            // Lyrics Pellet (positioned between top playlist/liked song header and song artwork)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF263328).copy(alpha = 0.85f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                    .padding(horizontal = 18.dp, vertical = 7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lyrics",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = "${song.title} • ${song.artist}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Lyrics",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // Large Artwork with subtle rounded corners (matching screenshot)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // Artwork container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(16.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .background(AlaktraCard)
                ) {
                    SongCover(
                        imageUrl = song.coverUrl,
                        size = 380.dp,
                        cornerRadius = 8.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title, Artist, and Green Like Checkmark
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Compact circular like badge (smaller checkmark button)
                IconButton(
                    onClick = onLikeToggle,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (song.isLiked) Color(0xFF1DB954) else Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = if (song.isLiked) Icons.Default.Check else Icons.Default.FavoriteBorder,
                            contentDescription = if (song.isLiked) "Liked" else "Like",
                            tint = if (song.isLiked) Color.Black else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Custom Duration Bar (matching uploaded screenshot)
            CustomDurationBar(
                positionMs = playbackState.currentPositionMs,
                durationMs = playbackState.durationMs,
                onSeek = { targetMs -> audioController.seekTo(targetMs) },
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Playback Controls Row: Shuffle, Prev, Play/Pause, Next, Timer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                // Shuffle
                IconButton(
                    onClick = { audioController.toggleShuffle() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.isShuffle) Color(0xFF1DB954) else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Previous
                IconButton(
                    onClick = { audioController.previous() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Track",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Play / Pause: Solid White Circle with Solid Black Icon (matching screenshot)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { audioController.togglePlayPause() }
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Next
                IconButton(
                    onClick = { audioController.next() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Track",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Loop / Repeat button (next to forward button, toggles OFF -> ALL -> ONE -> OFF)
                IconButton(
                    onClick = { audioController.toggleLoop() },
                    modifier = Modifier.size(44.dp)
                ) {
                    val isLoopActive = playbackState.loopMode != com.example.audio.LoopMode.OFF
                    val loopIcon = if (playbackState.loopMode == com.example.audio.LoopMode.ONE) {
                        Icons.Default.RepeatOne
                    } else {
                        Icons.Default.Repeat
                    }
                    Icon(
                        imageVector = loopIcon,
                        contentDescription = "Loop Mode",
                        tint = if (isLoopActive) Color(0xFF1DB954) else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Center-aligned small pellet below play button with credits of the song
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .clickable { showCreditsDialog = true }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Credits",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Credits • ${song.artist}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Bottom row: Share in left bottom corner, Queue in right bottom corner
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // Share button in left bottom corner
                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Listening to ${song.title} by ${song.artist} on Alaktra Stream"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share song via"))
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Queue button in right bottom corner
                IconButton(
                    onClick = { showQueueSheet = true },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "View Queue",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.12f))
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Sleep Timer Selection Dialog
        if (showSleepTimerDialog) {
            AlertDialog(
                onDismissRequest = { showSleepTimerDialog = false },
                containerColor = AlaktraCard,
                titleContentColor = AlaktraTextPrimary,
                textContentColor = AlaktraTextSecondary,
                title = { Text("Sleep Timer") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val timerOptions = listOf(
                            "15 minutes" to 15,
                            "30 minutes" to 30,
                            "45 minutes" to 45,
                            "60 minutes" to 60,
                            "Turn off timer" to 0
                        )
                        timerOptions.forEach { (label, minutes) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        audioController.setSleepTimer(minutes)
                                        showSleepTimerDialog = false
                                        val msg = if (minutes > 0) "Sleep timer set for $minutes minutes" else "Sleep timer turned off"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (minutes == 0) Color(0xFFFF5252) else AlaktraTextPrimary,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSleepTimerDialog = false }) {
                        Text("Close", color = AlaktraMint)
                    }
                }
            )
        }

        // Song Credits Dialog
        if (showCreditsDialog) {
            AlertDialog(
                onDismissRequest = { showCreditsDialog = false },
                containerColor = AlaktraCard,
                titleContentColor = AlaktraTextPrimary,
                textContentColor = AlaktraTextSecondary,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = AlaktraMint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Song Credits", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Column {
                            Text(
                                text = "Title",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White
                            )
                        }
                        Column {
                            Text(
                                text = "Performed by",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White
                            )
                        }
                        Column {
                            Text(
                                text = "Platform & Quality",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "Alaktra Stream • Lossless / Hi-Fi Audio",
                                style = MaterialTheme.typography.bodySmall,
                                color = AlaktraMint
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCreditsDialog = false }) {
                        Text("Close", color = AlaktraMint)
                    }
                }
            )
        }

        // Queue Modal Bottom Sheet
        if (showQueueSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                containerColor = AlaktraCard,
                contentColor = AlaktraTextPrimary,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AlaktraTextMuted.copy(alpha = 0.5f))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Now Playing & Queue",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = AlaktraTextPrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        if (playbackState.upNext.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Up Next",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = AlaktraMint,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            itemsIndexed(playbackState.upNext) { _, upNextSong ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    SongCover(imageUrl = upNextSong.coverUrl, size = 44.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = upNextSong.title,
                                            color = AlaktraTextPrimary,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = upNextSong.artist,
                                            color = AlaktraTextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Queue (${playbackState.queue.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = AlaktraTextPrimary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                            )
                        }

                        itemsIndexed(playbackState.queue) { idx, qSong ->
                            val isCurrent = idx == playbackState.currentIndex
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isCurrent) AlaktraSurface else Color.Transparent)
                                    .clickable { audioController.playQueue(playbackState.queue, idx) }
                                    .padding(vertical = 6.dp, horizontal = 8.dp)
                            ) {
                                SongCover(imageUrl = qSong.coverUrl, size = 44.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = qSong.title,
                                        color = if (isCurrent) AlaktraMint else AlaktraTextPrimary,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = qSong.artist,
                                        color = AlaktraTextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                                IconButton(
                                    onClick = { audioController.removeFromQueue(idx) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = AlaktraTextMuted,
                                        modifier = Modifier.size(18.dp)
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

/**
 * Custom duration seek bar exactly matching the uploaded screenshot:
 * - Thin 3.5dp rounded bar
 * - Inactive: translucent white
 * - Active: crisp solid white
 * - Thumb: small white circle dot at current position
 * - Direct tap & scrub gestures
 * - Timestamps directly below in clean 12sp typography
 */
@Composable
private fun CustomDurationBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val currentFraction = if (isDragging) {
        dragFraction
    } else {
        if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    }

    val displayPositionMs = if (isDragging) {
        (dragFraction * durationMs).toLong()
    } else {
        positionMs
    }

    val density = LocalDensity.current

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (size.width > 0 && durationMs > 0) {
                            val f = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek((f * durationMs).toLong())
                        }
                    }
                }
                .pointerInput(durationMs) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            if (size.width > 0) {
                                dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            if (durationMs > 0) {
                                onSeek((dragFraction * durationMs).toLong())
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (size.width > 0) {
                                dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val activeWidthPx = totalWidthPx * currentFraction

            // Inactive track line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f))
            )

            // Active track line
            Box(
                modifier = Modifier
                    .fillMaxWidth(currentFraction)
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )

            // Small white thumb circle dot
            val thumbRadiusDp = 4.5.dp
            val thumbRadiusPx = with(density) { thumbRadiusDp.toPx() }
            val thumbOffsetDp = with(density) {
                ((activeWidthPx - thumbRadiusPx).coerceIn(0f, (totalWidthPx - thumbRadiusPx * 2f).coerceAtLeast(0f))).toDp()
            }

            Box(
                modifier = Modifier
                    .offset(x = thumbOffsetDp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Timestamps below
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = formatTime(displayPositionMs),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = formatTime(durationMs),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
