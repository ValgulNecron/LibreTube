package com.github.libretube.helpers

import androidx.room.withTransaction
import com.github.libretube.api.GooglePipedAuthApi
import com.github.libretube.api.RetrofitInstance
import com.github.libretube.db.DatabaseHolder.Database
import com.github.libretube.db.obj.WatchHistoryItem
import com.github.libretube.enums.AccountType
import com.github.libretube.extensions.toLocalDate

object GoogleHistorySync {
    suspend fun syncHistory() {
        if (PreferenceHelper.getAccountType() != AccountType.GOOGLE) return

        val authApi = RetrofitInstance.authApi
        if (authApi is GooglePipedAuthApi) {
             val token = PreferenceHelper.getToken()
             try {
                 val youTubeApi = authApi.youTubeApi
                 val channelResponse = youTubeApi.getMyChannel("Bearer $token")
                 val historyId = channelResponse.items.firstOrNull()?.contentDetails?.relatedPlaylists?.watchHistory

                 if (historyId != null) {
                      val historyItems = youTubeApi.getPlaylistItems("Bearer $token", historyId)

                      val dbItems = historyItems.items.mapNotNull { item ->
                          val videoId = item.snippet.resourceId.videoId
                          if (videoId == null) return@mapNotNull null

                          WatchHistoryItem(
                              videoId = videoId,
                              title = item.snippet.title,
                              uploader = item.snippet.channelTitle ?: "",
                              uploaderUrl = "/channel/${item.snippet.channelId}",
                              uploadDate = item.snippet.publishedAt.toLocalDate(),
                              thumbnailUrl = item.snippet.thumbnails?.medium?.url,
                              uploaderAvatar = null,
                              duration = 0, // Unknown
                              isShort = false // Unknown
                          )
                      }

                      Database.withTransaction {
                          Database.watchHistoryDao().insertAll(dbItems.reversed())
                      }
                 }
             } catch (e: Exception) {
                 e.printStackTrace()
             }
        }
    }
}
