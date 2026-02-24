package com.github.libretube.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.github.libretube.api.JsonHelper
import com.github.libretube.api.RetrofitInstance
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.Request

/**
 * Helper class for Google OAuth2 authentication.
 *
 * Supports two authentication strategies:
 *
 * 1. **Credential Manager** (preferred) - Uses Android's Credential Manager API with
 *    Google Identity. Requires Google Play Services or a compatible microG installation.
 *
 * 2. **Browser-based OAuth2** (fallback) - Opens Google's consent page in a browser or
 *    Custom Tab. Works on any device, including those with microG or no Google services.
 *    The auth code is captured via a custom URI scheme redirect handled by
 *    [com.github.libretube.ui.activities.GoogleOAuthRedirectActivity].
 *
 * The sign-in flow automatically tries Credential Manager first and falls back to
 * the browser flow if it fails (e.g. no GMS available).
 */
object GoogleAuthHelper {
    private const val TAG = "GoogleAuthHelper"

    /**
     * Google OAuth2 token endpoint for exchanging authorization codes / refresh tokens.
     */
    private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"

    /**
     * Required OAuth2 scopes for YouTube Data API access.
     * - youtube.readonly: Read-only access to YouTube account (subscriptions, playlists)
     */
    private const val YOUTUBE_SCOPE = "https://www.googleapis.com/auth/youtube.readonly"

    /**
     * Custom URI scheme redirect for browser-based OAuth2 flow.
     */
    const val REDIRECT_URI = "com.github.libretube:/oauth2callback"

    @Serializable
    data class TokenResponse(
        val access_token: String? = null,
        val refresh_token: String? = null,
        val expires_in: Long? = null,
        val token_type: String? = null,
        val id_token: String? = null,
        val error: String? = null,
        val error_description: String? = null
    )

    /**
     * Result of a sign-in attempt.
     */
    sealed class SignInResult {
        /** Sign-in completed via Credential Manager. Contains ID token and email. */
        data class CredentialManagerSuccess(val idToken: String, val email: String) : SignInResult()

        /** Browser-based OAuth flow was launched. The result will arrive via the redirect activity. */
        data object BrowserFlowLaunched : SignInResult()

        /** Sign-in failed entirely. */
        data class Failed(val error: String) : SignInResult()
    }

    /**
     * Attempt sign-in using Credential Manager first, falling back to browser-based OAuth2.
     *
     * On devices with Google Play Services or a compatible microG, the Credential Manager
     * will show a native account picker. On devices without these, the browser-based flow
     * opens Google's consent page.
     */
    suspend fun signIn(
        context: Context,
        googleClientId: String
    ): SignInResult {
        // Strategy 1: Try Credential Manager (GMS / microG with CM support)
        try {
            val response = signInWithCredentialManager(context, googleClientId)
            val result = processCredentialResponse(response) ?: return SignInResult.Failed(
                "Invalid credential response"
            )
            return SignInResult.CredentialManagerSuccess(result.first, result.second)
        } catch (e: Exception) {
            Log.i(TAG, "Credential Manager unavailable, falling back to browser: ${e.message}")
        }

        // Strategy 2: Browser-based OAuth2 flow (works everywhere)
        return try {
            launchBrowserSignIn(context, googleClientId)
            SignInResult.BrowserFlowLaunched
        } catch (e: Exception) {
            Log.e(TAG, "Browser sign-in also failed", e)
            SignInResult.Failed(e.localizedMessage ?: "Sign-in failed")
        }
    }

    /**
     * Try Credential Manager (throws if GMS/microG is not available).
     */
    private suspend fun signInWithCredentialManager(
        context: Context,
        googleClientId: String
    ): GetCredentialResponse {
        val credentialManager = CredentialManager.create(context)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(googleClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return credentialManager.getCredential(context, request)
    }

    /**
     * Process the credential response from Credential Manager.
     * @return Pair of (idToken, email) or null if the credential is not a Google ID token
     */
    fun processCredentialResponse(response: GetCredentialResponse): Pair<String, String>? {
        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            Log.e(TAG, "Unexpected credential type: ${credential.javaClass.name}")
            return null
        }

        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        return Pair(googleIdTokenCredential.idToken, googleIdTokenCredential.id)
    }

    /**
     * Launch browser-based OAuth2 sign-in flow.
     * This opens Google's consent page in a Custom Tab (preferred) or external browser.
     * The auth code will be captured by [GoogleOAuthRedirectActivity] via the redirect URI.
     */
    fun launchBrowserSignIn(
        context: Context,
        googleClientId: String,
        loginHint: String? = null
    ) {
        val authUrl = buildAuthorizationUrl(googleClientId, REDIRECT_URI, loginHint)

        try {
            // Prefer Custom Tabs for a better in-app experience
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(context, authUrl.toUri())
        } catch (e: Exception) {
            // Fallback to regular browser
            val browserIntent = Intent(Intent.ACTION_VIEW, authUrl.toUri())
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(browserIntent)
        }
    }

    /**
     * Exchange an authorization code for access and refresh tokens.
     * Used by both the Credential Manager flow (ID token exchange) and
     * the browser flow (auth code exchange).
     */
    suspend fun exchangeAuthCode(
        authCode: String,
        googleClientId: String,
        redirectUri: String = ""
    ): TokenResponse = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("code", authCode)
            .add("client_id", googleClientId)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", redirectUri)
            .build()

        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(formBody)
            .build()

        val response = RetrofitInstance.httpClient.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response body")
        JsonHelper.json.decodeFromString<TokenResponse>(body)
    }

    /**
     * Refresh the access token using the stored refresh token.
     * Returns the new access token, or null if the refresh fails.
     */
    suspend fun refreshAccessToken(
        googleClientId: String
    ): String? = withContext(Dispatchers.IO) {
        val refreshToken = PreferenceHelper.getGoogleRefreshToken()
        if (refreshToken.isEmpty()) return@withContext null

        try {
            val formBody = FormBody.Builder()
                .add("refresh_token", refreshToken)
                .add("client_id", googleClientId)
                .add("grant_type", "refresh_token")
                .build()

            val request = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(formBody)
                .build()

            val response = RetrofitInstance.httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val tokenResponse = JsonHelper.json.decodeFromString<TokenResponse>(body)

            if (tokenResponse.access_token != null) {
                PreferenceHelper.setGoogleAccessToken(tokenResponse.access_token)
                tokenResponse.expires_in?.let { expiresIn ->
                    PreferenceHelper.setGoogleTokenExpiry(
                        System.currentTimeMillis() + (expiresIn * 1000)
                    )
                }
                tokenResponse.access_token
            } else {
                Log.e(TAG, "Token refresh failed: ${tokenResponse.error_description}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh token", e)
            null
        }
    }

    /**
     * Get a valid access token, refreshing if necessary.
     * Returns the token formatted as a Bearer authorization header value.
     */
    suspend fun getValidAccessToken(googleClientId: String): String? {
        val accessToken = PreferenceHelper.getGoogleAccessToken()
        if (accessToken.isEmpty()) return null

        val expiry = PreferenceHelper.getGoogleTokenExpiry()

        // Refresh if expired or about to expire (within 5 minutes)
        return if (System.currentTimeMillis() > expiry - 300_000) {
            refreshAccessToken(googleClientId)?.let { "Bearer $it" }
        } else {
            "Bearer $accessToken"
        }
    }

    /**
     * Exchange a Google ID token for OAuth2 tokens that can access YouTube Data API.
     *
     * Since the Credential Manager gives us an ID token (not an authorization code),
     * we use a JWT bearer assertion grant to exchange it for an access token.
     */
    suspend fun exchangeIdTokenForAccessToken(
        idToken: String,
        googleClientId: String
    ): TokenResponse = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", idToken)
            .add("client_id", googleClientId)
            .add("scope", YOUTUBE_SCOPE)
            .build()

        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(formBody)
            .build()

        try {
            val response = RetrofitInstance.httpClient.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response body")
            JsonHelper.json.decodeFromString<TokenResponse>(body)
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange failed", e)
            TokenResponse(error = "exchange_failed", error_description = e.message)
        }
    }

    /**
     * Build the OAuth2 authorization URL for the browser consent flow.
     */
    fun buildAuthorizationUrl(
        googleClientId: String,
        redirectUri: String,
        loginHint: String? = null
    ): String {
        val params = mutableListOf(
            "client_id" to googleClientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to YOUTUBE_SCOPE,
            "access_type" to "offline",
            "prompt" to "consent"
        )
        loginHint?.let { params.add("login_hint" to it) }

        return "https://accounts.google.com/o/oauth2/v2/auth?" +
            params.joinToString("&") { (key, value) ->
                "$key=${Uri.encode(value)}"
            }
    }

    /**
     * Sign out from Google account and clear stored tokens.
     * Attempts to clear Credential Manager state (safe to call even without GMS).
     */
    suspend fun signOut(context: Context) {
        PreferenceHelper.clearGoogleAuth()
        try {
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            // Credential Manager may not be available (no GMS) - that's fine
            Log.d(TAG, "Could not clear credential state (no GMS?): ${e.message}")
        }
    }

    /**
     * Store the tokens from a successful authentication.
     */
    fun saveTokens(tokenResponse: TokenResponse, email: String) {
        tokenResponse.access_token?.let { PreferenceHelper.setGoogleAccessToken(it) }
        tokenResponse.refresh_token?.let { PreferenceHelper.setGoogleRefreshToken(it) }
        tokenResponse.expires_in?.let { expiresIn ->
            PreferenceHelper.setGoogleTokenExpiry(
                System.currentTimeMillis() + (expiresIn * 1000)
            )
        }
        PreferenceHelper.setGoogleEmail(email)
    }
}
