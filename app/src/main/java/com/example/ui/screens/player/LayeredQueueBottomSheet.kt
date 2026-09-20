package com.example.ui.screens.player

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.audio.AudioController
import com.example.audio.LoopMode
import com.example.audio.PlaybackState
import com.example.model.Song
import com.example.ui.components.SongCover

// Spotify Design Palette Constants
private val SpotifyBlack = Color(0xFF121212)
private val SpotifySurface = Color(0xFF181818)
private val SpotifyElevated = Color(0xFF282828)
private val SpotifyGreen = Color(0xFF1DB954)
private val SpotifyRed = Color(0xFFE91429)
private val SpotifyTextPrimary = Color(0xFFFFFFFF)
private val SpotifyTextSecondary = Color(0xFFB3B3B3)
private val SpotifyTextSubtle = Color(0xFF727272)
private val SpotifyDivider = Color(0xFF282828)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayeredQueueBottomSheet(
    playbackState: PlaybackState,
    audioController: AudioController,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Upcoming songs in context queue after currentIndex
    val upcomingSongs = remember(playbackState.queue, playbackState.currentIndex) {
        if (playbackState.currentIndex in 0 until (playbackState.queue.size - 1)) {
            playbackState.queue.subList(playbackState.currentIndex + 1, playbackState.queue.size)
        } else {
            emptyList()
        }
    }

    // Maximum queue limit: 30 songs total
    val maxQueueLimit = 30
    val displayedUserQueue = remember(playbackState.userQueue) {
        playbackState.userQueue.take(maxQueueLimit)
    }
    val remainingSlots = (maxQueueLimit - displayedUserQueue.size).coerceAtLeast(0)
    val displayedUpcomingSongs = remember(upcomingSongs, remainingSlots) {
        upcomingSongs.take(remainingSlots)
    }

    // When LoopMode.ALL is active and we have remaining slots after upcoming songs, calculate loop-back songs
    val remainingAfterUpcoming = (remainingSlots - displayedUpcomingSongs.size).coerceAtLeast(0)
    val loopedBackSongs = remember(playbackState.queue, playbackState.loopMode, remainingAfterUpcoming, displayedUpcomingSongs) {
        if (playbackState.loopMode == LoopMode.ALL && remainingAfterUpcoming > 0 && playbackState.queue.isNotEmpty()) {
            playbackState.queue.take(remainingAfterUpcoming)
        } else {
            emptyList()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SpotifyBlack,
        contentColor = SpotifyTextPrimary,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF535353))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
        ) {
            // Spotify Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Close Queue",
                        tint = SpotifyTextPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = SpotifyTextPrimary
                )

                // Quick Action Controls: Shuffle & Loop
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Shuffle toggle button
                    IconButton(
                        onClick = {
                            audioController.toggleShuffle()
                            val msg = if (!playbackState.isShuffle) "Shuffle enabled" else "Shuffle disabled"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle Queue",
                            tint = if (playbackState.isShuffle) SpotifyGreen else SpotifyTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Loop toggle button in Queue header
                    IconButton(
                        onClick = {
                            audioController.toggleLoop()
                            val msg = when (playbackState.loopMode) {
                                LoopMode.OFF -> "Loop all tracks enabled"
                                LoopMode.ALL -> "Loop current track enabled"
                                LoopMode.ONE -> "Loop disabled"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        val (loopIcon, loopTint) = when (playbackState.loopMode) {
                            LoopMode.OFF -> Icons.Default.Repeat to SpotifyTextSecondary
                            LoopMode.ALL -> Icons.Default.Repeat to SpotifyGreen
                            LoopMode.ONE -> Icons.Default.RepeatOne to SpotifyGreen
                        }
                        Icon(
                            imageVector = loopIcon,
                            contentDescription = "Loop Mode",
                            tint = loopTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Loop Mode Visual Banner in Queue
            AnimatedVisibility(
                visible = playbackState.loopMode != LoopMode.OFF,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val isLoopOne = playbackState.loopMode == LoopMode.ONE
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SpotifyGreen.copy(alpha = 0.12f))
                        .border(1.dp, SpotifyGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isLoopOne) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = null,
                            tint = SpotifyGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isLoopOne) {
                                "Loop 1 Track Active • Current track will replay continuously"
                            } else {
                                "Loop All Active • Queue will continuously repeat from the start"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = SpotifyGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // ==========================================
                // 1. NOW PLAYING
                // ==========================================
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Now playing",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = SpotifyTextPrimary,
                        modifier = Modifier.padding(bottom = 10.dp, top = 4.dp)
                    )
                }

                item {
                    playbackState.currentSong?.let { currentSong ->
                        SpotifyNowPlayingRow(
                            song = currentSong,
                            isPlaying = playbackState.isPlaying,
                            isLoopOne = playbackState.loopMode == LoopMode.ONE
                        )
                    } ?: run {
                        Text(
                            text = "No track playing",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SpotifyTextSecondary,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }

                // ==========================================
                // 2. NEXT IN QUEUE (USER ADDED QUEUE)
                // ==========================================
                if (displayedUserQueue.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Next in queue",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = SpotifyTextPrimary
                            )

                            TextButton(
                                onClick = {
                                    audioController.clearUserQueue()
                                    Toast.makeText(context, "Queue cleared", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Clear queue",
                                    color = SpotifyTextSecondary,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    }

                    itemsIndexed(
                        items = displayedUserQueue,
                        key = { idx, s -> "user_${s.id}_$idx" }
                    ) { index, song ->
                        SpotifyQueueRow(
                            song = song,
                            index = index,
                            totalCount = displayedUserQueue.size,
                            onPlayNow = {
                                audioController.playUserQueueItem(index)
                                Toast.makeText(context, "Playing: ${song.title}", Toast.LENGTH_SHORT).show()
                            },
                            onRemoveFromQueue = {
                                audioController.removeFromUserQueue(index)
                                Toast.makeText(context, "Removed from queue", Toast.LENGTH_SHORT).show()
                            },
                            onAddToQueue = null,
                            onMove = { from, to ->
                                audioController.reorderUserQueue(from, to)
                            }
                        )
                    }
                }

                // ==========================================
                // 3. NEXT FROM: SOURCE (CONTEXT QUEUE)
                // ==========================================
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = "Next from: ${playbackState.playbackSource}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = SpotifyTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (playbackState.loopMode == LoopMode.ALL) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = null,
                                    tint = SpotifyGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Loop All",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = SpotifyGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (displayedUpcomingSongs.isEmpty() && loopedBackSongs.isEmpty()) {
                    item {
                        Text(
                            text = if (playbackState.loopMode == LoopMode.ONE) {
                                "Currently looping '${playbackState.currentSong?.title ?: "current song"}'"
                            } else {
                                "No more upcoming tracks from this stream"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SpotifyTextSecondary,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    itemsIndexed(
                        items = displayedUpcomingSongs,
                        key = { idx, s -> "ctx_${s.id}_$idx" }
                    ) { relativeIdx, song ->
                        val actualIndex = (playbackState.currentIndex + 1) + relativeIdx
                        SpotifyQueueRow(
                            song = song,
                            index = relativeIdx,
                            totalCount = displayedUpcomingSongs.size,
                            onPlayNow = {
                                audioController.playContextQueueItem(actualIndex)
                            },
                            onRemoveFromQueue = {
                                audioController.removeFromQueue(actualIndex)
                                Toast.makeText(context, "Removed from queue", Toast.LENGTH_SHORT).show()
                            },
                            onAddToQueue = {
                                audioController.addToQueue(song)
                                Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                            },
                            onMove = { relFrom, relTo ->
                                val actualFrom = (playbackState.currentIndex + 1) + relFrom
                                val actualTo = (playbackState.currentIndex + 1) + relTo
                                audioController.reorderQueue(actualFrom, actualTo)
                            }
                        )
                    }

                    // Loop All: Display continuous loop-back tracks when reaching near/end of playlist
                    if (loopedBackSongs.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(18.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = null,
                                    tint = SpotifyGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Looping back to start (${playbackState.playbackSource})",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = SpotifyGreen
                                )
                            }
                        }

                        itemsIndexed(
                            items = loopedBackSongs,
                            key = { idx, s -> "loop_${s.id}_$idx" }
                        ) { loopIdx, song ->
                            SpotifyQueueRow(
                                song = song,
                                index = loopIdx,
                                totalCount = loopedBackSongs.size,
                                isLoopedTrack = true,
                                onPlayNow = {
                                    audioController.playContextQueueItem(loopIdx)
                                },
                                onRemoveFromQueue = null,
                                onAddToQueue = {
                                    audioController.addToQueue(song)
                                    Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                                },
                                onMove = null
                            )
                        }
                    }
                }

                // Notice if total tracks reach the max limit of 30
                if (playbackState.userQueue.size + upcomingSongs.size > maxQueueLimit) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Showing first 30 tracks in queue",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = SpotifyTextSubtle
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Spotify-styled row for the currently playing track.
 * Clean: Circle selector completely removed.
 */
@Composable
private fun SpotifyNowPlayingRow(
    song: Song,
    isPlaying: Boolean,
    isLoopOne: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .padding(vertical = 6.dp)
    ) {
        SongCover(
            imageUrl = song.coverUrl,
            size = 48.dp,
            modifier = Modifier.clip(RoundedCornerShape(4.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = SpotifyGreen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = SpotifyTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isLoopOne) {
                    Text(
                        text = " • Looping",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = SpotifyGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Visual equalizer / repeat icon
        if (isLoopOne) {
            Icon(
                imageVector = Icons.Default.RepeatOne,
                contentDescription = "Repeating this track",
                tint = SpotifyGreen,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Icon(
                imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.Pause,
                contentDescription = if (isPlaying) "Playing" else "Paused",
                tint = SpotifyGreen,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Spotify-styled row with swipe gestures and smooth drag-and-drop rearranging:
 * - Circle selector completely removed.
 * - Left Swipe (End to Start): Add to queue (Spotify Green)
 * - Right Swipe (Start to End): Remove from queue (Spotify Red)
 * - Drag Handle: Drag up/down to rearrange songs dynamically just like Spotify, or tap for options!
 * - Tap: Play immediately
 * - No triangle play buttons, no X buttons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpotifyQueueRow(
    song: Song,
    index: Int,
    totalCount: Int,
    isLoopedTrack: Boolean = false,
    onPlayNow: () -> Unit,
    onRemoveFromQueue: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
    onMove: ((from: Int, to: Int) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 56.dp.toPx() }

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var showReorderMenu by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.EndToStart -> {
                    // Left swipe -> Add to queue
                    if (onAddToQueue != null) {
                        onAddToQueue()
                    }
                    false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Right swipe -> Remove from queue
                    if (onRemoveFromQueue != null) {
                        onRemoveFromQueue()
                    }
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = onAddToQueue != null && !isDragging,
        enableDismissFromStartToEnd = onRemoveFromQueue != null && !isDragging,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isRightSwipe = direction == SwipeToDismissBoxValue.StartToEnd
            val isLeftSwipe = direction == SwipeToDismissBoxValue.EndToStart

            val backgroundColor = when {
                isLeftSwipe -> SpotifyGreen.copy(alpha = 0.35f)
                isRightSwipe -> SpotifyRed.copy(alpha = 0.35f)
                else -> Color.Transparent
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(backgroundColor)
                    .padding(horizontal = 18.dp),
                contentAlignment = if (isRightSwipe) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (isRightSwipe) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remove",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Remove",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (isLeftSwipe) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Add to queue",
                            style = MaterialTheme.typography.labelMedium,
                            color = SpotifyGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = "Add to queue",
                            tint = SpotifyGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 10f else 1f)
                    .graphicsLayer {
                        translationY = dragOffsetY
                        if (isDragging) {
                            scaleX = 1.02f
                            scaleY = 1.02f
                            shadowElevation = 8f
                        }
                    }
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isDragging) SpotifyElevated else SpotifyBlack)
                    .clickable { onPlayNow() }
                    .padding(vertical = 8.dp)
            ) {
                // Album cover (starts cleanly at left, no circles beside it)
                SongCover(
                    imageUrl = song.coverUrl,
                    size = 44.dp,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        ),
                        color = SpotifyTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = SpotifyTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isLoopedTrack) {
                            Text(
                                text = " • Loop",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = SpotifyGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Spotify three-line drag handle on right with drag-to-reorder gesture
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .then(
                                if (onMove != null) {
                                    Modifier
                                        .pointerInput(Unit) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    isDragging = true
                                                    dragOffsetY = 0f
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetY += dragAmount.y
                                                    val shift = (dragOffsetY / rowHeightPx).toInt()
                                                    if (shift != 0) {
                                                        val target = (index + shift).coerceIn(0, totalCount - 1)
                                                        if (target != index) {
                                                            onMove(index, target)
                                                            dragOffsetY -= shift * rowHeightPx
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    isDragging = false
                                                    dragOffsetY = 0f
                                                },
                                                onDragCancel = {
                                                    isDragging = false
                                                    dragOffsetY = 0f
                                                }
                                            )
                                        }
                                        .clickable {
                                            showReorderMenu = true
                                        }
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = if (isDragging) SpotifyGreen else SpotifyTextSubtle,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Dropdown menu for quick accessibility reordering
                    if (onMove != null) {
                        DropdownMenu(
                            expanded = showReorderMenu,
                            onDismissRequest = { showReorderMenu = false },
                            modifier = Modifier.background(SpotifyElevated)
                        ) {
                            if (index > 0) {
                                DropdownMenuItem(
                                    text = { Text("Move to top", color = SpotifyTextPrimary) },
                                    leadingIcon = {
                                        Icon(Icons.Default.VerticalAlignTop, contentDescription = null, tint = SpotifyTextSecondary)
                                    },
                                    onClick = {
                                        onMove(index, 0)
                                        showReorderMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Move up", color = SpotifyTextPrimary) },
                                    leadingIcon = {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = SpotifyTextSecondary)
                                    },
                                    onClick = {
                                        onMove(index, index - 1)
                                        showReorderMenu = false
                                    }
                                )
                            }
                            if (index < totalCount - 1) {
                                DropdownMenuItem(
                                    text = { Text("Move down", color = SpotifyTextPrimary) },
                                    leadingIcon = {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = SpotifyTextSecondary)
                                    },
                                    onClick = {
                                        onMove(index, index + 1)
                                        showReorderMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Move to bottom", color = SpotifyTextPrimary) },
                                    leadingIcon = {
                                        Icon(Icons.Default.VerticalAlignBottom, contentDescription = null, tint = SpotifyTextSecondary)
                                    },
                                    onClick = {
                                        onMove(index, totalCount - 1)
                                        showReorderMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}
