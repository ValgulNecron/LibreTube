package com.github.libretube.api

import com.github.libretube.api.google.PlaylistInput
import com.github.libretube.api.google.PlaylistItemInput
import com.github.libretube.api.google.PlaylistUpdateInput
import com.github.libretube.api.google.SubscriptionInput
import com.github.libretube.api.google.YouTubeChannel
import com.github.libretube.api.google.YouTubeListResponse
import com.github.libretube.api.google.YouTubePlaylist
import com.github.libretube.api.google.YouTubePlaylistItem
import com.github.libretube.api.google.YouTubeSubscription
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

interface YouTubeAuthApi {

    @GET("subscriptions")
    suspend fun getSubscriptions(
        @Header("Authorization") token: String,
        @Query("mine") mine: Boolean = true,
        @Query("part") part: String = "snippet,contentDetails",
        @Query("maxResults") maxResults: Int = 50,
        @Query("pageToken") pageToken: String? = null
    ): YouTubeListResponse<YouTubeSubscription>

    @GET("subscriptions")
    suspend fun getSubscriptionForChannel(
        @Header("Authorization") token: String,
        @Query("forChannelId") channelId: String,
        @Query("mine") mine: Boolean = true,
        @Query("part") part: String = "snippet"
    ): YouTubeListResponse<YouTubeSubscription>

    @POST("subscriptions")
    suspend fun insertSubscription(
        @Header("Authorization") token: String,
        @Query("part") part: String = "snippet",
        @Body body: SubscriptionInput
    ): YouTubeSubscription

    @DELETE("subscriptions")
    suspend fun deleteSubscription(
        @Header("Authorization") token: String,
        @Query("id") id: String
    )

    @GET("playlists")
    suspend fun getPlaylists(
        @Header("Authorization") token: String,
        @Query("mine") mine: Boolean = true,
        @Query("part") part: String = "snippet,contentDetails",
        @Query("maxResults") maxResults: Int = 50,
        @Query("pageToken") pageToken: String? = null
    ): YouTubeListResponse<YouTubePlaylist>

    @GET("playlists")
    suspend fun getPlaylistById(
        @Header("Authorization") token: String,
        @Query("id") id: String,
        @Query("part") part: String = "snippet,contentDetails"
    ): YouTubeListResponse<YouTubePlaylist>

    @POST("playlists")
    suspend fun insertPlaylist(
        @Header("Authorization") token: String,
        @Query("part") part: String = "snippet,status",
        @Body body: PlaylistInput
    ): YouTubePlaylist

    @DELETE("playlists")
    suspend fun deletePlaylist(
        @Header("Authorization") token: String,
        @Query("id") id: String
    )

    @PUT("playlists")
    suspend fun updatePlaylist(
        @Header("Authorization") token: String,
        @Query("part") part: String = "snippet,status",
        @Body body: PlaylistUpdateInput
    ): YouTubePlaylist

    @GET("playlistItems")
    suspend fun getPlaylistItems(
        @Header("Authorization") token: String,
        @Query("playlistId") playlistId: String,
        @Query("part") part: String = "snippet,contentDetails",
        @Query("maxResults") maxResults: Int = 50,
        @Query("pageToken") pageToken: String? = null
    ): YouTubeListResponse<YouTubePlaylistItem>

    @POST("playlistItems")
    suspend fun insertPlaylistItem(
        @Header("Authorization") token: String,
        @Query("part") part: String = "snippet",
        @Body body: PlaylistItemInput
    ): YouTubePlaylistItem

    @DELETE("playlistItems")
    suspend fun deletePlaylistItem(
        @Header("Authorization") token: String,
        @Query("id") id: String
    )

    @GET("channels")
    suspend fun getMyChannel(
        @Header("Authorization") token: String,
        @Query("mine") mine: Boolean = true,
        @Query("part") part: String = "snippet,contentDetails"
    ): YouTubeListResponse<YouTubeChannel>
}
