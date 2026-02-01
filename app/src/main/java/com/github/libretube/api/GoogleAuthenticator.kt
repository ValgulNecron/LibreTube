package com.github.libretube.api

import com.github.libretube.LibreTubeApp
import com.github.libretube.enums.AccountType
import com.github.libretube.helpers.GoogleAuthManager
import com.github.libretube.helpers.PreferenceHelper
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class GoogleAuthenticator : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (PreferenceHelper.getAccountType() != AccountType.GOOGLE) return null

        if (responseCount(response) >= 2) return null

        val currentToken = PreferenceHelper.getToken()

        val newToken = runBlocking {
            GoogleAuthManager.invalidateToken(LibreTubeApp.instance, currentToken)
            GoogleAuthManager.getAccessToken(LibreTubeApp.instance)
        } ?: return null

        PreferenceHelper.setToken(newToken)

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
