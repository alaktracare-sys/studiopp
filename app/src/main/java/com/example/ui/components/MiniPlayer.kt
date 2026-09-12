package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.PlaybackState
import kotlin.math.sin

@Composable
fun MiniPlayer(
    playbackState: PlaybackState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onLikeToggle: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = playbackState.currentSong ?: return

    var totalDrag by remember { mutableFloatStateOf(0f) }

    // Wave animation phase
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val progressFraction = remember(playbackState.currentPositionMs, playbackState.durationMs) {
        if (playbackState.durationMs > 0) {
            (playbackState.currentPositionMs.toFloat() / playbackState.durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        playbackState.dominantColor.copy(alpha = 0.9f),
                        playbackState.secondaryColor.copy(alpha = 0.95f),
                        Color(0xFF1E1E1E)
                    )
                )
            )
            .draggable(
                state = rememberDraggableState { delta ->
                    totalDrag += delta
                },
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    if (totalDrag < -100f) {
                        onNext()
                    } else if (totalDrag > 100f) {
                        onPrevious()
                    }
                    totalDrag = 0f
                }
            )
            .clickable(onClick = onExpand)
    ) {
        // Liquid Wave Progress at the bottom
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.BottomCenter)
        ) {
            val width = size.width
            val height = size.height

            // Background track
            drawRect(
                color = Color.White.copy(alpha = 0.2f),
                size = size
            )

            // Active progress wave
            val progressWidth = width * progressFraction
            if (progressWidth > 0) {
                val path = Path()
                path.moveTo(0f, height)
                path.lineTo(0f, 0f)

                val points = 20
                for (i in 0..points) {
                    val x = (progressWidth / points) * i
                    val y = if (playbackState.isPlaying) {
                        (sin(wavePhase + (i * 0.4f)) * 2f).toFloat()
                    } else {
                        0f
                    }
                    path.lineTo(x, y)
                }

                path.lineTo(progressWidth, height)
                path.close()

                drawPath(
                    path = path,
                    color = Color(0xFF1DB954)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp)
        ) {
            SongCover(
                imageUrl = song.coverUrl,
                size = 46.dp,
                cornerRadius = 8.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = song.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFFB3B3B3),
                    fontSize = 12.sp
                )
            }

            IconButton(
                onClick = onLikeToggle,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (song.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (song.isLiked) Color(0xFF1DB954) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
