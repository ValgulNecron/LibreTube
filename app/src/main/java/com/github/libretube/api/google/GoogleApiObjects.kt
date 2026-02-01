package com.github.libretube.api.google

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeListResponse<T>(
    val kind: String? = null,
    val etag: String? = null,
    val nextPageToken: String? = null,
    val prevPageToken: String? = null,
    val pageInfo: PageInfo? = null,
    val items: List<T> = emptyList()
)

@Serializable
data class PageInfo(
    val totalResults: Int,
    val resultsPerPage: Int
)

@Serializable
data class YouTubeSubscription(
    val kind: String? = null,
    val etag: String? = null,
    val id: String,
    val snippet: SubscriptionSnippet
)

@Serializable
data class SubscriptionSnippet(
    val publishedAt: String,
    val title: String,
    val description: String? = null,
    val resourceId: ResourceId,
    val channelId: String? = null,
    val thumbnails: Thumbnails? = null
)

@Serializable
data class ResourceId(
    val kind: String? = null,
    val channelId: String? = null, // for subscriptions
    val videoId: String? = null // for playlist items
)

@Serializable
data class Thumbnails(
    val default: Thumbnail? = null,
    val medium: Thumbnail? = null,
    val high: Thumbnail? = null,
    val standard: Thumbnail? = null,
    val maxres: Thumbnail? = null
)

@Serializable
data class Thumbnail(
    val url: String,
    val width: Int? = null,
    val height: Int? = null
)

@Serializable
data class YouTubePlaylist(
    val kind: String? = null,
    val etag: String? = null,
    val id: String,
    val snippet: PlaylistSnippet,
    val contentDetails: PlaylistContentDetails? = null
)

@Serializable
data class PlaylistSnippet(
    val publishedAt: String,
    val channelId: String,
    val title: String,
    val description: String? = null,
    val thumbnails: Thumbnails? = null,
    val channelTitle: String? = null
)

@Serializable
data class PlaylistContentDetails(
    val itemCount: Int
)

@Serializable
data class YouTubePlaylistItem(
    val kind: String? = null,
    val etag: String? = null,
    val id: String,
    val snippet: PlaylistItemSnippet
)

@Serializable
data class PlaylistItemSnippet(
    val publishedAt: String,
    val channelId: String,
    val title: String,
    val description: String? = null,
    val thumbnails: Thumbnails? = null,
    val channelTitle: String? = null,
    val resourceId: ResourceId,
    val position: Int? = null
)

@Serializable
data class YouTubeChannel(
    val kind: String? = null,
    val etag: String? = null,
    val id: String,
    val snippet: ChannelSnippet? = null,
    val contentDetails: ChannelContentDetails? = null
)

@Serializable
data class ChannelSnippet(
    val title: String,
    val description: String? = null,
    val publishedAt: String? = null,
    val thumbnails: Thumbnails? = null
)

@Serializable
data class ChannelContentDetails(
    val relatedPlaylists: RelatedPlaylists
)

@Serializable
data class RelatedPlaylists(
    val likes: String? = null,
    val uploads: String? = null,
    val watchHistory: String? = null,
    val watchLater: String? = null
)

// Input objects for POST requests

@Serializable
data class SubscriptionInput(
    val snippet: SubscriptionSnippetInput
)

@Serializable
data class SubscriptionSnippetInput(
    val resourceId: ResourceId
)

@Serializable
data class PlaylistInput(
    val snippet: PlaylistSnippetInput,
    val status: PlaylistStatusInput? = null
)

@Serializable
data class PlaylistSnippetInput(
    val title: String,
    val description: String? = null
)

@Serializable
data class PlaylistStatusInput(
    val privacyStatus: String // private, public, unlisted
)

@Serializable
data class PlaylistUpdateInput(
    val id: String,
    val snippet: PlaylistSnippetInput,
    val status: PlaylistStatusInput? = null
)

@Serializable
data class PlaylistItemInput(
    val snippet: PlaylistItemSnippetInput
)

@Serializable
data class PlaylistItemSnippetInput(
    val playlistId: String,
    val resourceId: ResourceId
)
