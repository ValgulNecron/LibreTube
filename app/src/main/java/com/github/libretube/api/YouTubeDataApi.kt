package com.github.libretube.api

import com.github.libretube.api.obj.youtube.YouTubeChannelListResponse
import com.github.libretube.api.obj.youtube.YouTubePlaylistItemListResponse
import com.github.libretube.api.obj.youtube.YouTubeSubscriptionListResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * YouTube Data API v3 interface for fetching subscriptions and watch history
 * from a Google account.
 */
interface YouTubeDataApi {

    @GET("subscriptions")
    suspend fun getSubscriptions(
        @Header("Authorization") accessToken: String,
        @Query("part") part: String = "snippet",
        @Query("mine") mine: Boolean = true,
        @Query("maxResults") maxResults: Int = 50,
        @Query("pageToken") pageToken: String? = null
    ): YouTubeSubscriptionListResponse

    @GET("channels")
    suspend fun getChannel(
        @Header("Authorization") accessToken: String,
        @Query("part") part: String = "snippet",
        @Query("id") channelId: String
    ): YouTubeChannelListResponse

    @GET("playlistItems")
    suspend fun getPlaylistItems(
        @Header("Authorization") accessToken: String,
        @Query("part") part: String = "snippet",
        @Query("playlistId") playlistId: String,
        @Query("maxResults") maxResults: Int = 50,
        @Query("pageToken") pageToken: String? = null
    ): YouTubePlaylistItemListResponse

    @GET("channels")
    suspend fun getMyChannel(
        @Header("Authorization") accessToken: String,
        @Query("part") part: String = "snippet,contentDetails",
        @Query("mine") mine: Boolean = true
    ): YouTubeChannelListResponse
}
