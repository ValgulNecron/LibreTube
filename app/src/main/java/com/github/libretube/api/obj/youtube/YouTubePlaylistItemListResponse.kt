package com.github.libretube.api.obj.youtube

import kotlinx.serialization.Serializable

@Serializable
data class YouTubePlaylistItemListResponse(
    val kind: String? = null,
    val nextPageToken: String? = null,
    val pageInfo: YouTubePageInfo? = null,
    val items: List<YouTubePlaylistItem> = emptyList()
)

@Serializable
data class YouTubePlaylistItem(
    val kind: String? = null,
    val id: String? = null,
    val snippet: YouTubePlaylistItemSnippet? = null
)

@Serializable
data class YouTubePlaylistItemSnippet(
    val title: String? = null,
    val description: String? = null,
    val thumbnails: YouTubeThumbnails? = null,
    val channelTitle: String? = null,
    val videoOwnerChannelTitle: String? = null,
    val videoOwnerChannelId: String? = null,
    val resourceId: YouTubeResourceId? = null,
    val publishedAt: String? = null
)
