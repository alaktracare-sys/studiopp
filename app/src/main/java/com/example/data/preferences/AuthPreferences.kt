package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.model.User
import org.json.JSONArray
import org.json.JSONObject

class AuthPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("already_prefs", Context.MODE_PRIVATE)

    fun saveUser(userId: Int, username: String, email: String, token: String? = null) {
        prefs.edit()
            .putInt(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .putString(KEY_EMAIL, email)
            .putString(KEY_TOKEN, token ?: "")
            .apply()
    }

    fun getUser(): User? {
        val id = prefs.getInt(KEY_USER_ID, -1)
        if (id == -1) return null
        val username = prefs.getString(KEY_USERNAME, "User") ?: "User"
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        return User(id, username, email)
    }

    fun isLoggedIn(): Boolean {
        return prefs.getInt(KEY_USER_ID, -1) != -1
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun getLikedSongIds(): Set<Int> {
        val set = prefs.getStringSet(KEY_LIKED_SONGS, emptySet()) ?: emptySet()
        return set.mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun setSongLiked(songId: Int, isLiked: Boolean) {
        val current = getLikedSongIds().toMutableSet()
        if (isLiked) {
            current.add(songId)
        } else {
            current.remove(songId)
        }
        prefs.edit()
            .putStringSet(KEY_LIKED_SONGS, current.map { it.toString() }.toSet())
            .apply()
    }

    fun getOfflineQueue(): List<JSONObject> {
        val json = prefs.getString(KEY_OFFLINE_QUEUE, "[]") ?: "[]"
        val list = mutableListOf<JSONObject>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                list.add(array.getJSONObject(i))
            }
        } catch (_: Exception) {}
        return list
    }

    fun addOfflineAction(action: JSONObject) {
        val current = getOfflineQueue().toMutableList()
        current.add(action)
        val array = JSONArray()
        current.forEach { array.put(it) }
        prefs.edit().putString(KEY_OFFLINE_QUEUE, array.toString()).apply()
    }

    fun clearOfflineQueue() {
        prefs.edit().putString(KEY_OFFLINE_QUEUE, "[]").apply()
    }

    fun getServerBaseUrl(): String {
        val url = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        return if (url.endsWith("/")) url else "$url/"
    }

    fun setServerBaseUrl(url: String) {
        val normalized = url.trim().let { if (it.endsWith("/")) it else "$it/" }
        prefs.edit().putString(KEY_SERVER_URL, normalized).apply()
    }

    fun getLikedPlaylistId(): Int {
        return prefs.getInt(KEY_LIKED_PLAYLIST_ID, -1)
    }

    fun setLikedPlaylistId(id: Int) {
        prefs.edit().putInt(KEY_LIKED_PLAYLIST_ID, id).apply()
    }

    companion object {
        const val DEFAULT_SERVER_URL = "http://100.65.126.106:8000/"
        private const val KEY_SERVER_URL = "server_base_url"
        private const val KEY_LIKED_PLAYLIST_ID = "liked_playlist_id"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_EMAIL = "email"
        private const val KEY_TOKEN = "token"
        private const val KEY_LIKED_SONGS = "liked_song_ids"
        private const val KEY_OFFLINE_QUEUE = "offline_action_queue"
    }
}
