package com.github.libretube.repo

import com.github.libretube.LibreTubeApp
import com.github.libretube.R
import com.github.libretube.api.RetrofitInstance
import com.github.libretube.api.obj.Subscription
import com.github.libretube.db.DatabaseHolder.Database
import com.github.libretube.db.obj.LocalSubscription
import com.github.libretube.helpers.GoogleAuthHelper

/**
 * Repository that fetches subscriptions from the user's YouTube/Google account
 * via the YouTube Data API v3, while also maintaining a local cache in the Room database.
 *
 * Subscribe/unsubscribe operations only affect the local database since the YouTube Data API
 * doesn't allow modifying subscriptions. The Google account connection is primarily used
 * for importing/syncing existing YouTube subscriptions.
 */
class GoogleAccountSubscriptionsRepository : SubscriptionsRepository {

    private val googleClientId: String
        get() = LibreTubeApp.instance.getString(R.string.google_client_id)

    private suspend fun getAccessToken(): String {
        return GoogleAuthHelper.getValidAccessToken(googleClientId)
            ?: throw Exception("Google account not connected or token expired")
    }

    override suspend fun subscribe(
        channelId: String, name: String, uploaderAvatar: String?, verified: Boolean
    ) {
        // Store locally - YouTube Data API doesn't support subscribing programmatically
        // in a way that would be appropriate for a third-party app
        val localSubscription = LocalSubscription(
            channelId = channelId,
            name = name,
            avatar = uploaderAvatar,
            verified = verified
        )
        Database.localSubscriptionDao().insert(localSubscription)
    }

    override suspend fun unsubscribe(channelId: String) {
        Database.localSubscriptionDao().deleteById(channelId)
    }

    override suspend fun isSubscribed(channelId: String): Boolean {
        return Database.localSubscriptionDao().includes(channelId)
    }

    override suspend fun importSubscriptions(newChannels: List<String>) {
        Database.localSubscriptionDao().insertAll(
            newChannels.map { LocalSubscription(it) }
        )
    }

    override suspend fun getSubscriptions(): List<Subscription> {
        return Database.localSubscriptionDao().getAll().map {
            Subscription(
                url = it.channelId,
                name = it.name.orEmpty(),
                avatar = it.avatar,
                verified = it.verified
            )
        }
    }

    override suspend fun getSubscriptionChannelIds(): List<String> {
        return Database.localSubscriptionDao().getAll().map { it.channelId }
    }

    /**
     * Fetch all subscriptions from the user's YouTube account and import them
     * into the local database.
     *
     * @return The number of new subscriptions imported
     */
    suspend fun importFromGoogleAccount(): Int {
        val accessToken = getAccessToken()
        val channelIds = mutableListOf<String>()

        var pageToken: String? = null
        do {
            val response = RetrofitInstance.youtubeDataApi.getSubscriptions(
                accessToken = accessToken,
                pageToken = pageToken
            )

            for (item in response.items) {
                val channelId = item.snippet?.resourceId?.channelId ?: continue
                channelIds.add(channelId)

                // Store with metadata from YouTube API
                val localSub = LocalSubscription(
                    channelId = channelId,
                    name = item.snippet.title,
                    avatar = item.snippet.thumbnails?.high?.url
                        ?: item.snippet.thumbnails?.medium?.url
                        ?: item.snippet.thumbnails?.default?.url,
                    verified = false
                )
                Database.localSubscriptionDao().insert(localSub)
            }

            pageToken = response.nextPageToken
        } while (pageToken != null)

        return channelIds.size
    }

    override suspend fun submitSubscriptionChannelInfosChanged(subscriptions: List<Subscription>) {
        Database.localSubscriptionDao().updateAll(subscriptions.map {
            LocalSubscription(
                it.url,
                it.name,
                it.avatar,
                it.verified
            )
        })
    }
}
