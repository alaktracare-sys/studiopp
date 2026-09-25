package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), SongDao {

    private val dbScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _downloadedSongsFlow = MutableStateFlow<List<DownloadedSongEntity>>(emptyList())
    private val _listeningHistoryFlow = MutableStateFlow<List<ListeningHistoryEntity>>(emptyList())

    init {
        refreshFlow()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS downloaded_songs (
                song_id INTEGER PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                audio_url TEXT NOT NULL,
                cover_url TEXT NOT NULL,
                local_path TEXT NOT NULL,
                duration REAL NOT NULL,
                downloaded_at INTEGER NOT NULL,
                file_size INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS listening_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                song_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                audio_url TEXT NOT NULL,
                cover_url TEXT NOT NULL,
                duration REAL NOT NULL,
                played_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_played_at ON listening_history(played_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS listening_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    song_id INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    audio_url TEXT NOT NULL,
                    cover_url TEXT NOT NULL,
                    duration REAL NOT NULL,
                    played_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_played_at ON listening_history(played_at DESC)")
        }
    }

    fun songDao(): SongDao = this

    private fun refreshFlow() {
        dbScope.launch {
            try {
                val songs = queryAllDownloadedSongs()
                _downloadedSongsFlow.value = songs
                val history = queryAllHistory()
                _listeningHistoryFlow.value = history
            } catch (_: Exception) {}
        }
    }

    private fun queryAllHistory(): List<ListeningHistoryEntity> {
        val list = mutableListOf<ListeningHistoryEntity>()
        val db = readableDatabase
        val cursor = db.query(
            "listening_history",
            null, null, null, null, null,
            "played_at DESC"
        )
        cursor.use {
            val idCol = it.getColumnIndexOrThrow("id")
            val songIdCol = it.getColumnIndexOrThrow("song_id")
            val titleCol = it.getColumnIndexOrThrow("title")
            val artistCol = it.getColumnIndexOrThrow("artist")
            val audioCol = it.getColumnIndexOrThrow("audio_url")
            val coverCol = it.getColumnIndexOrThrow("cover_url")
            val durCol = it.getColumnIndexOrThrow("duration")
            val dateCol = it.getColumnIndexOrThrow("played_at")

            while (it.moveToNext()) {
                list.add(
                    ListeningHistoryEntity(
                        id = it.getLong(idCol),
                        songId = it.getInt(songIdCol),
                        title = it.getString(titleCol),
                        artist = it.getString(artistCol),
                        audioUrl = it.getString(audioCol),
                        coverUrl = it.getString(coverCol),
                        duration = it.getDouble(durCol),
                        playedAt = it.getLong(dateCol)
                    )
                )
            }
        }
        return list
    }

    private fun queryAllDownloadedSongs(): List<DownloadedSongEntity> {
        val list = mutableListOf<DownloadedSongEntity>()
        val db = readableDatabase
        val cursor = db.query(
            "downloaded_songs",
            null, null, null, null, null,
            "downloaded_at DESC"
        )
        cursor.use {
            val idCol = it.getColumnIndexOrThrow("song_id")
            val titleCol = it.getColumnIndexOrThrow("title")
            val artistCol = it.getColumnIndexOrThrow("artist")
            val audioCol = it.getColumnIndexOrThrow("audio_url")
            val coverCol = it.getColumnIndexOrThrow("cover_url")
            val pathCol = it.getColumnIndexOrThrow("local_path")
            val durCol = it.getColumnIndexOrThrow("duration")
            val dateCol = it.getColumnIndexOrThrow("downloaded_at")
            val sizeCol = it.getColumnIndexOrThrow("file_size")

            while (it.moveToNext()) {
                list.add(
                    DownloadedSongEntity(
                        songId = it.getInt(idCol),
                        title = it.getString(titleCol),
                        artist = it.getString(artistCol),
                        audioUrl = it.getString(audioCol),
                        coverUrl = it.getString(coverCol),
                        localPath = it.getString(pathCol),
                        duration = it.getDouble(durCol),
                        downloadedAt = it.getLong(dateCol),
                        fileSize = it.getLong(sizeCol)
                    )
                )
            }
        }
        return list
    }

    override fun getAllDownloadedSongs(): Flow<List<DownloadedSongEntity>> = _downloadedSongsFlow.asStateFlow()

    override suspend fun getDownloadedSongsList(): List<DownloadedSongEntity> = withContext(Dispatchers.IO) {
        queryAllDownloadedSongs()
    }

    override suspend fun getDownloadedSong(songId: Int): DownloadedSongEntity? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.query(
            "downloaded_songs",
            null,
            "song_id = ?",
            arrayOf(songId.toString()),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                DownloadedSongEntity(
                    songId = it.getInt(it.getColumnIndexOrThrow("song_id")),
                    title = it.getString(it.getColumnIndexOrThrow("title")),
                    artist = it.getString(it.getColumnIndexOrThrow("artist")),
                    audioUrl = it.getString(it.getColumnIndexOrThrow("audio_url")),
                    coverUrl = it.getString(it.getColumnIndexOrThrow("cover_url")),
                    localPath = it.getString(it.getColumnIndexOrThrow("local_path")),
                    duration = it.getDouble(it.getColumnIndexOrThrow("duration")),
                    downloadedAt = it.getLong(it.getColumnIndexOrThrow("downloaded_at")),
                    fileSize = it.getLong(it.getColumnIndexOrThrow("file_size"))
                )
            } else null
        }
    }

    override suspend fun getLocalPath(songId: Int): String? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.query(
            "downloaded_songs",
            arrayOf("local_path"),
            "song_id = ?",
            arrayOf(songId.toString()),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                it.getString(0)
            } else null
        }
    }

    override suspend fun insertDownloadedSong(song: DownloadedSongEntity): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("song_id", song.songId)
            put("title", song.title)
            put("artist", song.artist)
            put("audio_url", song.audioUrl)
            put("cover_url", song.coverUrl)
            put("local_path", song.localPath)
            put("duration", song.duration)
            put("downloaded_at", song.downloadedAt)
            put("file_size", song.fileSize)
        }
        db.insertWithOnConflict("downloaded_songs", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        refreshFlow()
    }

    override suspend fun deleteDownloadedSong(songId: Int): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete("downloaded_songs", "song_id = ?", arrayOf(songId.toString()))
        refreshFlow()
    }

    override suspend fun deleteAll(): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete("downloaded_songs", null, null)
        refreshFlow()
    }

    // ==========================================================
    // LISTENING HISTORY
    // ==========================================================

    fun getAllHistory(): Flow<List<ListeningHistoryEntity>> = _listeningHistoryFlow.asStateFlow()

    suspend fun insertListeningHistory(item: ListeningHistoryEntity): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("song_id", item.songId)
            put("title", item.title)
            put("artist", item.artist)
            put("audio_url", item.audioUrl)
            put("cover_url", item.coverUrl)
            put("duration", item.duration)
            put("played_at", item.playedAt)
        }
        db.insert("listening_history", null, values)
        refreshFlow()
    }

    suspend fun deleteHistoryItem(id: Long): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete("listening_history", "id = ?", arrayOf(id.toString()))
        refreshFlow()
    }

    suspend fun clearAllHistory(): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete("listening_history", null, null)
        refreshFlow()
    }

    suspend fun seedSampleHistoryIfEmpty(songs: List<Song>): Unit = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) return@withContext
        val db = writableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM listening_history", null)
        var count = 0
        cursor.use {
            if (it.moveToFirst()) {
                count = it.getInt(0)
            }
        }
        if (count > 0) return@withContext

        // Seed realistic listening events across the current month (last 30 days)
        val now = System.currentTimeMillis()
        val oneHourMs = 3600_000L
        val oneDayMs = 86400_000L

        // Offsets in milliseconds from 'now' for a diverse, realistic daywise distribution:
        // Today: a few hours ago, 1 hour ago
        // Yesterday: 2 songs
        // 2 days ago: 3 songs
        // 4 days ago: 2 songs
        // 7 days ago: 3 songs
        // 12 days ago: 2 songs
        // 18 days ago: 1 song
        // 24 days ago: 2 songs
        val sampleOffsets = listOf(
            20 * 60_000L,           // 20 mins ago (Today)
            2 * oneHourMs,          // 2 hours ago (Today)
            5 * oneHourMs,          // 5 hours ago (Today)
            oneDayMs + 2 * oneHourMs, // Yesterday
            oneDayMs + 6 * oneHourMs, // Yesterday
            2 * oneDayMs + 3 * oneHourMs, // 2 days ago
            2 * oneDayMs + 8 * oneHourMs, // 2 days ago
            4 * oneDayMs + 4 * oneHourMs, // 4 days ago
            5 * oneDayMs + 2 * oneHourMs, // 5 days ago
            7 * oneDayMs + 5 * oneHourMs, // 7 days ago
            9 * oneDayMs + 1 * oneHourMs, // 9 days ago
            12 * oneDayMs + 7 * oneHourMs, // 12 days ago
            15 * oneDayMs + 3 * oneHourMs, // 15 days ago
            19 * oneDayMs + 6 * oneHourMs, // 19 days ago
            23 * oneDayMs + 2 * oneHourMs, // 23 days ago
            27 * oneDayMs + 4 * oneHourMs  // 27 days ago
        )

        db.beginTransaction()
        try {
            sampleOffsets.forEachIndexed { index, offsetMs ->
                val song = songs[index % songs.size]
                val values = ContentValues().apply {
                    put("song_id", song.id)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("audio_url", song.audioUrl)
                    put("cover_url", song.coverUrl)
                    put("duration", song.duration)
                    put("played_at", now - offsetMs)
                }
                db.insert("listening_history", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshFlow()
    }

    companion object {
        private const val DATABASE_NAME = "already_music.db"
        private const val DATABASE_VERSION = 2

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: AppDatabase(context.applicationContext).also { instance = it }
            }
        }
    }
}
