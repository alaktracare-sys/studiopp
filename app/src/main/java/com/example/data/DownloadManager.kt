package com.example.data

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadedSongEntity
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class DownloadManager(private val context: Context) {
    private val songDao = AppDatabase.getInstance(context).songDao()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _downloadProgress = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<Int, Float>> = _downloadProgress.asStateFlow()

    private val downloadsDir: File by lazy {
        val dir = File(context.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    suspend fun isDownloaded(songId: Int): Boolean = withContext(Dispatchers.IO) {
        val localPath = songDao.getLocalPath(songId)
        if (localPath != null) {
            val file = File(localPath)
            if (file.exists() && file.length() > 0) {
                return@withContext true
            } else {
                songDao.deleteDownloadedSong(songId)
            }
        }
        false
    }

    suspend fun downloadSong(song: Song) = withContext(Dispatchers.IO) {
        if (isDownloaded(song.id)) return@withContext

        val targetFile = File(downloadsDir, "${song.id}.mp3")
        _downloadProgress.value = _downloadProgress.value + (song.id to 0f)

        try {
            val request = Request.Builder().url(song.audioUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                _downloadProgress.value = _downloadProgress.value - song.id
                return@withContext
            }

            val body = response.body ?: run {
                _downloadProgress.value = _downloadProgress.value - song.id
                return@withContext
            }

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input: InputStream ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val progress = (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                            _downloadProgress.value = _downloadProgress.value + (song.id to progress)
                        }
                    }
                    output.flush()
                }
            }

            // Record in Room Database
            val entity = DownloadedSongEntity.fromSong(
                song = song,
                localPath = targetFile.absolutePath,
                fileSize = targetFile.length()
            )
            songDao.insertDownloadedSong(entity)
        } catch (e: Exception) {
            e.printStackTrace()
            if (targetFile.exists()) targetFile.delete()
        } finally {
            _downloadProgress.value = _downloadProgress.value - song.id
        }
    }

    suspend fun removeDownload(songId: Int) = withContext(Dispatchers.IO) {
        val path = songDao.getLocalPath(songId)
        if (path != null) {
            val file = File(path)
            if (file.exists()) file.delete()
        }
        songDao.deleteDownloadedSong(songId)
    }

    suspend fun getLocalPath(songId: Int): String? = withContext(Dispatchers.IO) {
        songDao.getLocalPath(songId)
    }
}
