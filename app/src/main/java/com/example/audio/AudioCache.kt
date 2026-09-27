package com.example.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Centralized singleton audio streaming cache manager.
 * Uses Media3 SimpleCache with LRU eviction (512 MB).
 *
 * IMPORTANT ARCHITECTURAL SEPARATION:
 * - Temporary streaming audio is cached here in context.cacheDir/audio_cache.
 * - Permanent downloads (filesDir/downloads via DownloadManager) are NOT stored here
 *   and are completely independent and protected from LRU eviction.
 * - Image cache (context.cacheDir/image_cache via Coil) is separate.
 *
 * Cache initialization is completely passive and NEVER starts MusicPlaybackService,
 * foreground service, or ExoPlayer playback.
 */
@OptIn(UnstableApi::class)
object AudioCache {
    private const val TAG = "AudioCache"
    const val MAX_CACHE_SIZE_BYTES: Long = 512L * 1024L * 1024L // 512 MB
    const val AUDIO_CACHE_DIR = "audio_cache"

    @Volatile
    private var simpleCacheInstance: SimpleCache? = null

    @Volatile
    private var databaseProviderInstance: StandaloneDatabaseProvider? = null

    /**
     * Stable cache key factory:
     * - Uses customCacheKey if already provided on DataSpec (e.g. "song:<id>").
     * - Fallback: extracts song id from backend URL pattern (/songs/{id}/stream).
     * - Strips dynamic query parameters/tokens so that token rotations do not cause
     *   duplicate cache entries or cache misses for the same underlying song.
     */
    /**
     * Computes a stable cache key:
     * - Uses explicit key if provided (e.g. "song:<id>").
     * - Extracts song id from URL pattern (/songs/{id}/stream).
     * - Strips dynamic query parameters/tokens (?token=...) to avoid cache fragmentation.
     */
    fun buildCacheKey(uriString: String, explicitKey: String? = null): String {
        if (!explicitKey.isNullOrEmpty()) {
            return explicitKey
        }
        val match = Regex("""/songs/(\d+)(?:/stream)?""").find(uriString)
        return if (match != null) {
            "song:${match.groupValues[1]}"
        } else {
            uriString.substringBefore('?')
        }
    }

    val cacheKeyFactory: CacheKeyFactory = CacheKeyFactory { dataSpec ->
        buildCacheKey(dataSpec.uri.toString(), dataSpec.key)
    }

    /**
     * Returns the singleton SimpleCache instance using application context.
     * Thread-safe and safe to call from any thread or during startup.
     */
    @Synchronized
    fun getSimpleCache(context: Context): SimpleCache {
        val appContext = context.applicationContext
        return simpleCacheInstance ?: synchronized(this) {
            simpleCacheInstance ?: run {
                val cacheDir = File(appContext.cacheDir, AUDIO_CACHE_DIR).apply {
                    if (!exists()) {
                        mkdirs()
                    }
                }
                val dbProvider = databaseProviderInstance ?: StandaloneDatabaseProvider(appContext).also {
                    databaseProviderInstance = it
                }
                val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
                SimpleCache(cacheDir, evictor, dbProvider).also {
                    simpleCacheInstance = it
                    Log.d(TAG, "Initialized shared SimpleCache at ${cacheDir.absolutePath} (max: 512MB)")
                }
            }
        }
    }

    /**
     * Builds a DataSource.Factory:
     * - Delegates file://, content://, asset:// directly (used for local downloaded MP3s).
     * - Delegates http:// and https:// through CacheDataSource with SimpleCache.
     * - Uses FLAG_IGNORE_CACHE_ON_ERROR so any disk/cache failure safely falls back to network.
     */
    fun createCacheDataSourceFactory(context: Context): DataSource.Factory {
        val appContext = context.applicationContext
        val cache = getSimpleCache(appContext)

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setCacheKeyFactory(cacheKeyFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return DefaultDataSource.Factory(appContext, cacheDataSourceFactory)
    }

    /**
     * Returns all song IDs currently retained in SimpleCache memory.
     */
    fun getCachedSongIds(context: Context): Set<Int> {
        return try {
            val cache = getSimpleCache(context)
            val ids = mutableSetOf<Int>()
            for (key in cache.keys) {
                if (key.startsWith("song:")) {
                    key.removePrefix("song:").toIntOrNull()?.let { ids.add(it) }
                } else {
                    val match = Regex("""/songs/(\d+)""").find(key)
                    if (match != null) {
                        match.groupValues[1].toIntOrNull()?.let { ids.add(it) }
                    }
                }
            }
            ids
        } catch (e: Exception) {
            emptySet()
        }
    }

    /**
     * Returns the cached byte count for a given song in SimpleCache.
     */
    fun getSongCachedBytes(context: Context, songId: Int): Long {
        return try {
            val cache = getSimpleCache(context)
            val key = "song:$songId"
            cache.getCachedBytes(key, 0, -1)
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Checks if any byte range of a given song is cached in SimpleCache.
     */
    fun isSongCached(context: Context, songId: Int): Boolean {
        return try {
            val cache = getSimpleCache(context)
            val key = "song:$songId"
            getSongCachedBytes(context, songId) > 0 ||
                    cache.isCached(key, 0, 1024) ||
                    getCachedSongIds(context).contains(songId)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Preloads an upcoming track into the streaming audio cache asynchronously.
     * Modern music apps (e.g. Spotify, Apple Music) preload upcoming tracks
     * so that track changes and gapless transitions happen with zero latency.
     */
    fun preloadTrack(
        context: Context,
        audioUrl: String,
        songId: Int,
        bytesToPreload: Long = 512L * 1024L // 512 KB initial buffer
    ) {
        if (audioUrl.isBlank() || audioUrl.startsWith("file://")) return
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cache = getSimpleCache(appContext)
                val key = "song:$songId"
                if (cache.isCached(key, 0, bytesToPreload)) {
                    return@launch
                }
                val dataSpec = DataSpec.Builder()
                    .setUri(Uri.parse(audioUrl))
                    .setKey(key)
                    .setPosition(0)
                    .setLength(bytesToPreload)
                    .build()

                val httpDataSource = DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(8000)
                    .setReadTimeoutMs(10000)
                    .createDataSource()

                val cacheDataSource = CacheDataSource(
                    cache,
                    httpDataSource,
                    CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
                )

                val cacheWriter = CacheWriter(
                    cacheDataSource,
                    dataSpec,
                    null,
                    null
                )
                cacheWriter.cache()
                Log.d(TAG, "Successfully pre-cached $bytesToPreload bytes for upcoming song #$songId")
            } catch (e: Exception) {
                Log.d(TAG, "Preload skipped or interrupted for song #$songId: ${e.message}")
            }
        }
    }

    /**
     * Returns total cached bytes in the streaming audio cache.
     */
    fun getCacheSizeBytes(context: Context): Long {
        return try {
            getSimpleCache(context).cacheSpace
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Returns total cached bytes in the Coil artwork disk cache.
     */
    fun getImageCacheSizeBytes(context: Context): Long {
        return try {
            val dir = File(context.cacheDir, "image_cache")
            getFolderSizeBytes(dir)
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Returns total cached bytes in the OkHttp API disk cache.
     */
    fun getHttpCacheSizeBytes(context: Context): Long {
        return try {
            val dir = File(context.cacheDir, "http_cache")
            getFolderSizeBytes(dir)
        } catch (e: Exception) {
            0L
        }
    }

    private fun getFolderSizeBytes(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        val children = file.listFiles() ?: return 0L
        for (child in children) {
            size += getFolderSizeBytes(child)
        }
        return size
    }

    /**
     * Clears all cached streaming chunks without touching permanent downloads.
     */
    @Synchronized
    fun clearCache(context: Context) {
        try {
            val cache = getSimpleCache(context)
            for (key in cache.keys) {
                cache.removeResource(key)
            }
            Log.d(TAG, "Cleared streaming audio cache")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear audio cache", e)
        }
    }

    /**
     * Clears artwork and HTTP disk cache.
     */
    fun clearArtworkAndHttpCache(context: Context) {
        try {
            val imageDir = File(context.cacheDir, "image_cache")
            if (imageDir.exists()) imageDir.deleteRecursively()
            val httpDir = File(context.cacheDir, "http_cache")
            if (httpDir.exists()) httpDir.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear artwork and HTTP cache", e)
        }
    }
}
