package com.github.libretube.repo

import com.github.libretube.api.obj.StreamItem
import com.github.libretube.db.DatabaseHolder
import com.github.libretube.db.obj.SubscriptionsFeedItem

/**
 * Feed repository for Google account mode.
 * Uses the local feed extraction (same as LocalFeedRepository) since
 * the YouTube Data API doesn't provide a direct "feed" endpoint.
 * Subscriptions are stored locally after import from Google account.
 */
class GoogleAccountFeedRepository : FeedRepository {

    override suspend fun submitFeedItemChange(feedItem: SubscriptionsFeedItem) {
        DatabaseHolder.Database.feedDao().update(feedItem)
    }

    override suspend fun removeChannel(channelId: String) {
        DatabaseHolder.Database.feedDao().delete(channelId)
    }

    override suspend fun getFeed(
        forceRefresh: Boolean,
        onProgressUpdate: (FeedProgress) -> Unit
    ): List<StreamItem> {
        // Delegate to LocalFeedRepository since Google account mode
        // still needs to fetch feed content via local extraction
        // (YouTube Data API activity feed is limited)
        return LocalFeedRepository().getFeed(forceRefresh, onProgressUpdate)
    }
}
