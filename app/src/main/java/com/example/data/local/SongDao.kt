package com.example.data.local

import kotlinx.coroutines.flow.Flow

interface SongDao {
    fun getAllDownloadedSongs(): Flow<List<DownloadedSongEntity>>
    suspend fun getDownloadedSongsList(): List<DownloadedSongEntity>
    suspend fun getDownloadedSong(songId: Int): DownloadedSongEntity?
    suspend fun getLocalPath(songId: Int): String?
    suspend fun insertDownloadedSong(song: DownloadedSongEntity)
    suspend fun deleteDownloadedSong(songId: Int)
    suspend fun deleteAll()
}
