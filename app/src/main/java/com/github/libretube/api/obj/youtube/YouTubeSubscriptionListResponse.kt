package com.github.libretube.api.obj.youtube

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeSubscriptionListResponse(
    val kind: String? = null,
    val nextPageToken: String? = null,
    val pageInfo: YouTubePageInfo? = null,
    val items: List<YouTubeSubscriptionItem> = emptyList()
)

@Serializable
data class YouTubeSubscriptionItem(
    val kind: String? = null,
    val id: String? = null,
    val snippet: YouTubeSubscriptionSnippet? = null
)

@Serializable
data class YouTubeSubscriptionSnippet(
    val title: String? = null,
    val description: String? = null,
    val resourceId: YouTubeResourceId? = null,
    val thumbnails: YouTubeThumbnails? = null
)

@Serializable
data class YouTubeResourceId(
    val kind: String? = null,
    val channelId: String? = null,
    val videoId: String? = null
)

@Serializable
data class YouTubeThumbnails(
    val default: YouTubeThumbnail? = null,
    val medium: YouTubeThumbnail? = null,
    val high: YouTubeThumbnail? = null
)

@Serializable
data class YouTubeThumbnail(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

@Serializable
data class YouTubePageInfo(
    val totalResults: Int? = null,
    val resultsPerPage: Int? = null
)
