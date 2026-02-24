package com.github.libretube.ui.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.github.libretube.R
import com.github.libretube.api.JsonHelper
import com.github.libretube.api.RetrofitInstance
import com.github.libretube.extensions.TAG
import com.github.libretube.helpers.GoogleAuthHelper
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.ui.base.BaseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.Request

/**
 * Activity that captures the OAuth2 redirect from the browser-based Google Sign-In flow.
 *
 * This is used as a fallback when Credential Manager is unavailable (e.g. on devices
 * running microG or no Google Play Services at all).
 *
 * The flow:
 * 1. User opens Google's OAuth consent page in a browser
 * 2. After granting access, Google redirects to our custom scheme:
 *    com.github.libretube:/oauth2callback?code=AUTH_CODE
 * 3. This activity intercepts that redirect via the intent filter
 * 4. We exchange the auth code for access + refresh tokens
 * 5. Tokens are stored, and the user is redirected back to settings
 */
class GoogleOAuthRedirectActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }

        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")

        if (error != null) {
            Log.e(TAG(), "OAuth error: $error")
            finish()
            return
        }

        if (code == null) {
            Log.e(TAG(), "No authorization code in redirect")
            finish()
            return
        }

        val googleClientId = getString(R.string.google_client_id)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tokenResponse = GoogleAuthHelper.exchangeAuthCode(
                    authCode = code,
                    googleClientId = googleClientId,
                    redirectUri = REDIRECT_URI
                )

                if (tokenResponse.access_token != null) {
                    // Fetch user email from the ID token or userinfo endpoint
                    val email = fetchUserEmail(tokenResponse.access_token) ?: ""
                    GoogleAuthHelper.saveTokens(tokenResponse, email)
                    Log.i(TAG(), "Browser OAuth flow succeeded for $email")
                } else {
                    Log.e(TAG(), "Token exchange failed: ${tokenResponse.error_description}")
                }
            } catch (e: Exception) {
                Log.e(TAG(), "Failed to exchange auth code", e)
            } finally {
                // Navigate back to the main activity / settings
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (intent != null) startActivity(intent)
                finish()
            }
        }
    }

    /**
     * Fetch the user's email from Google's userinfo endpoint using the access token.
     */
    private fun fetchUserEmail(accessToken: String): String? {
        return try {
            val request = Request.Builder()
                .url("https://www.googleapis.com/oauth2/v2/userinfo")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            val response = RetrofitInstance.httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return null
            val json = JsonHelper.json.parseToJsonElement(body)
            json.takeIf {
                it is kotlinx.serialization.json.JsonObject
            }?.let {
                (it as kotlinx.serialization.json.JsonObject)["email"]
                    ?.takeIf { e -> e is kotlinx.serialization.json.JsonPrimitive }
                    ?.let { e -> (e as kotlinx.serialization.json.JsonPrimitive).content }
            }
        } catch (e: Exception) {
            Log.e(TAG(), "Failed to fetch user email", e)
            null
        }
    }

    companion object {
        const val REDIRECT_URI = "com.github.libretube:/oauth2callback"
    }
}
