package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.Color
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
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

@OptIn(UnstableApi::class)
@SuppressLint("StaticFieldLeak")
class AudioController private constructor(private val context: Context) {
    val player: ExoPlayer = run {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }
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

            override fun onPlayerError(error: PlaybackException) {
                Log.e("AudioController", "Playback error: ${error.errorCodeName} (${error.errorCode}): ${error.message}")
                _playbackState.value = _playbackState.value.copy(isPlaying = false)
                scope.launch {
                    val songTitle = _playbackState.value.currentSong?.title ?: "track"
                    Toast.makeText(context, "Unable to stream \"$songTitle\" (source error)", Toast.LENGTH_SHORT).show()
                    // If queue has more songs, attempt playing next track
                    val state = _playbackState.value
                    if (state.queue.size > 1 && state.currentIndex < state.queue.size - 1) {
                        delay(1200)
                        next()
                    }
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

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(Uri.parse(song.coverUrl))
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(song.id.toString())
            .setMediaMetadata(mediaMetadata)
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        // Ensure playback service is started
        try {
            val serviceIntent = Intent(context, MusicPlaybackService::class.java)
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Log.w("AudioController", "Could not start playback service: ${e.message}")
        }

        // Extract palette from cover
        extractPalette(song.coverUrl)

        // Record listening history
        recordHistory(song)
    }

    private var lastRecordedSongId: Int? = null
    private var lastRecordedTimeMs: Long = 0L

    private fun recordHistory(song: Song) {
        val now = System.currentTimeMillis()
        if (song.id == lastRecordedSongId && (now - lastRecordedTimeMs) < 10000) {
            return
        }
        lastRecordedSongId = song.id
        lastRecordedTimeMs = now

        scope.launch(Dispatchers.IO) {
            try {
                val db = com.example.data.local.AppDatabase.getInstance(context)
                db.insertListeningHistory(
                    com.example.data.local.ListeningHistoryEntity(
                        songId = song.id,
                        title = song.title,
                        artist = song.artist,
                        audioUrl = song.audioUrl,
                        coverUrl = song.coverUrl,
                        duration = song.duration,
                        playedAt = now
                    )
                )
            } catch (e: Exception) {
                Log.w("AudioController", "Failed to record history", e)
            }
        }
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

    fun pause() {
        player.pause()
    }

    fun play() {
        player.play()
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
        if (_stopAtEndOfTrack.value) {
            _stopAtEndOfTrack.value = false
            _isSleepTimerActive.value = false
            _sleepTimerRemainingSeconds.value = null
            sleepTimerJob?.cancel()
            pause()
            seekTo(0)
            return
        }
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
                    val swatch = palette.vibrantSwatch
                        ?: palette.dominantSwatch
                        ?: palette.darkVibrantSwatch
                        ?: palette.mutedSwatch
                        ?: palette.lightVibrantSwatch
                    val dominantRgb = swatch?.rgb ?: palette.getDominantColor(0xFF6B2132.toInt())
                    val secondaryRgb = palette.getDarkMutedColor(
                        palette.getMutedColor(0xFF1B0B10.toInt())
                    )
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

    private var sleepTimerJob: Job? = null
    private val _sleepTimerRemainingSeconds = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Int?> = _sleepTimerRemainingSeconds.asStateFlow()

    private val _isSleepTimerActive = MutableStateFlow(false)
    val isSleepTimerActive: StateFlow<Boolean> = _isSleepTimerActive.asStateFlow()

    private val _stopAtEndOfTrack = MutableStateFlow(false)
    val stopAtEndOfTrack: StateFlow<Boolean> = _stopAtEndOfTrack.asStateFlow()

    fun setSleepTimer(minutes: Int, endOfTrack: Boolean = false) {
        sleepTimerJob?.cancel()
        _stopAtEndOfTrack.value = endOfTrack

        if (endOfTrack) {
            _isSleepTimerActive.value = true
            val remainingMs = (player.duration - player.currentPosition).coerceAtLeast(0L)
            _sleepTimerRemainingSeconds.value = (remainingMs / 1000).toInt()
            sleepTimerJob = scope.launch {
                while (isActive && _stopAtEndOfTrack.value) {
                    val rem = ((player.duration - player.currentPosition).coerceAtLeast(0L) / 1000).toInt()
                    _sleepTimerRemainingSeconds.value = rem
                    if (rem <= 0 && !player.isPlaying) {
                        break
                    }
                    delay(500)
                }
            }
            return
        }

        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }

        _isSleepTimerActive.value = true
        sleepTimerJob = scope.launch {
            var remaining = minutes * 60
            while (remaining > 0 && isActive) {
                _sleepTimerRemainingSeconds.value = remaining
                // Gentle audio fade-out during final 4 seconds
                if (remaining <= 4 && player.isPlaying) {
                    val factor = (remaining.toFloat() / 5f).coerceIn(0.1f, 1f)
                    player.volume = factor
                }
                delay(1000)
                remaining--
            }
            if (isActive) {
                _sleepTimerRemainingSeconds.value = null
                _isSleepTimerActive.value = false
                pause()
                player.volume = 1.0f
            }
        }
    }

    fun addMinutesToSleepTimer(extraMinutes: Int) {
        if (_stopAtEndOfTrack.value) {
            setSleepTimer(extraMinutes, false)
            return
        }
        val currentRemaining = _sleepTimerRemainingSeconds.value ?: 0
        val newTotalMinutes = ((currentRemaining + (extraMinutes * 60)) / 60).coerceAtLeast(1)
        setSleepTimer(newTotalMinutes, false)
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _stopAtEndOfTrack.value = false
        _isSleepTimerActive.value = false
        _sleepTimerRemainingSeconds.value = null
        player.volume = 1.0f
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
