package com.example.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class LoopMode {
    OFF, ALL, ONE
}

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val upNext: List<Song> = emptyList(),
    val isShuffle: Boolean = false,
    val loopMode: LoopMode = LoopMode.OFF,
    val dominantColor: Color = Color(0xFF1E1B4B),
    val secondaryColor: Color = Color(0xFF0F172A)
)

class AudioController private constructor(private val context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var progressJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val dur = player.duration.coerceAtLeast(0L)
                    _playbackState.value = _playbackState.value.copy(durationMs = dur)
                } else if (state == Player.STATE_ENDED) {
                    handleTrackEnded()
                }
            }
        })
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.coerceAtLeast(0L)
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = pos,
                        durationMs = if (dur > 0) dur else _playbackState.value.durationMs
                    )
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val validIndex = startIndex.coerceIn(0, songs.size - 1)
        _playbackState.value = _playbackState.value.copy(
            queue = songs,
            currentIndex = validIndex
        )
        playSongAtIndex(validIndex)
    }

    fun playSong(song: Song) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        val existingIndex = currentQueue.indexOfFirst { it.id == song.id }
        if (existingIndex != -1) {
            _playbackState.value = _playbackState.value.copy(currentIndex = existingIndex)
            playSongAtIndex(existingIndex)
        } else {
            currentQueue.add(0, song)
            _playbackState.value = _playbackState.value.copy(queue = currentQueue, currentIndex = 0)
            playSongAtIndex(0)
        }
    }

    private fun playSongAtIndex(index: Int) {
        val state = _playbackState.value
        if (index !in state.queue.indices) return

        val song = state.queue[index]
        _playbackState.value = state.copy(
            currentSong = song,
            currentIndex = index,
            currentPositionMs = 0L,
            durationMs = (song.duration * 1000).toLong()
        )

        // Resolve local file if available
        val uri = if (song.localPath != null && File(song.localPath).exists()) {
            Uri.fromFile(File(song.localPath))
        } else {
            Uri.parse(song.audioUrl)
        }

        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        // Extract palette from cover
        extractPalette(song.coverUrl)
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (_playbackState.value.currentSong == null && _playbackState.value.queue.isNotEmpty()) {
                playSongAtIndex(0)
            } else {
                player.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    fun next() {
        val state = _playbackState.value
        // Check Up Next first
        if (state.upNext.isNotEmpty()) {
            val nextSong = state.upNext.first()
            val remainingUpNext = state.upNext.drop(1)
            val newQueue = state.queue.toMutableList()
            newQueue.add(state.currentIndex + 1, nextSong)
            _playbackState.value = state.copy(
                queue = newQueue,
                upNext = remainingUpNext,
                currentIndex = state.currentIndex + 1
            )
            playSongAtIndex(state.currentIndex + 1)
            return
        }

        if (state.queue.isEmpty()) return

        if (state.isShuffle) {
            val randomIdx = state.queue.indices.filter { it != state.currentIndex }.randomOrNull()
                ?: state.currentIndex
            playSongAtIndex(randomIdx)
            return
        }

        val nextIndex = state.currentIndex + 1
        if (nextIndex < state.queue.size) {
            playSongAtIndex(nextIndex)
        } else if (state.loopMode == LoopMode.ALL) {
            playSongAtIndex(0)
        }
    }

    fun previous() {
        val state = _playbackState.value
        if (player.currentPosition > 3000) {
            seekTo(0)
            return
        }
        val prevIndex = state.currentIndex - 1
        if (prevIndex >= 0) {
            playSongAtIndex(prevIndex)
        } else if (state.queue.isNotEmpty()) {
            playSongAtIndex(state.queue.size - 1)
        }
    }

    fun toggleShuffle() {
        _playbackState.value = _playbackState.value.copy(isShuffle = !_playbackState.value.isShuffle)
    }

    fun toggleLoop() {
        val nextMode = when (_playbackState.value.loopMode) {
            LoopMode.OFF -> LoopMode.ALL
            LoopMode.ALL -> LoopMode.ONE
            LoopMode.ONE -> LoopMode.OFF
        }
        _playbackState.value = _playbackState.value.copy(loopMode = nextMode)
    }

    fun addToUpNext(song: Song) {
        val updated = _playbackState.value.upNext + song
        _playbackState.value = _playbackState.value.copy(upNext = updated)
    }

    fun removeFromQueue(index: Int) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (index in currentQueue.indices) {
            currentQueue.removeAt(index)
            var newIdx = _playbackState.value.currentIndex
            if (index < newIdx) {
                newIdx--
            } else if (index == newIdx && currentQueue.isNotEmpty()) {
                newIdx = newIdx.coerceAtMost(currentQueue.size - 1)
                playSongAtIndex(newIdx)
            }
            _playbackState.value = _playbackState.value.copy(queue = currentQueue, currentIndex = newIdx)
        }
    }

    fun reorderQueue(from: Int, to: Int) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (from in currentQueue.indices && to in currentQueue.indices) {
            val moved = currentQueue.removeAt(from)
            currentQueue.add(to, moved)
            val currSong = _playbackState.value.currentSong
            val newIndex = currentQueue.indexOfFirst { it.id == currSong?.id }
            _playbackState.value = _playbackState.value.copy(queue = currentQueue, currentIndex = newIndex)
        }
    }

    private fun handleTrackEnded() {
        when (_playbackState.value.loopMode) {
            LoopMode.ONE -> {
                seekTo(0)
                player.play()
            }
            else -> next()
        }
    }

    fun updateSongLiked(songId: Int, isLiked: Boolean) {
        val state = _playbackState.value
        val updatedQueue = state.queue.map { if (it.id == songId) it.copy(isLiked = isLiked) else it }
        val updatedSong = if (state.currentSong?.id == songId) state.currentSong.copy(isLiked = isLiked) else state.currentSong
        val updatedUpNext = state.upNext.map { if (it.id == songId) it.copy(isLiked = isLiked) else it }

        _playbackState.value = state.copy(
            queue = updatedQueue,
            currentSong = updatedSong,
            upNext = updatedUpNext
        )
    }

    fun resetForLogout() {
        player.stop()
        player.clearMediaItems()
        _playbackState.value = PlaybackState()
    }

    private fun extractPalette(imageUrl: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .allowHardware(false)
                    .build()
                val result = (loader.execute(request) as? SuccessResult)?.drawable
                val bitmap = (result as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    val dominantRgb = palette.getDominantColor(0xFF1E1B4B.toInt())
                    val secondaryRgb = palette.getDarkMutedColor(0xFF0F172A.toInt())
                    withContext(Dispatchers.Main) {
                        _playbackState.value = _playbackState.value.copy(
                            dominantColor = Color(dominantRgb),
                            secondaryColor = Color(secondaryRgb)
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    companion object {
        @Volatile
        private var instance: AudioController? = null

        fun getInstance(context: Context): AudioController {
            return instance ?: synchronized(this) {
                instance ?: AudioController(context.applicationContext).also { instance = it }
            }
        }
    }
}
