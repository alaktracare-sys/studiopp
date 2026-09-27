package com.example.data

import com.example.data.local.ListeningHistoryEntity
import com.example.ui.screens.profile.formatListeningSeconds
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ListeningHistoryStorageTest {

    @Test
    fun testListeningHistoryEntity_effectiveListenedSeconds() {
        val fullItem = ListeningHistoryEntity(
            id = 1L,
            songId = 101,
            title = "Acoustic Melody",
            artist = "SoundHelix",
            audioUrl = "https://example.com/audio1.mp3",
            coverUrl = "https://example.com/cover1.jpg",
            duration = 240.0,
            listenedSeconds = 185L, // User listened for 185 seconds
            playedAt = System.currentTimeMillis()
        )

        assertEquals(185L, fullItem.effectiveListenedSeconds)
        assertEquals("Acoustic Melody", fullItem.toSong().title)
        assertEquals(101, fullItem.toSong().id)

        // Verify that 0L listenedSeconds accurately reflects 0L real duration without bogus fallbacks
        val zeroPlayItem = ListeningHistoryEntity(
            id = 2L,
            songId = 102,
            title = "Microtonal Groove",
            artist = "Sevish",
            audioUrl = "https://example.com/audio2.mp3",
            coverUrl = "https://example.com/cover2.jpg",
            duration = 180.0,
            listenedSeconds = 0L,
            playedAt = System.currentTimeMillis()
        )
        assertEquals(0L, zeroPlayItem.effectiveListenedSeconds)
    }

    @Test
    fun testDailyAggregation_howManySongsAndTotalTimeListenedInWholeDay() {
        val now = System.currentTimeMillis()
        val today = LocalDate.now()

        val historyList = listOf(
            ListeningHistoryEntity(
                id = 1L,
                songId = 101,
                title = "Song A",
                artist = "Artist 1",
                audioUrl = "https://example.com/a.mp3",
                coverUrl = "https://example.com/a.jpg",
                duration = 200.0,
                listenedSeconds = 150L, // 2m 30s
                playedAt = now - 1000L
            ),
            ListeningHistoryEntity(
                id = 2L,
                songId = 102,
                title = "Song B",
                artist = "Artist 2",
                audioUrl = "https://example.com/b.mp3",
                coverUrl = "https://example.com/b.jpg",
                duration = 180.0,
                listenedSeconds = 180L, // 3m 0s
                playedAt = now - 2000L
            ),
            ListeningHistoryEntity(
                id = 3L,
                songId = 101, // Replayed Song A
                title = "Song A",
                artist = "Artist 1",
                audioUrl = "https://example.com/a.mp3",
                coverUrl = "https://example.com/a.jpg",
                duration = 200.0,
                listenedSeconds = 60L, // 1m 0s
                playedAt = now - 3000L
            ),
            // Song from yesterday (25 hours ago)
            ListeningHistoryEntity(
                id = 4L,
                songId = 103,
                title = "Song C",
                artist = "Artist 3",
                audioUrl = "https://example.com/c.mp3",
                coverUrl = "https://example.com/c.jpg",
                duration = 300.0,
                listenedSeconds = 300L,
                playedAt = now - (25 * 3600 * 1000L)
            )
        )

        // Filter for whole day (Today)
        val todaySongs = historyList.filter {
            val date = Instant.ofEpochMilli(it.playedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            date == today
        }

        // How many songs did user listen to today?
        val totalSongsToday = todaySongs.size
        assertEquals(3, totalSongsToday)

        val uniqueSongsToday = todaySongs.map { it.songId }.distinct().size
        assertEquals(2, uniqueSongsToday)

        // Time user listened to songs for in the whole day:
        val totalListenTimeSecondsToday = todaySongs.sumOf { it.effectiveListenedSeconds }
        assertEquals(390L, totalListenTimeSecondsToday) // 150 + 180 + 60 = 390 seconds (6m 30s)

        // Verify time formatter
        val formattedTime = formatListeningSeconds(totalListenTimeSecondsToday)
        assertEquals("6m 30s", formattedTime)

        // What songs did he listen to today?
        val songTitlesToday = todaySongs.map { it.title }
        assertTrue(songTitlesToday.contains("Song A"))
        assertTrue(songTitlesToday.contains("Song B"))
        assertFalse(songTitlesToday.contains("Song C")) // Song C was yesterday
    }

    @Test
    fun testFormatListeningSeconds_variousDurations() {
        assertEquals("45s", formatListeningSeconds(45L))
        assertEquals("2m 15s", formatListeningSeconds(135L))
        assertEquals("1h 14m", formatListeningSeconds(4450L))
        assertEquals("2h 0m", formatListeningSeconds(7200L))
    }
}
