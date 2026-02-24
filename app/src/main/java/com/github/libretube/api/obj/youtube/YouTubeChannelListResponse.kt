package com.github.libretube.api.obj.youtube

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeChannelListResponse(
    val kind: String? = null,
    val items: List<YouTubeChannelItem> = emptyList()
)

@Serializable
data class YouTubeChannelItem(
    val kind: String? = null,
    val id: String? = null,
    val snippet: YouTubeChannelSnippet? = null,
    val contentDetails: YouTubeChannelContentDetails? = null
)

@Serializable
data class YouTubeChannelSnippet(
    val title: String? = null,
    val description: String? = null,
    val thumbnails: YouTubeThumbnails? = null
)

@Serializable
data class YouTubeChannelContentDetails(
    val relatedPlaylists: YouTubeRelatedPlaylists? = null
)

@Serializable
data class YouTubeRelatedPlaylists(
    val likes: String? = null,
    val uploads: String? = null
)
