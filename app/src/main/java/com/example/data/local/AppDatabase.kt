package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AppDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), SongDao {

    private val _downloadedSongsFlow = MutableStateFlow<List<DownloadedSongEntity>>(emptyList())

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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS downloaded_songs")
        onCreate(db)
    }

    fun songDao(): SongDao = this

    private fun refreshFlow() {
        try {
            val list = queryAllDownloadedSongs()
            _downloadedSongsFlow.value = list
        } catch (_: Exception) {}
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

    companion object {
        private const val DATABASE_NAME = "already_music.db"
        private const val DATABASE_VERSION = 1

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: AppDatabase(context.applicationContext).also { instance = it }
            }
        }
    }
}
