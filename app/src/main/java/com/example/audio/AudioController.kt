package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import android.os.Handler
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener
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

enum class RepeatMode {
    OFF, CONTEXT, TRACK
}

enum class ContextType {
    PLAYLIST, ALBUM, ARTIST, SEARCH
}

data class PlaybackContext(
    val uri: String,
    val type: ContextType,
    val name: String,
    val trackIds: List<Int>,
    val tracks: List<Song> = emptyList()
)

enum class AdvanceReason {
    ENDED, SKIP
}

data class PlaybackState(
    val context: PlaybackContext? = null,
    val contextOrder: List<Song> = emptyList(),    // ACTIVE ordering of context.trackIds
    val contextIndex: Int = -1,                  // pointer into contextOrder
    val userQueue: List<Song> = emptyList(),       // explicitly queued songs, FIFO, destructive
    val history: List<Song> = emptyList(),         // context plays only
    val recentPlayedSongIds: List<Int> = emptyList(), // last 20 actually played song IDs
    val currentSong: Song? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val autoplayTracks: List<Song> = emptyList(),
    val dominantColor: Color = Color(0xFF1E1B4B),
    val secondaryColor: Color = Color(0xFF0F172A)
) {
    val elapsedSec: Long get() = currentPositionMs / 1000
    val currentTrackId: String? get() = currentSong?.id?.toString()

    // Backward-compatibility properties
    val isShuffle: Boolean get() = shuffle
    val loopMode: LoopMode get() = when (repeat) {
        RepeatMode.OFF -> LoopMode.OFF
        RepeatMode.CONTEXT -> LoopMode.ALL
        RepeatMode.TRACK -> LoopMode.ONE
    }
    val queue: List<Song> get() = contextOrder
    val currentIndex: Int get() = contextIndex
    val originalQueue: List<Song> get() = context?.tracks ?: emptyList()
    val playbackSource: String get() = context?.name ?: "Now Playing"
    val upNext: List<Song> get() = userQueue
}

@OptIn(UnstableApi::class)
@SuppressLint("StaticFieldLeak")
class AudioController private constructor(private val context: Context) {
    val player: ExoPlayer by lazy { createPlayer() }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    val recentPlayedSongIds: List<Int> get() = _playbackState.value.recentPlayedSongIds

    private var progressJob: Job? = null

    init {
        // Deferred initialization: ExoPlayer is created lazily when playback is requested
    }

    private fun createPlayer(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildVideoRenderers(
                context: Context,
                extensionRendererMode: Int,
                mediaCodecSelector: MediaCodecSelector,
                enableDecoderFallback: Boolean,
                eventHandler: Handler,
                eventListener: VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long,
                out: ArrayList<Renderer>
            ) {
                // Audio-only player: skip video codecs to prevent C2 system resource errors
            }

            override fun buildCameraMotionRenderers(
                context: Context,
                extensionRendererMode: Int,
                out: ArrayList<Renderer>
            ) {
                // Audio-only player: skip camera motion renderers
            }

            override fun buildImageRenderers(
                out: ArrayList<Renderer>
            ) {
                // Audio-only player: skip image renderers
            }

            override fun buildMiscellaneousRenderers(
                context: Context,
                eventHandler: Handler,
                extensionRendererMode: Int,
                out: ArrayList<Renderer>
            ) {
                // Audio-only player: skip misc renderers
            }
        }.apply {
            setEnableDecoderFallback(false)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val exo = ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setLoadControl(loadControl)
            .build()

        exo.addListener(object : Player.Listener {
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
                    val dur = exo.duration.coerceAtLeast(0L)
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

        return exo
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

    fun playContext(
        context: PlaybackContext,
        startIndex: Int = 0,
        autoShuffle: Boolean = false
    ) {
        val songs = context.tracks
        if (songs.isEmpty()) return
        val validIndex = startIndex.coerceIn(0, songs.size - 1)
        val shouldShuffle = autoShuffle || _playbackState.value.shuffle

        val activeOrder = if (shouldShuffle) {
            val startSong = songs[validIndex]
            val otherSongs = songs.filterIndexed { i, _ -> i != validIndex }
            val shuffledOthers = ShuffleUtils.artistSpreadShuffle(otherSongs)
            listOf(startSong) + shuffledOthers
        } else {
            songs
        }
        val activeIndex = if (shouldShuffle) 0 else validIndex

        // Starting a new context must NOT clear userQueue — queued tracks survive a context change (Spotify behavior)
        var updatedState = _playbackState.value.copy(
            context = context,
            contextOrder = activeOrder,
            contextIndex = activeIndex,
            shuffle = shouldShuffle,
            autoplayTracks = emptyList()
        )
        updatedState = ensureContextBuffer(updatedState)
        _playbackState.value = updatedState
        playSongAtIndex(updatedState.contextIndex)
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0, source: String = "Alaktra Stream") {
        if (songs.isEmpty()) return
        val contextType = when {
            source.startsWith("Album", ignoreCase = true) -> ContextType.ALBUM
            source.startsWith("Artist", ignoreCase = true) -> ContextType.ARTIST
            source.startsWith("Search", ignoreCase = true) -> ContextType.SEARCH
            else -> ContextType.PLAYLIST
        }
        val ctx = PlaybackContext(
            uri = "alaktra:${contextType.name.lowercase()}:${source.replace(" ", "_").lowercase()}",
            type = contextType,
            name = source,
            trackIds = songs.map { it.id },
            tracks = songs
        )
        playContext(ctx, startIndex = startIndex)
    }

    fun playSong(song: Song, source: String = "Now Playing") {
        val currentOrder = _playbackState.value.contextOrder
        val existingIndex = currentOrder.indexOfFirst { it.id == song.id }
        if (existingIndex != -1 && _playbackState.value.context != null) {
            _playbackState.value = _playbackState.value.copy(
                contextIndex = existingIndex
            )
            playSongAtIndex(existingIndex)
        } else {
            val ctx = PlaybackContext(
                uri = "alaktra:track:${song.id}",
                type = ContextType.SEARCH,
                name = source,
                trackIds = listOf(song.id),
                tracks = listOf(song)
            )
            playContext(ctx, startIndex = 0)
        }
    }

    private fun playSongDirectly(song: Song, pushToHistory: Boolean = false) {
        val state = _playbackState.value
        val updatedHistory = if (pushToHistory && state.currentSong != null) {
            val wasContext = state.contextOrder.getOrNull(state.contextIndex)?.id == state.currentSong.id
            if (wasContext) state.history + state.currentSong else state.history
        } else {
            state.history
        }
        val updatedRecentPlayed = addToRecentPlayedHistory(song.id, state.recentPlayedSongIds)

        _playbackState.value = state.copy(
            currentSong = song,
            currentPositionMs = 0L,
            durationMs = (song.duration * 1000).toLong(),
            history = updatedHistory,
            recentPlayedSongIds = updatedRecentPlayed
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

        ensureServiceStarted()

        // Extract palette from cover
        extractPalette(song.coverUrl)

        // Record listening history
        recordHistory(song)
    }

    private fun playSongAtIndex(index: Int) {
        val state = _playbackState.value
        if (index !in state.contextOrder.indices) return

        val song = state.contextOrder[index]
        val updatedRecentPlayed = addToRecentPlayedHistory(song.id, state.recentPlayedSongIds)
        _playbackState.value = state.copy(
            currentSong = song,
            contextIndex = index,
            currentPositionMs = 0L,
            durationMs = (song.duration * 1000).toLong(),
            recentPlayedSongIds = updatedRecentPlayed
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

        ensureServiceStarted()

        // Extract palette from cover
        extractPalette(song.coverUrl)

        // Record listening history
        recordHistory(song)
    }

    fun ensureServiceStarted() {
        try {
            val serviceIntent = Intent(context, MusicPlaybackService::class.java)
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Log.w("AudioController", "Could not start playback service", e)
        }
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
            if (_playbackState.value.currentSong == null && _playbackState.value.contextOrder.isNotEmpty()) {
                playSongAtIndex(0)
            } else {
                ensureServiceStarted()
                player.play()
            }
        }
    }

    fun pause() {
        player.pause()
    }

    fun play() {
        ensureServiceStarted()
        player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    /**
     * Advance ladder: strict top-down execution with early returns (Steps 1-4).
     */
    fun advanceTrack(reason: AdvanceReason) {
        val state = _playbackState.value

        // 1. repeat === 'track' && reason === 'ended'
        //    -> reset elapsedSec to 0, keep currentTrackId, return.
        //    This check sits ABOVE the user queue on purpose: a queued song must not cut
        //    into a looping track. An explicit skip press still moves on.
        if (state.repeat == RepeatMode.TRACK && reason == AdvanceReason.ENDED) {
            seekTo(0)
            player.play()
            return
        }

        // 2. userQueue.length > 0
        //    -> shift the head, set it as currentTrackId, return.
        //    contextIndex does NOT move. Do not push the outgoing track to history if it
        //    itself came from the user queue.
        if (state.userQueue.isNotEmpty()) {
            val nextSong = state.userQueue.first()
            val remainingUserQueue = state.userQueue.drop(1)
            _playbackState.value = state.copy(userQueue = remainingUserQueue)
            playSongDirectly(nextSong, pushToHistory = false)
            return
        }

        // 3. contextIndex + 1 < contextOrder.length
        //    -> push outgoing track to history, increment contextIndex, play
        //       contextOrder[contextIndex], return.
        val nextIndex = state.contextIndex + 1
        if (nextIndex < state.contextOrder.size) {
            val outgoingTrack = state.currentSong
            val wasContext = outgoingTrack != null && state.contextOrder.getOrNull(state.contextIndex)?.id == outgoingTrack.id
            val updatedHistory = if (outgoingTrack != null && wasContext) {
                state.history + outgoingTrack
            } else {
                state.history
            }
            var updatedState = state.copy(
                contextIndex = nextIndex,
                history = updatedHistory
            )
            updatedState = ensureContextBuffer(updatedState)
            _playbackState.value = updatedState
            playSongAtIndex(updatedState.contextIndex)
            return
        }

        // 4. context exhausted:
        //    - repeat === 'context' -> if shuffle is on, regenerate contextOrder with a
        //      FRESH seed (never replay the same random order), set contextIndex = 0,
        //      play it.
        //    - otherwise -> populate autoplayTracks and play the first one.
        if (state.repeat == RepeatMode.CONTEXT && state.contextOrder.isNotEmpty()) {
            val outgoingTrack = state.currentSong
            val wasContext = outgoingTrack != null && state.contextOrder.getOrNull(state.contextIndex)?.id == outgoingTrack.id
            val updatedHistory = if (outgoingTrack != null && wasContext) {
                state.history + outgoingTrack
            } else {
                state.history
            }
            val naturalTracks = state.context?.tracks ?: state.contextOrder
            val newOrder = if (state.shuffle) {
                val shuffled = ShuffleUtils.artistSpreadShuffle(naturalTracks)
                val eligibleIndex = shuffled.indexOfFirst { it.id !in state.recentPlayedSongIds }
                if (eligibleIndex > 0) {
                    val eligibleTrack = shuffled[eligibleIndex]
                    listOf(eligibleTrack) + shuffled.filterIndexed { i, _ -> i != eligibleIndex }
                } else {
                    shuffled
                }
            } else {
                naturalTracks
            }
            var updatedState = state.copy(
                contextOrder = newOrder,
                contextIndex = 0,
                history = updatedHistory
            )
            updatedState = ensureContextBuffer(updatedState)
            _playbackState.value = updatedState
            playSongAtIndex(updatedState.contextIndex)
            return
        } else {
            // While repeat !== 'off', autoplay never triggers
            if (state.repeat == RepeatMode.OFF) {
                if (state.autoplayTracks.isNotEmpty()) {
                    val nextAutoplay = state.autoplayTracks.first()
                    val remainingAutoplay = state.autoplayTracks.drop(1)
                    _playbackState.value = state.copy(autoplayTracks = remainingAutoplay)
                    playSongDirectly(nextAutoplay, pushToHistory = false)
                    return
                } else {
                    val candidateTracks = state.context?.tracks ?: emptyList()
                    if (candidateTracks.isNotEmpty()) {
                        val generated = candidateTracks.shuffled().take(5)
                        val first = generated.first()
                        val remaining = generated.drop(1)
                        _playbackState.value = state.copy(autoplayTracks = remaining)
                        playSongDirectly(first, pushToHistory = false)
                        return
                    }
                }
            }
        }
    }

    fun next() {
        advanceTrack(AdvanceReason.SKIP)
    }

    /**
     * previousTrack(): if elapsedSec > 3, restart current track. Otherwise pop
     * history. Never return to a consumed userQueue track.
     */
    fun previousTrack() {
        val state = _playbackState.value
        val elapsedSec = state.currentPositionMs / 1000
        if (elapsedSec > 3) {
            seekTo(0)
            return
        }

        if (state.history.isNotEmpty()) {
            val lastPlayed = state.history.last()
            val remainingHistory = state.history.dropLast(1)
            val orderIndex = state.contextOrder.indexOfFirst { it.id == lastPlayed.id }
            val newIndex = if (orderIndex != -1) orderIndex else state.contextIndex
            _playbackState.value = state.copy(
                history = remainingHistory,
                contextIndex = newIndex
            )
            playSongDirectly(lastPlayed, pushToHistory = false)
        } else if (state.contextIndex > 0) {
            val prevIndex = state.contextIndex - 1
            _playbackState.value = state.copy(contextIndex = prevIndex)
            playSongAtIndex(prevIndex)
        } else {
            seekTo(0)
        }
    }

    fun previous() {
        previousTrack()
    }

    /**
     * Shuffle is a property of the CONTEXT layer only. It must never touch userQueue.
     * toggleShuffle(true):
     *   - generate a shuffled ordering of context.trackIds
     *   - move currentTrackId to position 0 of that ordering
     *   - set contextIndex = 0
     *   - the currently playing track must NOT change when shuffle is toggled
     * toggleShuffle(false):
     *   - contextOrder = [...context.trackIds]
     *   - contextIndex = contextOrder.indexOf(currentTrackId)
     *   - position preserved; only the neighbours change
     */
    fun ensureContextBuffer(state: PlaybackState): PlaybackState {
        val contextTracks = state.context?.tracks ?: state.contextOrder
        if (contextTracks.isEmpty()) return state

        // When repeat context is active, maintain at least 30 upcoming tracks ahead of contextIndex
        if (state.repeat != RepeatMode.CONTEXT) {
            return state
        }

        var order = state.contextOrder.toMutableList()
        var cIndex = state.contextIndex.coerceAtLeast(0)

        if (order.isEmpty()) {
            val seed = if (state.shuffle) {
                ShuffleUtils.artistSpreadShuffle(contextTracks)
            } else {
                contextTracks
            }
            order.addAll(seed)
            cIndex = 0
        }

        val targetUpcoming = 30
        var upcoming = (order.size - 1) - cIndex

        while (upcoming < targetUpcoming) {
            if (state.shuffle) {
                // Loop All + Shuffle = continuous random stream
                // Pick a random song from contextTracks, avoiding immediate back-to-back duplicate if context has > 1 song
                val lastId = order.lastOrNull()?.id
                val candidates = if (contextTracks.size > 1 && lastId != null) {
                    val filtered = contextTracks.filter { it.id != lastId }
                    if (filtered.isNotEmpty()) filtered else contextTracks
                } else {
                    contextTracks
                }
                val selectedSong = selectShuffleCandidate(candidates, state.recentPlayedSongIds)
                order.add(selectedSong)
                upcoming += 1
            } else {
                // Loop All + Shuffle OFF = repeat the normal playlist order
                order.addAll(contextTracks)
                upcoming += contextTracks.size
            }
        }

        // Keep order bounded so memory does not grow unbounded over long sessions
        if (cIndex > 40) {
            val trimCount = cIndex - 10
            order = order.drop(trimCount).toMutableList()
            cIndex = 10
        }

        return state.copy(
            contextOrder = order,
            contextIndex = cIndex
        )
    }

    fun ensureBuffer() {
        val updated = ensureContextBuffer(_playbackState.value)
        if (updated != _playbackState.value) {
            _playbackState.value = updated
        }
    }

    fun toggleShuffle(forceEnable: Boolean? = null) {
        val state = _playbackState.value
        val newShuffle = forceEnable ?: !state.shuffle
        val naturalTracks = state.context?.tracks ?: state.contextOrder

        if (naturalTracks.isEmpty()) return

        val currentSong = state.currentSong
        var newState = if (newShuffle) {
            if (currentSong != null && naturalTracks.isNotEmpty()) {
                val otherSongs = naturalTracks.filter { it.id != currentSong.id }
                val shuffledOthers = ShuffleUtils.artistSpreadShuffle(otherSongs)
                val newOrder = listOf(currentSong) + shuffledOthers
                state.copy(
                    shuffle = true,
                    contextOrder = newOrder,
                    contextIndex = 0
                )
            } else {
                val shuffled = ShuffleUtils.artistSpreadShuffle(naturalTracks)
                state.copy(
                    shuffle = true,
                    contextOrder = shuffled,
                    contextIndex = 0
                )
            }
        } else {
            val restoredIndex = if (currentSong != null) {
                val idx = naturalTracks.indexOfFirst { it.id == currentSong.id }
                if (idx != -1) idx else 0
            } else {
                0
            }
            state.copy(
                shuffle = false,
                contextOrder = naturalTracks,
                contextIndex = restoredIndex
            )
        }

        newState = ensureContextBuffer(newState)
        _playbackState.value = newState
    }

    /**
     * Repeat cycle: OFF -> CONTEXT -> TRACK -> OFF
     */
    fun cycleRepeatMode(): RepeatMode {
        val nextMode = when (_playbackState.value.repeat) {
            RepeatMode.OFF -> RepeatMode.CONTEXT
            RepeatMode.CONTEXT -> RepeatMode.TRACK
            RepeatMode.TRACK -> RepeatMode.OFF
        }
        setRepeatMode(nextMode)
        return nextMode
    }

    fun setRepeatMode(mode: RepeatMode) {
        var state = _playbackState.value.copy(repeat = mode)
        if (mode == RepeatMode.CONTEXT) {
            state = ensureContextBuffer(state)
        } else if (mode == RepeatMode.OFF) {
            val naturalTracks = state.context?.tracks ?: emptyList()
            if (naturalTracks.isNotEmpty()) {
                val currentIdx = state.contextIndex
                val naturalSize = naturalTracks.size
                val maxLimit = currentIdx + 1 + naturalSize
                if (state.contextOrder.size > maxLimit) {
                    state = state.copy(contextOrder = state.contextOrder.take(maxLimit))
                }
            }
        }
        _playbackState.value = state
    }

    fun toggleLoop() {
        cycleRepeatMode()
    }

    /** Add song to the end of user queue ("Next in Queue") - Destructive FIFO */
    fun addToQueue(song: Song) {
        val updated = _playbackState.value.userQueue + song
        _playbackState.value = _playbackState.value.copy(userQueue = updated)
    }

    /** Add song immediately next in line ("Play Next") */
    fun playNext(song: Song) {
        val updated = listOf(song) + _playbackState.value.userQueue
        _playbackState.value = _playbackState.value.copy(userQueue = updated)
    }

    /** Alias for backward compatibility */
    fun addToUpNext(song: Song) {
        addToQueue(song)
    }

    /** Promote a track from context layer into userQueue */
    fun promoteToUserQueue(song: Song) {
        // Pushes track into userQueue without modifying contextOrder or removing from context
        _playbackState.value = _playbackState.value.copy(
            userQueue = _playbackState.value.userQueue + song
        )
    }

    /** Promote a track from context layer into userQueue at specific position */
    fun promoteToUserQueueAt(song: Song, targetIndex: Int) {
        val current = _playbackState.value.userQueue.toMutableList()
        val clamped = targetIndex.coerceIn(0, current.size)
        current.add(clamped, song)
        _playbackState.value = _playbackState.value.copy(userQueue = current)
    }

    /** Remove a song from the user-enqueued layer */
    fun removeFromUserQueue(index: Int) {
        val current = _playbackState.value.userQueue.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _playbackState.value = _playbackState.value.copy(userQueue = current)
        }
    }

    /** Clear all songs from the user-enqueued layer (scoped to user queue only) */
    fun clearUserQueue() {
        _playbackState.value = _playbackState.value.copy(userQueue = emptyList())
    }

    /** Reorder songs inside the user-enqueued layer */
    fun reorderUserQueue(from: Int, to: Int) {
        val current = _playbackState.value.userQueue.toMutableList()
        if (from in current.indices && to in current.indices) {
            val moved = current.removeAt(from)
            current.add(to, moved)
            _playbackState.value = _playbackState.value.copy(userQueue = current)
        }
    }

    /** Immediately play a song from the user-enqueued layer and remove it */
    fun playUserQueueItem(index: Int) {
        val current = _playbackState.value.userQueue.toMutableList()
        if (index in current.indices) {
            val songToPlay = current.removeAt(index)
            _playbackState.value = _playbackState.value.copy(userQueue = current)
            playSongDirectly(songToPlay, pushToHistory = false)
        }
    }

    /** Play a song directly from the context queue */
    fun playContextQueueItem(index: Int) {
        if (index in _playbackState.value.contextOrder.indices) {
            val outgoingTrack = _playbackState.value.currentSong
            val wasContext = outgoingTrack != null && _playbackState.value.contextOrder.getOrNull(_playbackState.value.contextIndex)?.id == outgoingTrack.id
            val updatedHistory = if (outgoingTrack != null && wasContext) {
                _playbackState.value.history + outgoingTrack
            } else {
                _playbackState.value.history
            }
            var updatedState = _playbackState.value.copy(
                contextIndex = index,
                history = updatedHistory
            )
            updatedState = ensureContextBuffer(updatedState)
            _playbackState.value = updatedState
            playSongAtIndex(updatedState.contextIndex)
        }
    }

    /** Remove a song from the base/context queue */
    fun removeFromQueue(index: Int) {
        val currentOrder = _playbackState.value.contextOrder.toMutableList()
        if (index in currentOrder.indices) {
            val removedSong = currentOrder.removeAt(index)
            val updatedNatural = _playbackState.value.context?.tracks?.filter { it.id != removedSong.id } ?: currentOrder
            var newIdx = _playbackState.value.contextIndex
            if (index < newIdx) {
                newIdx--
            } else if (index == newIdx && currentOrder.isNotEmpty()) {
                newIdx = newIdx.coerceAtMost(currentOrder.size - 1)
                playSongAtIndex(newIdx)
            }
            val updatedContext = _playbackState.value.context?.copy(
                tracks = updatedNatural,
                trackIds = updatedNatural.map { it.id }
            )
            var updatedState = _playbackState.value.copy(
                context = updatedContext,
                contextOrder = currentOrder,
                contextIndex = newIdx
            )
            updatedState = ensureContextBuffer(updatedState)
            _playbackState.value = updatedState
        }
    }

    fun reorderQueue(from: Int, to: Int) {
        val currentOrder = _playbackState.value.contextOrder.toMutableList()
        if (from in currentOrder.indices && to in currentOrder.indices) {
            val moved = currentOrder.removeAt(from)
            currentOrder.add(to, moved)
            val currSong = _playbackState.value.currentSong
            val newIndex = if (currSong != null) currentOrder.indexOfFirst { it.id == currSong.id } else _playbackState.value.contextIndex
            _playbackState.value = _playbackState.value.copy(
                contextOrder = currentOrder,
                contextIndex = if (newIndex != -1) newIndex else _playbackState.value.contextIndex
            )
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
        advanceTrack(AdvanceReason.ENDED)
    }

    fun updateSongLiked(songId: Int, isLiked: Boolean) {
        val state = _playbackState.value
        val updatedOrder = state.contextOrder.map { if (it.id == songId) it.copy(isLiked = isLiked) else it }
        val updatedSong = if (state.currentSong?.id == songId) state.currentSong.copy(isLiked = isLiked) else state.currentSong
        val updatedUserQueue = state.userQueue.map { if (it.id == songId) it.copy(isLiked = isLiked) else it }
        val updatedContext = state.context?.copy(
            tracks = state.context.tracks.map { if (it.id == songId) it.copy(isLiked = isLiked) else it }
        )

        _playbackState.value = state.copy(
            context = updatedContext,
            contextOrder = updatedOrder,
            currentSong = updatedSong,
            userQueue = updatedUserQueue
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
                    val stream = java.io.ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    val artworkBytes = stream.toByteArray()

                    withContext(Dispatchers.Main) {
                        _playbackState.value = _playbackState.value.copy(
                            dominantColor = Color(dominantRgb),
                            secondaryColor = Color(secondaryRgb)
                        )
                        try {
                            val currentItem = player.currentMediaItem
                            if (currentItem != null) {
                                val currentMetadata = currentItem.mediaMetadata
                                val updatedMetadata = currentMetadata.buildUpon()
                                    .setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                    .build()
                                val updatedItem = currentItem.buildUpon()
                                    .setMediaMetadata(updatedMetadata)
                                    .build()
                                player.replaceMediaItem(player.currentMediaItemIndex, updatedItem)
                            }
                        } catch (e: Exception) {
                            Log.w("AudioController", "Could not update media item artwork", e)
                        }
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

        /**
         * Maintain at most the last 20 played song IDs in playback sequence.
         * Consecutive duplicate plays (e.g. Loop One or immediate repeat) are not duplicated.
         */
        fun addToRecentPlayedHistory(songId: Int, currentHistory: List<Int>): List<Int> {
            if (currentHistory.lastOrNull() == songId) {
                return currentHistory
            }
            val updated = currentHistory + songId
            return if (updated.size > 20) {
                updated.takeLast(20)
            } else {
                updated
            }
        }

        /**
         * Automatic candidate selection for Shuffle = ON, Loop = ALL:
         * 1. Existing shuffle logic selects candidate.
         * 2. Candidate is checked against the last 20 actually played songs.
         * 3. If in recent 20, candidate is rejected and an eligible alternative is selected.
         * 4. If all candidates are in recent 20 (small libraries), fallback gracefully to avoid infinite loops.
         */
        fun selectShuffleCandidate(
            candidates: List<Song>,
            recentHistory: List<Int>
        ): Song {
            val candidate = candidates.random()
            return if (candidate.id in recentHistory) {
                val eligibleAlternatives = candidates.filter { it.id !in recentHistory }
                if (eligibleAlternatives.isNotEmpty()) {
                    // Reject candidate and select an eligible alternative
                    eligibleAlternatives.random()
                } else {
                    // Graceful fallback for small library where all candidates exist in recent history
                    candidate
                }
            } else {
                // Accept candidate
                candidate
            }
        }
    }
}
