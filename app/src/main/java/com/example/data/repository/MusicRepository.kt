package com.example.data.repository

import android.content.Context
import com.example.BuildConfig
import com.example.data.DownloadManager
import com.example.data.local.AppDatabase
import com.example.data.preferences.AuthPreferences
import com.example.data.remote.*
import com.example.model.Playlist
import com.example.model.Song
import com.example.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

import com.example.data.local.ListeningHistoryEntity
import kotlinx.coroutines.flow.Flow

class MusicRepository(private val context: Context) {
    val authPreferences = AuthPreferences(context)
    val downloadManager = DownloadManager(context)
    private val appDatabase = AppDatabase.getInstance(context)
    private val songDao = appDatabase.songDao()
    val listeningHistory: Flow<List<ListeningHistoryEntity>> = appDatabase.getAllHistory()

    suspend fun recordSongPlayed(song: Song) {
        appDatabase.insertListeningHistory(
            ListeningHistoryEntity(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                audioUrl = song.audioUrl,
                coverUrl = song.coverUrl,
                duration = song.duration,
                playedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteHistoryItem(id: Long) {
        appDatabase.deleteHistoryItem(id)
    }

    suspend fun clearListeningHistory() {
        appDatabase.clearAllHistory()
    }

    suspend fun seedHistoryIfEmpty(songs: List<Song>) {
        appDatabase.seedSampleHistoryIfEmpty(songs)
    }

    @Volatile
    private var cachedBaseUrl: String = authPreferences.getServerBaseUrl()

    @Volatile
    private var apiInstance: MusicApiService = buildApiService(cachedBaseUrl)

    private val api: MusicApiService
        get() {
            val currentUrl = authPreferences.getServerBaseUrl()
            if (currentUrl != cachedBaseUrl) {
                synchronized(this) {
                    if (currentUrl != cachedBaseUrl) {
                        cachedBaseUrl = currentUrl
                        apiInstance = buildApiService(currentUrl)
                    }
                }
            }
            return apiInstance
        }

    private fun buildApiService(baseUrl: String): MusicApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MusicApiService::class.java)
    }

    fun sanitizeUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        val configuredBase = authPreferences.getServerBaseUrl().trimEnd('/')
        return if (rawUrl.startsWith("http://100.65.126.106:8000")) {
            rawUrl.replace("http://100.65.126.106:8000", configuredBase)
        } else if (rawUrl.startsWith("/")) {
            "$configuredBase$rawUrl"
        } else {
            rawUrl
        }
    }

    private fun getEffectiveUserId(): Int {
        val user = authPreferences.getUser()
        return user?.id ?: 1
    }

    // Default sample songs used when server is initially empty or offline
    private val defaultSongs = listOf(
        Song(
            id = 101,
            title = "Acoustic Melody",
            artist = "SoundHelix",
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            coverUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&auto=format&fit=crop",
            duration = 372.0
        ),
        Song(
            id = 102,
            title = "Microtonal Groove",
            artist = "Sevish",
            audioUrl = "https://commondatastorage.googleapis.com/codeskulptor-demos/DDR_assets/Sevish_-__nbsp_.mp3",
            coverUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500&auto=format&fit=crop",
            duration = 186.0
        ),
        Song(
            id = 103,
            title = "Lepidoptera Suite",
            artist = "Epoq Ambient",
            audioUrl = "https://commondatastorage.googleapis.com/codeskulptor-assets/Epoq-Lepidoptera.ogg",
            coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop",
            duration = 240.0
        ),
        Song(
            id = 104,
            title = "Neon Velocity",
            artist = "Cyber Pulse",
            audioUrl = "https://commondatastorage.googleapis.com/codeskulptor-demos/riceracer_assets/music/race1.ogg",
            coverUrl = "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=500&auto=format&fit=crop",
            duration = 152.0
        ),
        Song(
            id = 105,
            title = "Harmonic Chillout",
            artist = "SoundHelix",
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            coverUrl = "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=500&auto=format&fit=crop",
            duration = 425.0
        )
    )

    // ==========================================
    // AUTHENTICATION
    // ==========================================

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = api.getSongs()
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    suspend fun login(identifier: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val response = api.login(LoginRequest(identifier.trim(), password.trim()))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val user = User(
                    id = body.userId,
                    username = body.username,
                    email = body.email
                )
                authPreferences.saveUser(user.id, user.username, user.email)

                // Retrieve and cache Liked Playlist ID
                try {
                    val likedResp = api.getLikedPlaylist(user.id)
                    if (likedResp.isSuccessful && likedResp.body()?.playlistId != null) {
                        authPreferences.setLikedPlaylistId(likedResp.body()!!.playlistId!!)
                    }
                } catch (_: Exception) {}

                // Sync liked song IDs
                syncLikedSongs(user.id)

                return@withContext Result.success(user)
            } else {
                val errorMsg = parseErrorMessage(response.errorBody()?.string())
                return@withContext Result.failure(Exception(errorMsg ?: "Invalid credentials"))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(Exception(e.message ?: "Failed to connect to Tailscale server"))
        }
    }

    suspend fun signup(
        username: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.signup(
                SignupRequest(
                    username = username.trim(),
                    email = email.trim(),
                    password = password.trim(),
                    confirmPassword = confirmPassword.trim()
                )
            )
            if (response.isSuccessful && response.body() != null) {
                val msg = response.body()!!.message ?: "OTP sent to your email"
                return@withContext Result.success(msg)
            } else {
                val err = parseErrorMessage(response.errorBody()?.string())
                return@withContext Result.failure(Exception(err ?: "Signup failed"))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(Exception(e.message ?: "Server unreachable"))
        }
    }

    suspend fun verifyOtp(email: String, otp: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.verifyOtp(VerifyOtpRequest(email.trim(), otp.trim()))
            if (response.isSuccessful) {
                val msg = response.body()?.message ?: "Account verified successfully"
                return@withContext Result.success(msg)
            } else {
                val err = parseErrorMessage(response.errorBody()?.string())
                return@withContext Result.failure(Exception(err ?: "Invalid OTP"))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(Exception(e.message ?: "Verification failed"))
        }
    }

    private suspend fun syncLikedSongs(userId: Int) {
        try {
            val likedPlaylistId = getOrFetchLikedPlaylistId(userId)
            if (likedPlaylistId != null) {
                val resp = api.getPlaylistSongs(likedPlaylistId)
                if (resp.isSuccessful && resp.body() != null) {
                    val ids = resp.body()!!.map { it.id }.toSet()
                    ids.forEach { authPreferences.setSongLiked(it, true) }
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun getOrFetchLikedPlaylistId(userId: Int): Int? {
        val cached = authPreferences.getLikedPlaylistId()
        if (cached > 0) return cached

        return try {
            val resp = api.getLikedPlaylist(userId)
            if (resp.isSuccessful && resp.body()?.playlistId != null) {
                val id = resp.body()!!.playlistId!!
                authPreferences.setLikedPlaylistId(id)
                id
            } else null
        } catch (_: Exception) {
            null
        }
    }

    // ==========================================
    // SONGS
    // ==========================================

    suspend fun getSongs(query: String? = null): List<Song> = withContext(Dispatchers.IO) {
        val likedIds = authPreferences.getLikedSongIds()
        try {
            val response = if (query.isNullOrBlank()) {
                api.getSongs()
            } else {
                api.searchSongs(query.trim())
            }

            if (response.isSuccessful && response.body() != null) {
                val songs = response.body()!!.map { dto ->
                    Song(
                        id = dto.id,
                        title = dto.title,
                        artist = dto.artist,
                        audioUrl = sanitizeUrl(dto.audioUrl),
                        coverUrl = sanitizeUrl(dto.coverUrl),
                        duration = dto.duration,
                        isLiked = likedIds.contains(dto.id)
                    )
                }
                if (songs.isNotEmpty()) {
                    return@withContext attachLocalPaths(songs)
                }
            }
        } catch (_: Exception) {}

        // Fallback to sample songs if server has no songs yet or is unreachable
        val filtered = if (!query.isNullOrBlank()) {
            defaultSongs.filter {
                it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
            }
        } else {
            defaultSongs
        }

        val enriched = filtered.map { it.copy(isLiked = likedIds.contains(it.id)) }
        attachLocalPaths(enriched)
    }

    suspend fun uploadSong(
        title: String,
        artist: String,
        audioBytes: ByteArray,
        audioFileName: String,
        coverBytes: ByteArray,
        coverFileName: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val titlePart = title.trim().toRequestBody("text/plain".toMediaTypeOrNull())
            val artistPart = artist.trim().toRequestBody("text/plain".toMediaTypeOrNull())

            val audioBody = audioBytes.toRequestBody("audio/mpeg".toMediaTypeOrNull())
            val audioPart = MultipartBody.Part.createFormData("audio", audioFileName, audioBody)

            val coverBody = coverBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val coverPart = MultipartBody.Part.createFormData("cover", coverFileName, coverBody)

            val response = api.uploadSong(titlePart, artistPart, audioPart, coverPart)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(true)
            } else {
                val err = response.body()?.error ?: "Upload failed"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Upload failed"))
        }
    }

    // ==========================================
    // PLAYLISTS
    // ==========================================

    suspend fun getPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        val userId = getEffectiveUserId()
        try {
            val response = api.getPlaylists(userId)
            if (response.isSuccessful && response.body() != null) {
                // Filter out system playlists (is_system == 1) for the custom playlists list
                val userPlaylists = response.body()!!.filter { it.isSystem == 0 }
                return@withContext userPlaylists.map { p ->
                    // Optionally fetch count or default
                    Playlist(
                        id = p.id,
                        name = p.name,
                        description = "Playlist",
                        songCount = 0,
                        isLikedPlaylist = false
                    )
                }
            }
        } catch (_: Exception) {}

        emptyList()
    }

    suspend fun createPlaylist(name: String): Playlist = withContext(Dispatchers.IO) {
        val userId = getEffectiveUserId()
        try {
            val response = api.createPlaylist(userId = userId, name = name.trim())
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                return@withContext Playlist(
                    id = body.id ?: ((System.currentTimeMillis() % 10000).toInt()),
                    name = body.name ?: name,
                    description = "Playlist",
                    songCount = 0
                )
            }
        } catch (_: Exception) {}

        Playlist(
            id = (System.currentTimeMillis() % 10000).toInt(),
            name = name,
            description = "Playlist",
            songCount = 0
        )
    }

    suspend fun renamePlaylist(playlistId: Int, newName: String): Playlist = withContext(Dispatchers.IO) {
        val userId = getEffectiveUserId()
        try {
            val response = api.renamePlaylist(
                playlistId = playlistId,
                userId = userId,
                name = newName.trim()
            )
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                return@withContext Playlist(
                    id = playlistId,
                    name = body.name ?: newName,
                    description = "Playlist"
                )
            }
        } catch (_: Exception) {}

        Playlist(id = playlistId, name = newName)
    }

    suspend fun deletePlaylist(playlistId: Int) = withContext(Dispatchers.IO) {
        try {
            api.deletePlaylist(playlistId)
        } catch (_: Exception) {}
    }

    suspend fun getPlaylistSongs(playlistId: Int): List<Song> = withContext(Dispatchers.IO) {
        val likedIds = authPreferences.getLikedSongIds()
        val userId = getEffectiveUserId()

        val actualPlaylistId = if (playlistId == -1) {
            getOrFetchLikedPlaylistId(userId) ?: return@withContext emptyList()
        } else {
            playlistId
        }

        try {
            val response = api.getPlaylistSongs(actualPlaylistId)
            if (response.isSuccessful && response.body() != null) {
                val songs = response.body()!!.map { dto ->
                    Song(
                        id = dto.id,
                        title = dto.title,
                        artist = dto.artist,
                        audioUrl = sanitizeUrl(dto.audioUrl),
                        coverUrl = sanitizeUrl(dto.coverUrl),
                        duration = dto.duration,
                        isLiked = likedIds.contains(dto.id)
                    )
                }
                return@withContext attachLocalPaths(songs)
            }
        } catch (_: Exception) {}

        emptyList()
    }

    suspend fun addSongToPlaylist(playlistId: Int, songId: Int) = withContext(Dispatchers.IO) {
        try {
            api.addSongToPlaylist(playlistId = playlistId, songId = songId)
        } catch (_: Exception) {}
    }

    suspend fun removeSongFromPlaylist(playlistId: Int, songId: Int) = withContext(Dispatchers.IO) {
        try {
            api.removeSongFromPlaylist(playlistId = playlistId, songId = songId)
        } catch (_: Exception) {}
    }

    suspend fun toggleLike(song: Song): Boolean = withContext(Dispatchers.IO) {
        val userId = getEffectiveUserId()
        val currentlyLiked = authPreferences.getLikedSongIds().contains(song.id)
        val newStatus = !currentlyLiked
        authPreferences.setSongLiked(song.id, newStatus)

        try {
            val likedPlaylistId = getOrFetchLikedPlaylistId(userId)
            if (likedPlaylistId != null) {
                if (newStatus) {
                    api.addSongToPlaylist(playlistId = likedPlaylistId, songId = song.id)
                } else {
                    api.removeSongFromPlaylist(playlistId = likedPlaylistId, songId = song.id)
                }
            }
        } catch (_: Exception) {}

        newStatus
    }

    suspend fun getLikedSongs(): List<Song> = withContext(Dispatchers.IO) {
        getPlaylistSongs(-1)
    }

    private suspend fun attachLocalPaths(songs: List<Song>): List<Song> {
        return songs.map { song ->
            val local = downloadManager.getLocalPath(song.id)
            if (local != null) {
                song.copy(localPath = local, isDownloaded = true)
            } else {
                song.copy(isDownloaded = false)
            }
        }
    }

    private fun parseErrorMessage(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        return try {
            val json = JSONObject(errorBody)
            if (json.has("detail")) json.getString("detail")
            else if (json.has("message")) json.getString("message")
            else if (json.has("error")) json.getString("error")
            else errorBody
        } catch (_: Exception) {
            errorBody
        }
    }
}
