package com.github.libretube.helpers

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GoogleAuthManager {
    // TODO: Replace with your actual Client ID from Google Cloud Console if needed for backend verification.
    // For client-side API access, the package name and SHA-1 in Google Cloud Console is crucial.
    const val GOOGLE_CLIENT_ID = "YOUR_CLIENT_ID_HERE.apps.googleusercontent.com"

    private const val SCOPE_YOUTUBE = "https://www.googleapis.com/auth/youtube"

    private fun getSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SCOPE_YOUTUBE))
            .build()
    }

    fun getSignInIntent(context: Context): Intent {
        val client = GoogleSignIn.getClient(context, getSignInOptions())
        return client.signInIntent
    }

    suspend fun signOut(context: Context) {
        val client = GoogleSignIn.getClient(context, getSignInOptions())
        withContext(Dispatchers.IO) {
            client.signOut()
        }
    }

    /**
     * Retrieves the OAuth 2.0 Access Token for the signed-in user.
     * This must be called on a background thread.
     */
    suspend fun getAccessToken(context: Context): String? {
        return withContext(Dispatchers.IO) {
            val account = GoogleSignIn.getLastSignedInAccount(context)?.account ?: return@withContext null
            try {
                // "oauth2:" + scope
                val scope = "oauth2:$SCOPE_YOUTUBE"
                GoogleAuthUtil.getToken(context, account, scope)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun invalidateToken(context: Context, token: String) {
        withContext(Dispatchers.IO) {
            try {
                GoogleAuthUtil.clearToken(context, token)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun isSignedIn(context: Context): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null
    }
}
