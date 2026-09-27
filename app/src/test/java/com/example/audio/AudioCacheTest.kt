package com.example.audio

import com.example.model.Song
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AudioCacheTest {

    @Test
    fun testCacheLimitsAndConstants() {
        assertEquals(512L * 1024L * 1024L, AudioCache.MAX_CACHE_SIZE_BYTES)
        assertEquals("audio_cache", AudioCache.AUDIO_CACHE_DIR)
    }

    @Test
    fun testCacheKeyFactory_withExplicitKey() {
        val key = AudioCache.buildCacheKey(
            uriString = "https://example.com/stream",
            explicitKey = "song:101"
        )
        assertEquals("song:101", key)
    }

    @Test
    fun testCacheKeyFactory_extractsSongIdFromStandardUrl() {
        val key = AudioCache.buildCacheKey(
            uriString = "http://100.65.126.106:8000/songs/5/stream"
        )
        assertEquals("song:5", key)
    }

    @Test
    fun testCacheKeyFactory_stabilizesChangingQueryParameters() {
        val urlWithToken1 = "http://100.65.126.106:8000/songs/12/stream?token=abc123xyz&expires=1000"
        val urlWithToken2 = "http://100.65.126.106:8000/songs/12/stream?token=newtoken456&expires=2000"

        val key1 = AudioCache.buildCacheKey(urlWithToken1)
        val key2 = AudioCache.buildCacheKey(urlWithToken2)

        assertEquals("song:12", key1)
        assertEquals("song:12", key2)
        assertEquals("Cache keys must match regardless of token/query rotation", key1, key2)
    }

    @Test
    fun testCacheKeyFactory_genericUrlStripsQueryParams() {
        val url1 = "https://cdn.example.com/audio/track.mp3?session=1"
        val url2 = "https://cdn.example.com/audio/track.mp3?session=2"

        val key1 = AudioCache.buildCacheKey(url1)
        val key2 = AudioCache.buildCacheKey(url2)

        assertEquals(key1, key2)
        assertEquals("https://cdn.example.com/audio/track.mp3", key1)
    }

    @Test
    fun testDownloadedSongResolvesToLocalFileDirectly() {
        val downloadedSong = Song(
            id = 99,
            title = "Offline Track",
            artist = "Local Artist",
            audioUrl = "https://example.com/songs/99/stream",
            coverUrl = "https://example.com/covers/99.jpg",
            localPath = "/data/user/0/com.example/files/downloads/song_99.mp3",
            isDownloaded = true
        )

        assertEquals("/data/user/0/com.example/files/downloads/song_99.mp3", downloadedSong.resolvedPath())

        val streamingSong = Song(
            id = 100,
            title = "Streaming Track",
            artist = "Online Artist",
            audioUrl = "https://example.com/songs/100/stream",
            coverUrl = "https://example.com/covers/100.jpg",
            localPath = null,
            isDownloaded = false
        )

        assertEquals("https://example.com/songs/100/stream", streamingSong.resolvedPath())
    }

    @Test
    fun testDirectorySeparationRules() {
        val cacheBase = File("/data/user/0/com.example/cache")
        val filesBase = File("/data/user/0/com.example/files")

        val streamingAudioCache = File(cacheBase, AudioCache.AUDIO_CACHE_DIR)
        val imageCache = File(cacheBase, "image_cache")
        val permanentDownloads = File(filesBase, "downloads")

        assertNotEquals(streamingAudioCache.path, imageCache.path)
        assertNotEquals(streamingAudioCache.path, permanentDownloads.path)
        assertNotEquals(imageCache.path, permanentDownloads.path)

        assertTrue(permanentDownloads.path.startsWith(filesBase.path))
        assertTrue(streamingAudioCache.path.startsWith(cacheBase.path))
        assertTrue(imageCache.path.startsWith(cacheBase.path))
    }
}
