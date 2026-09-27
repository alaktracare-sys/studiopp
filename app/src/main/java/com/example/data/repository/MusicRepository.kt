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
import okhttp3.Cache
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

import com.example.data.local.ListeningHistoryEntity
import com.example.data.local.PlaylistDao
import com.example.data.local.PlaylistEntity
import com.example.data.local.SongEntity
import com.example.data.local.UserDao
import com.example.data.local.UserEntity
import com.example.data.remote.cache.ApiCacheConfig
import com.example.data.remote.cache.ApiCacheManager
import com.example.data.remote.cache.OkHttpCacheInterceptor
import com.example.data.remote.cache.OfflineCacheInterceptor
import com.example.data.remote.cache.NetworkCacheInterceptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MusicRepository(private val context: Context) {
    val authPreferences = AuthPreferences(context)
    val downloadManager = DownloadManager(context)
    val apiCacheManager = ApiCacheManager()
    private val appDatabase = AppDatabase.getInstance(context)
    private val songDao = appDatabase.songDao()
    private val playlistDao = appDatabase.playlistDao()
    private val userDao = appDatabase.userDao()

    val listeningHistory: Flow<List<ListeningHistoryEntity>> = appDatabase.getAllHistory()
    val downloadedSongsFlow: Flow<List<Song>> = songDao.getAllDownloadedSongs().map { list ->
        val likedIds = authPreferences.getLikedSongIds()
        list.map { entity ->
            entity.toSong().copy(isLiked = likedIds.contains(entity.songId))
        }
    }
    val cachedSongsFlow: Flow<List<Song>> = songDao.getAllCachedSongsFlow().map { list ->
        val likedIds = authPreferences.getLikedSongIds()
        list.map { it.toSong().copy(isLiked = likedIds.contains(it.id)) }
    }
    val cachedUserFlow: Flow<User?> = userDao.getUserFlow(getEffectiveUserId()).map { it?.toUser() }
    val cachedPlaylistsFlow: Flow<List<Playlist>> = playlistDao.getUserPlaylistsFlow(getEffectiveUserId()).map { list ->
        list.map { it.toPlaylist() }
    }

    suspend fun recordSongPlayed(song: Song, initialListenedSec: Long = 0L): Long {
        return appDatabase.insertListeningHistory(
            ListeningHistoryEntity(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                audioUrl = song.audioUrl,
                coverUrl = song.coverUrl,
                duration = song.duration,
                listenedSeconds = initialListenedSec,
                playedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateListeningDuration(id: Long, listenedSeconds: Long) {
        appDatabase.updateListeningDuration(id, listenedSeconds)
    }

    suspend fun deleteHistoryItem(id: Long) {
        appDatabase.deleteHistoryItem(id)
    }

    suspend fun clearListeningHistory() {
        appDatabase.clearAllHistory()
    }

    @Volatile
    private var cachedBaseUrl: String = authPreferences.getServerBaseUrl()

    @Volatile
    private var apiInstance: MusicApiService? = null

    private val api: MusicApiService
        get() {
            val currentUrl = authPreferences.getServerBaseUrl()
            val existing = apiInstance
            if (existing != null && currentUrl == cachedBaseUrl) {
                return existing
            }
            return synchronized(this) {
                val current = apiInstance
                if (current != null && currentUrl == cachedBaseUrl) {
                    current
                } else {
                    cachedBaseUrl = currentUrl
                    val newApi = buildApiService(currentUrl)
                    apiInstance = newApi
                    newApi
                }
            }
        }

    private fun buildApiService(baseUrl: String): MusicApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }

        val httpCacheDir = File(context.cacheDir, ApiCacheConfig.HTTP_CACHE_DIR)
        val httpCache = Cache(httpCacheDir, ApiCacheConfig.HTTP_CACHE_MAX_BYTES)

        val client = OkHttpClient.Builder()
            .cache(httpCache)
            .addInterceptor(OfflineCacheInterceptor(context))
            .addNetworkInterceptor(NetworkCacheInterceptor())
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
                userDao.insertUser(UserEntity.fromUser(user))

                // Retrieve and cache Liked Playlist ID
                try {
                    val likedResp = api.getLikedPlaylist(user.id)
                    if (likedResp.isSuccessful && likedResp.body()?.playlistId != null) {
                        authPreferences.setLikedPlaylistId(likedResp.body()!!.playlistId!!)
                    }
                } catch (_: Exception) {}

                // Sync liked song IDs and pending offline actions
                syncLikedSongs(user.id)
                syncOfflineActions()

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
    // SONGS (Offline-First Catalog & Search)
    // ==========================================

    suspend fun getSongs(query: String? = null, forceRefresh: Boolean = false): List<Song> = withContext(Dispatchers.IO) {
        if (!query.isNullOrBlank()) {
            return@withContext searchSongs(query = query, forceRefresh = forceRefresh)
        }

        val likedIds = authPreferences.getLikedSongIds()

        // 1. In-memory bounded feed cache check (avoids repeating API calls within TTL)
        if (!forceRefresh) {
            val cachedFeed = apiCacheManager.getFeed("home_feed")
            if (cachedFeed != null) {
                val withLikes = cachedFeed.map { it.copy(isLiked = likedIds.contains(it.id)) }
                return@withContext attachLocalPaths(withLikes)
            }
        }

        // 2. Fetch fresh catalog from network
        val cachedEntities = songDao.getAllCachedSongs()
        val localSongs = cachedEntities.map { it.toSong().copy(isLiked = likedIds.contains(it.id)) }

        try {
            val response = api.getSongs()
            if (response.isSuccessful && response.body() != null) {
                val remoteSongs = response.body()!!.map { dto ->
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
                if (remoteSongs.isNotEmpty()) {
                    apiCacheManager.putFeed("home_feed", remoteSongs)
                    songDao.insertCachedSongs(remoteSongs.map { SongEntity.fromSong(it) })
                    authPreferences.setLastSyncTime("songs")
                    return@withContext attachLocalPaths(remoteSongs)
                }
            }
        } catch (_: Exception) {
            // Network is unavailable or server error: continue using local cached songs
        }

        // 3. Return local cached songs if available
        if (localSongs.isNotEmpty()) {
            return@withContext attachLocalPaths(localSongs)
        }

        // 4. Initial offline fallback: cache default songs to DB and return
        val enriched = defaultSongs.map { it.copy(isLiked = likedIds.contains(it.id)) }
        songDao.insertCachedSongs(enriched.map { SongEntity.fromSong(it) })
        attachLocalPaths(enriched)
    }

    suspend fun searchSongs(
        query: String,
        page: Int = 1,
        limit: Int = 30,
        offset: Int = 0,
        filter: String? = null,
        sort: String? = null,
        forceRefresh: Boolean = false
    ): List<Song> = withContext(Dispatchers.IO) {
        val likedIds = authPreferences.getLikedSongIds()
        val q = query.trim()
        val actualOffset = if (offset > 0) offset else (page - 1) * limit
        val searchKey = ApiCacheConfig.buildSearchCacheKey(
            query = q,
            page = page,
            limit = limit,
            offset = actualOffset,
            filter = filter,
            sort = sort
        )

        // 1. Fast in-memory bounded search cache check
        if (!forceRefresh) {
            val cached = apiCacheManager.getSearch(searchKey)
            if (cached != null) {
                val withLikes = cached.map { it.copy(isLiked = likedIds.contains(it.id)) }
                return@withContext attachLocalPaths(withLikes)
            }
        }

        // 2. Network query
        try {
            val response = api.searchSongs(query = q, limit = limit, offset = actualOffset)
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
                apiCacheManager.putSearch(searchKey, songs)
                if (songs.isNotEmpty()) {
                    songDao.insertCachedSongs(songs.map { SongEntity.fromSong(it) })
                }
                return@withContext attachLocalPaths(songs)
            }
        } catch (_: Exception) {}

        // 3. Offline search from local SQLite database
        val cachedMatches = songDao.searchCachedSongs(q)
        if (cachedMatches.isNotEmpty()) {
            val songs = cachedMatches.map { it.toSong().copy(isLiked = likedIds.contains(it.id)) }
            return@withContext attachLocalPaths(songs)
        }

        val fallback = defaultSongs.filter {
            it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true)
        }.map { it.copy(isLiked = likedIds.contains(it.id)) }
        attachLocalPaths(fallback)
    }

    suspend fun getArtistSongs(artist: String, forceRefresh: Boolean = false): List<Song> = withContext(Dispatchers.IO) {
        val likedIds = authPreferences.getLikedSongIds()
        val key = ApiCacheConfig.buildArtistCacheKey(artist)
        if (!forceRefresh) {
            val cached = apiCacheManager.getArtist(key)
            if (cached != null) {
                return@withContext attachLocalPaths(cached.map { it.copy(isLiked = likedIds.contains(it.id)) })
            }
        }

        // Fetch matching artist tracks via search API
        try {
            val response = api.searchSongs(query = artist, limit = 50, offset = 0)
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
                }.filter { it.artist.contains(artist, ignoreCase = true) }

                if (songs.isNotEmpty()) {
                    apiCacheManager.putArtist(key, songs)
                    songDao.insertCachedSongs(songs.map { SongEntity.fromSong(it) })
                    return@withContext attachLocalPaths(songs)
                }
            }
        } catch (_: Exception) {}

        // Fallback: local cached songs matching artist
        val localMatches = songDao.searchCachedSongs(artist)
            .filter { it.artist.contains(artist, ignoreCase = true) }
            .map { it.toSong().copy(isLiked = likedIds.contains(it.id)) }
        if (localMatches.isNotEmpty()) {
            apiCacheManager.putArtist(key, localMatches)
            return@withContext attachLocalPaths(localMatches)
        }

        val fallback = defaultSongs.filter { it.artist.contains(artist, ignoreCase = true) }
            .map { it.copy(isLiked = likedIds.contains(it.id)) }
        attachLocalPaths(fallback)
    }

    suspend fun getAlbumSongs(album: String, forceRefresh: Boolean = false): List<Song> = withContext(Dispatchers.IO) {
        val likedIds = authPreferences.getLikedSongIds()
        val key = ApiCacheConfig.buildAlbumCacheKey(album)
        if (!forceRefresh) {
            val cached = apiCacheManager.getAlbum(key)
            if (cached != null) {
                return@withContext attachLocalPaths(cached.map { it.copy(isLiked = likedIds.contains(it.id)) })
            }
        }

        try {
            val response = api.searchSongs(query = album, limit = 50, offset = 0)
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
                    apiCacheManager.putAlbum(key, songs)
                    songDao.insertCachedSongs(songs.map { SongEntity.fromSong(it) })
                    return@withContext attachLocalPaths(songs)
                }
            }
        } catch (_: Exception) {}

        val localMatches = songDao.searchCachedSongs(album)
            .map { it.toSong().copy(isLiked = likedIds.contains(it.id)) }
        if (localMatches.isNotEmpty()) {
            apiCacheManager.putAlbum(key, localMatches)
            return@withContext attachLocalPaths(localMatches)
        }

        val fallback = defaultSongs.filter { it.title.contains(album, ignoreCase = true) }
            .map { it.copy(isLiked = likedIds.contains(it.id)) }
        attachLocalPaths(fallback)
    }

    suspend fun getRecommendations(seedSongId: Int? = null, forceRefresh: Boolean = false): List<Song> = withContext(Dispatchers.IO) {
        val userId = getEffectiveUserId()
        val key = ApiCacheConfig.buildRecommendationsCacheKey(userId, seedSongId)
        val likedIds = authPreferences.getLikedSongIds()

        if (!forceRefresh) {
            val cached = apiCacheManager.getRecommendations(key)
            if (cached != null) {
                return@withContext attachLocalPaths(cached.map { it.copy(isLiked = likedIds.contains(it.id)) })
            }
        }

        // Generate recommendations from catalog
        val allCatalog = getCachedSongs()
        val recs = if (seedSongId != null) {
            val seed = allCatalog.find { it.id == seedSongId }
            if (seed != null) {
                allCatalog.filter { it.id != seedSongId && (it.artist.equals(seed.artist, ignoreCase = true) || it.title.length % 2 == seed.title.length % 2) }
            } else allCatalog.shuffled()
        } else {
            allCatalog.shuffled()
        }
        val result = if (recs.isNotEmpty()) recs else allCatalog
        apiCacheManager.putRecommendations(key, result)
        attachLocalPaths(result.map { it.copy(isLiked = likedIds.contains(it.id)) })
    }

    suspend fun getRecentlyPlayedSongs(forceRefresh: Boolean = false): List<Song> = withContext(Dispatchers.IO) {
        val userId = getEffectiveUserId()
        val key = ApiCacheConfig.buildRecentlyPlayedCacheKey(userId)
        val likedIds = authPreferences.getLikedSongIds()

        if (!forceRefresh) {
            val cached = apiCacheManager.getRecentlyPlayed(key)
            if (cached != null) {
                return@withContext attachLocalPaths(cached.map { it.copy(isLiked = likedIds.contains(it.id)) })
            }
        }

        val history = appDatabase.getAllHistoryList()
        val songs = history.map {
            Song(
                id = it.songId,
                title = it.title,
                artist = it.artist,
                audioUrl = it.audioUrl,
                coverUrl = it.coverUrl,
                duration = it.duration,
                isLiked = likedIds.contains(it.songId)
            )
        }.distinctBy { it.id }

        if (songs.isNotEmpty()) {
            apiCacheManager.putRecentlyPlayed(key, songs)
        }
        attachLocalPaths(songs)
    }

    suspend fun getCachedSongs(): List<Song> = withContext(Dispatchers.IO) {
        val likedIds = authPreferences.getLikedSongIds()
        val inMemoryFeed = apiCacheManager.getFeed("home_feed")
        if (inMemoryFeed != null && inMemoryFeed.isNotEmpty()) {
            return@withContext attachLocalPaths(inMemoryFeed.map { it.copy(isLiked = likedIds.contains(it.id)) })
        }
        val cachedEntities = songDao.getAllCachedSongs()
        val songs = cachedEntities.map { it.toSong().copy(isLiked = likedIds.contains(it.id)) }
        if (songs.isNotEmpty()) {
            attachLocalPaths(songs)
        } else {
            val enriched = defaultSongs.map { it.copy(isLiked = likedIds.contains(it.id)) }
            songDao.insertCachedSongs(enriched.map { SongEntity.fromSong(it) })
            attachLocalPaths(enriched)
        }
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
    // PLAYLISTS (Offline-First Storage & Sync)
    // ==========================================

    suspend fun getPlaylists(forceRefresh: Boolean = false): List<Playlist> = withContext(Dispatchers.IO) {
        val userId = getEffectiveUserId()
        if (!forceRefresh) {
            val inMemory = apiCacheManager.getUserPlaylists(userId)
            if (inMemory != null) {
                return@withContext inMemory
            }
        }

        val localEntities = playlistDao.getUserPlaylists(userId)
        val localPlaylists = localEntities.map { it.toPlaylist() }

        try {
            val response = api.getPlaylists(userId)
            if (response.isSuccessful && response.body() != null) {
                val userPlaylists = response.body()!!.filter { it.isSystem == 0 }
                val entities = userPlaylists.map { p ->
                    PlaylistEntity(
                        id = p.id,
                        userId = userId,
                        name = p.name,
                        description = "Playlist",
                        isSystem = false,
                        songCount = 0,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                }
                playlistDao.insertPlaylists(entities)
                val mapped = entities.map { it.toPlaylist() }
                apiCacheManager.putUserPlaylists(userId, mapped)
                authPreferences.setLastSyncTime("playlists")
                return@withContext mapped
            }
        } catch (_: Exception) {}

        if (localPlaylists.isNotEmpty()) {
            apiCacheManager.putUserPlaylists(userId, localPlaylists)
        }
        localPlaylists
    }

    suspend fun getCachedPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        val userId = getEffectiveUserId()
        val inMemory = apiCacheManager.getUserPlaylists(userId)
        if (inMemory != null && inMemory.isNotEmpty()) {
            return@withContext inMemory
        }
        val localEntities = playlistDao.getUserPlaylists(userId)
        val mapped = localEntities.map { it.toPlaylist() }
        if (mapped.isNotEmpty()) {
            apiCacheManager.putUserPlaylists(userId, mapped)
        }
        mapped
    }

    suspend fun createPlaylist(name: String): Playlist = withContext(Dispatchers.IO) {
        val userId = getEffectiveUserId()
        val tempId = ((System.currentTimeMillis() % 100000).toInt())
        apiCacheManager.invalidateUserPlaylists(userId)

        try {
            val response = api.createPlaylist(userId = userId, name = name.trim())
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val pl = Playlist(
                    id = body.id ?: tempId,
                    name = body.name ?: name,
                    description = "Playlist",
                    songCount = 0
                )
                playlistDao.insertPlaylist(PlaylistEntity.fromPlaylist(pl, userId))
                return@withContext pl
            }
        } catch (_: Exception) {
            val action = JSONObject().apply {
                put("type", "create_playlist")
                put("userId", userId)
                put("name", name.trim())
            }
            authPreferences.addOfflineAction(action)
        }

        val localPl = Playlist(
            id = tempId,
            name = name.trim(),
            description = "Playlist",
            songCount = 0
        )
        playlistDao.insertPlaylist(PlaylistEntity.fromPlaylist(localPl, userId))
        localPl
    }

    suspend fun renamePlaylist(playlistId: Int, newName: String): Playlist = withContext(Dispatchers.IO) {
        val userId = getEffectiveUserId()
        apiCacheManager.invalidateUserPlaylists(userId)
        playlistDao.updatePlaylistName(playlistId, newName.trim())
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
        } catch (_: Exception) {
            val action = JSONObject().apply {
                put("type", "rename_playlist")
                put("playlistId", playlistId)
                put("userId", userId)
                put("name", newName.trim())
            }
            authPreferences.addOfflineAction(action)
        }

        Playlist(id = playlistId, name = newName)
    }

    suspend fun deletePlaylist(playlistId: Int) = withContext(Dispatchers.IO) {
        val userId = getEffectiveUserId()
        apiCacheManager.invalidateUserPlaylists(userId)
        apiCacheManager.invalidatePlaylistSongs(playlistId)
        playlistDao.deletePlaylist(playlistId)
        try {
            api.deletePlaylist(playlistId)
        } catch (_: Exception) {
            val action = JSONObject().apply {
                put("type", "delete_playlist")
                put("playlistId", playlistId)
            }
            authPreferences.addOfflineAction(action)
        }
    }

    suspend fun getPlaylistSongs(playlistId: Int, forceRefresh: Boolean = false): List<Song> = withContext(Dispatchers.IO) {
        val likedIds = authPreferences.getLikedSongIds()
        val userId = getEffectiveUserId()

        if (playlistId == Playlist.ID_DOWNLOADED || playlistId == -2) {
            return@withContext getDownloadedSongs()
        }

        if (playlistId == Playlist.ID_OFFLINE_BACKUP || playlistId == -3) {
            return@withContext getOfflineBackupSongs()
        }

        if (playlistId == Playlist.ID_LIKED || playlistId == -1) {
            val localLiked = songDao.getLikedSongs().map { it.toSong() }
            val actualLikedId = getOrFetchLikedPlaylistId(userId)
            if (actualLikedId != null) {
                try {
                    val response = api.getPlaylistSongs(actualLikedId)
                    if (response.isSuccessful && response.body() != null) {
                        val songs = response.body()!!.map { dto ->
                            Song(
                                id = dto.id,
                                title = dto.title,
                                artist = dto.artist,
                                audioUrl = sanitizeUrl(dto.audioUrl),
                                coverUrl = sanitizeUrl(dto.coverUrl),
                                duration = dto.duration,
                                isLiked = true
                            )
                        }
                        songDao.insertCachedSongs(songs.map { SongEntity.fromSong(it, isLiked = true) })
                        return@withContext attachLocalPaths(songs)
                    }
                } catch (_: Exception) {}
            }
            return@withContext attachLocalPaths(localLiked)
        }

        // Custom playlist:
        if (!forceRefresh) {
            val inMemory = apiCacheManager.getPlaylistSongs(playlistId)
            if (inMemory != null) {
                val withLikes = inMemory.map { it.copy(isLiked = likedIds.contains(it.id)) }
                return@withContext attachLocalPaths(withLikes)
            }
        }

        val localCached = playlistDao.getPlaylistSongs(playlistId).map {
            it.toSong().copy(isLiked = likedIds.contains(it.id))
        }

        try {
            val response = api.getPlaylistSongs(playlistId)
            if (response.isSuccessful && response.body() != null) {
                val remoteSongs = response.body()!!.map { dto ->
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
                apiCacheManager.putPlaylistSongs(playlistId, remoteSongs)
                playlistDao.setPlaylistSongs(playlistId, remoteSongs.map { SongEntity.fromSong(it) })
                return@withContext attachLocalPaths(remoteSongs)
            }
        } catch (_: Exception) {}

        if (localCached.isNotEmpty()) {
            apiCacheManager.putPlaylistSongs(playlistId, localCached)
        }
        attachLocalPaths(localCached)
    }

    suspend fun getCachedPlaylistSongs(playlistId: Int): List<Song> = withContext(Dispatchers.IO) {
        val likedIds = authPreferences.getLikedSongIds()
        if (playlistId == Playlist.ID_DOWNLOADED || playlistId == -2) {
            return@withContext getDownloadedSongs()
        }
        if (playlistId == Playlist.ID_OFFLINE_BACKUP || playlistId == -3) {
            return@withContext getOfflineBackupSongs()
        }
        if (playlistId == Playlist.ID_LIKED || playlistId == -1) {
            return@withContext getCachedLikedSongs()
        }
        val localCached = playlistDao.getPlaylistSongs(playlistId).map {
            it.toSong().copy(isLiked = likedIds.contains(it.id))
        }
        attachLocalPaths(localCached)
    }

    /**
     * Spotify-style Offline Backup / Cached Songs Playlist:
     * Assembles all tracks present in the streaming cache memory (SimpleCache)
     * and permanent downloads, allowing offline listening with zero internet required.
     */
    suspend fun getOfflineBackupSongs(): List<Song> = withContext(Dispatchers.IO) {
        val likedIds = authPreferences.getLikedSongIds()
        val cachedMemoryIds = com.example.audio.AudioCache.getCachedSongIds(context)
        val downloadedList = songDao.getDownloadedSongsList()
        val downloadedIds = downloadedList.map { it.songId }.toSet()
        val allOfflineIds = cachedMemoryIds + downloadedIds

        val catalogEntities = songDao.getAllCachedSongs()
        val historyEntities = appDatabase.getAllHistoryList()

        val knownSongs = mutableMapOf<Int, Song>()

        // 1. Default catalog songs
        defaultSongs.forEach { knownSongs[it.id] = it }

        // 2. Room cached songs
        catalogEntities.forEach { knownSongs[it.id] = it.toSong() }

        // 3. Listening history songs
        historyEntities.forEach {
            knownSongs[it.songId] = Song(
                id = it.songId,
                title = it.title,
                artist = it.artist,
                audioUrl = it.audioUrl,
                coverUrl = it.coverUrl,
                duration = it.duration
            )
        }

        // 4. Downloaded songs
        downloadedList.forEach {
            knownSongs[it.songId] = it.toSong()
        }

        // Filter songs that exist in cache memory or downloaded
        val offlineReady = knownSongs.values.filter { song ->
            allOfflineIds.contains(song.id) ||
                    (song.localPath != null && File(song.localPath).exists()) ||
                    com.example.audio.AudioCache.isSongCached(context, song.id)
        }.distinctBy { it.id }.toMutableList()

        // If cache memory is fresh and no songs streamed yet, provide downloaded or initial songs so user can test immediately
        if (offlineReady.isEmpty()) {
            if (downloadedList.isNotEmpty()) {
                offlineReady.addAll(downloadedList.map { it.toSong() })
            } else {
                offlineReady.addAll(defaultSongs.take(3))
            }
        }

        val enriched = offlineReady.map { song ->
            song.copy(
                isLiked = likedIds.contains(song.id),
                isCached = true
            )
        }
        attachLocalPaths(enriched)
    }

    suspend fun addSongToPlaylist(playlistId: Int, songId: Int) = withContext(Dispatchers.IO) {
        val userId = getEffectiveUserId()
        apiCacheManager.invalidatePlaylistSongs(playlistId)
        apiCacheManager.invalidateUserPlaylists(userId)
        playlistDao.addSongToPlaylist(playlistId, songId, position = 0)
        try {
            api.addSongToPlaylist(playlistId = playlistId, songId = songId)
        } catch (_: Exception) {
            val action = JSONObject().apply {
                put("type", "add_song")
                put("playlistId", playlistId)
                put("songId", songId)
            }
            authPreferences.addOfflineAction(action)
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: Int, songId: Int) = withContext(Dispatchers.IO) {
        val userId = getEffectiveUserId()
        apiCacheManager.invalidatePlaylistSongs(playlistId)
        apiCacheManager.invalidateUserPlaylists(userId)
        playlistDao.removeSongFromPlaylist(playlistId, songId)
        try {
            api.removeSongFromPlaylist(playlistId = playlistId, songId = songId)
        } catch (_: Exception) {
            val action = JSONObject().apply {
                put("type", "remove_song")
                put("playlistId", playlistId)
                put("songId", songId)
            }
            authPreferences.addOfflineAction(action)
        }
    }

    // ==========================================
    // LIKES / FAVORITES
    // ==========================================

    suspend fun toggleLike(song: Song): Boolean = withContext(Dispatchers.IO) {
        val userId = getEffectiveUserId()
        val currentlyLiked = authPreferences.getLikedSongIds().contains(song.id)
        val newStatus = !currentlyLiked
        authPreferences.setSongLiked(song.id, newStatus)
        songDao.updateSongLiked(song.id, newStatus)
        apiCacheManager.updateSongLike(song.id, newStatus)
        apiCacheManager.invalidatePlaylistSongs(Playlist.ID_LIKED)

        try {
            val likedPlaylistId = getOrFetchLikedPlaylistId(userId)
            if (likedPlaylistId != null) {
                if (newStatus) {
                    api.addSongToPlaylist(playlistId = likedPlaylistId, songId = song.id)
                } else {
                    api.removeSongFromPlaylist(playlistId = likedPlaylistId, songId = song.id)
                }
            }
        } catch (_: Exception) {
            val action = JSONObject().apply {
                put("type", "toggle_like")
                put("songId", song.id)
                put("newStatus", newStatus)
                put("userId", userId)
            }
            authPreferences.addOfflineAction(action)
        }

        newStatus
    }

    suspend fun getLikedSongs(): List<Song> = withContext(Dispatchers.IO) {
        getPlaylistSongs(Playlist.ID_LIKED)
    }

    suspend fun getCachedLikedSongs(): List<Song> = withContext(Dispatchers.IO) {
        val likedIds = authPreferences.getLikedSongIds()
        val localLiked = songDao.getLikedSongs().map { it.toSong().copy(isLiked = true) }
        if (localLiked.isNotEmpty()) {
            attachLocalPaths(localLiked)
        } else {
            val allCached = songDao.getAllCachedSongs().filter { likedIds.contains(it.id) || it.isLiked }
            attachLocalPaths(allCached.map { it.toSong().copy(isLiked = true) })
        }
    }

    suspend fun getCachedUser(): User? = withContext(Dispatchers.IO) {
        val userId = getEffectiveUserId()
        val fromDb = userDao.getUser(userId)?.toUser()
        if (fromDb != null) return@withContext fromDb
        val fromPrefs = authPreferences.getUser()
        if (fromPrefs != null) {
            userDao.insertUser(UserEntity.fromUser(fromPrefs))
            return@withContext fromPrefs
        }
        null
    }

    suspend fun clearUserData() = withContext(Dispatchers.IO) {
        apiCacheManager.clearAll()
        userDao.clearUser()
        authPreferences.clear()
    }

    suspend fun getDownloadedSongs(): List<Song> = withContext(Dispatchers.IO) {
        val likedIds = authPreferences.getLikedSongIds()
        val entities = songDao.getDownloadedSongsList()
        entities.map { entity ->
            entity.toSong().copy(isLiked = likedIds.contains(entity.songId))
        }
    }

    // ==========================================
    // BACKGROUND OFFLINE ACTION SYNC
    // ==========================================

    suspend fun syncOfflineActions() = withContext(Dispatchers.IO) {
        val queue = authPreferences.getOfflineQueue()
        if (queue.isEmpty()) return@withContext

        val remaining = mutableListOf<JSONObject>()
        for (action in queue) {
            val success = try {
                when (action.optString("type")) {
                    "create_playlist" -> {
                        val userId = action.getInt("userId")
                        val name = action.getString("name")
                        val resp = api.createPlaylist(userId, name)
                        if (resp.isSuccessful && resp.body()?.id != null) {
                            playlistDao.insertPlaylist(PlaylistEntity(resp.body()!!.id!!, userId, name))
                            true
                        } else false
                    }
                    "rename_playlist" -> {
                        val plId = action.getInt("playlistId")
                        val userId = action.getInt("userId")
                        val name = action.getString("name")
                        api.renamePlaylist(plId, userId, name).isSuccessful
                    }
                    "delete_playlist" -> {
                        val plId = action.getInt("playlistId")
                        api.deletePlaylist(plId).isSuccessful
                    }
                    "add_song" -> {
                        val plId = action.getInt("playlistId")
                        val songId = action.getInt("songId")
                        api.addSongToPlaylist(plId, songId).isSuccessful
                    }
                    "remove_song" -> {
                        val plId = action.getInt("playlistId")
                        val songId = action.getInt("songId")
                        api.removeSongFromPlaylist(plId, songId).isSuccessful
                    }
                    "toggle_like" -> {
                        val userId = action.getInt("userId")
                        val songId = action.getInt("songId")
                        val status = action.getBoolean("newStatus")
                        val likedPlId = getOrFetchLikedPlaylistId(userId)
                        if (likedPlId != null) {
                            if (status) api.addSongToPlaylist(likedPlId, songId).isSuccessful
                            else api.removeSongFromPlaylist(likedPlId, songId).isSuccessful
                        } else false
                    }
                    else -> true
                }
            } catch (_: Exception) {
                false
            }
            if (!success) {
                remaining.add(action)
            }
        }
        authPreferences.clearOfflineQueue()
        remaining.forEach { authPreferences.addOfflineAction(it) }
    }

    private suspend fun attachLocalPaths(songs: List<Song>): List<Song> {
        val cachedMemoryIds = com.example.audio.AudioCache.getCachedSongIds(context)
        return songs.map { song ->
            val local = downloadManager.getLocalPath(song.id)
            val isDownloaded = local != null && File(local).exists()
            val isCached = isDownloaded || cachedMemoryIds.contains(song.id) || com.example.audio.AudioCache.isSongCached(context, song.id)
            if (isDownloaded) {
                song.copy(localPath = local, isDownloaded = true, isCached = true)
            } else {
                song.copy(isDownloaded = false, isCached = isCached)
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
