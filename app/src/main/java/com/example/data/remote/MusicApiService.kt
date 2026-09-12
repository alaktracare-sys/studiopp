package com.example.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

// ----------------- AUTH MODELS -----------------

data class LoginRequest(
    val identifier: String,
    val password: String
)

data class LoginResponse(
    val message: String? = null,
    @SerializedName("user_id") val userId: Int,
    val username: String,
    val email: String
)

data class SignupRequest(
    val username: String,
    val email: String,
    val password: String,
    @SerializedName("confirm_password") val confirmPassword: String
)

data class VerifyOtpRequest(
    val email: String,
    val otp: String
)

data class MessageResponse(
    val message: String? = null,
    val detail: String? = null
)

// ----------------- SONG MODELS -----------------

data class SongDto(
    val id: Int,
    val title: String,
    val artist: String,
    @SerializedName("audio_url") val audioUrl: String,
    @SerializedName("cover_url") val coverUrl: String,
    val duration: Double = 0.0
)

// ----------------- PLAYLIST MODELS -----------------

data class ServerPlaylistDto(
    val id: Int,
    @SerializedName("user_id") val userId: Int,
    val name: String,
    @SerializedName("is_system") val isSystem: Int = 0
)

data class LikedPlaylistResponse(
    @SerializedName("playlist_id") val playlistId: Int? = null,
    val error: String? = null
)

data class CreatePlaylistResponse(
    val success: Boolean,
    val id: Int? = null,
    val name: String? = null,
    val error: String? = null
)

data class GenericSuccessResponse(
    val success: Boolean = false,
    val name: String? = null,
    val error: String? = null,
    val message: String? = null
)

// ----------------- RETROFIT INTERFACE -----------------

interface MusicApiService {

    // === AUTH ===
    @POST("auth/signup")
    suspend fun signup(@Body req: SignupRequest): Response<MessageResponse>

    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body req: VerifyOtpRequest): Response<MessageResponse>

    @POST("auth/login")
    suspend fun login(@Body req: LoginRequest): Response<LoginResponse>

    // === SONGS ===
    @GET("songs")
    suspend fun getSongs(): Response<List<SongDto>>

    @GET("songs/search")
    suspend fun searchSongs(
        @Query("q") query: String,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0
    ): Response<List<SongDto>>

    @Multipart
    @POST("songs/upload")
    suspend fun uploadSong(
        @Part("title") title: RequestBody,
        @Part("artist") artist: RequestBody,
        @Part audio: MultipartBody.Part,
        @Part cover: MultipartBody.Part
    ): Response<GenericSuccessResponse>

    // === PLAYLISTS ===
    @GET("playlists/{user_id}")
    suspend fun getPlaylists(@Path("user_id") userId: Int): Response<List<ServerPlaylistDto>>

    @GET("playlists/liked/{user_id}")
    suspend fun getLikedPlaylist(@Path("user_id") userId: Int): Response<LikedPlaylistResponse>

    @GET("playlists/{playlist_id}/songs")
    suspend fun getPlaylistSongs(@Path("playlist_id") playlistId: Int): Response<List<SongDto>>

    @FormUrlEncoded
    @POST("playlists/create")
    suspend fun createPlaylist(
        @Field("user_id") userId: Int,
        @Field("name") name: String
    ): Response<CreatePlaylistResponse>

    @FormUrlEncoded
    @POST("playlists/rename")
    suspend fun renamePlaylist(
        @Field("playlist_id") playlistId: Int,
        @Field("user_id") userId: Int,
        @Field("name") name: String
    ): Response<GenericSuccessResponse>

    @FormUrlEncoded
    @POST("playlists/delete")
    suspend fun deletePlaylist(
        @Field("playlist_id") playlistId: Int
    ): Response<GenericSuccessResponse>

    @FormUrlEncoded
    @POST("playlists/add-song")
    suspend fun addSongToPlaylist(
        @Field("playlist_id") playlistId: Int,
        @Field("song_id") songId: Int
    ): Response<GenericSuccessResponse>

    @FormUrlEncoded
    @POST("playlists/remove-song")
    suspend fun removeSongFromPlaylist(
        @Field("playlist_id") playlistId: Int,
        @Field("song_id") songId: Int
    ): Response<GenericSuccessResponse>
}
