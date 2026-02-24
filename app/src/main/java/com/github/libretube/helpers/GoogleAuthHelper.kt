package com.github.libretube.helpers

import android.content.Context
import android.util.Log
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
 * Helper class for Google OAuth2 authentication using Android Credential Manager.
 *
 * The flow works as follows:
 * 1. User taps "Sign in with Google" -> launches Credential Manager
 * 2. User selects a Google account -> we receive an ID token
 * 3. We exchange the ID token for OAuth2 access + refresh tokens using Google's token endpoint
 * 4. Access token is used for YouTube Data API calls (subscriptions, history)
 * 5. Refresh token is stored to obtain new access tokens when they expire
 *
 * The OAuth2 client ID and secret must be configured for the app. For open-source builds,
 * users can provide their own credentials via the google_client_id string resource.
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
     * Initiates Google Sign-In using Credential Manager and returns the credential response.
     * This must be called from an Activity context for the system UI to display.
     */
    suspend fun signIn(
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
     * Process the credential response from Google Sign-In.
     * Extracts the ID token and email from the Google ID credential.
     *
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
        val idToken = googleIdTokenCredential.idToken
        val email = googleIdTokenCredential.id

        return Pair(idToken, email)
    }

    /**
     * Exchange an authorization code for access and refresh tokens.
     * This uses Google's token endpoint with the authorization_code grant type.
     */
    suspend fun exchangeAuthCode(
        authCode: String,
        googleClientId: String
    ): TokenResponse = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("code", authCode)
            .add("client_id", googleClientId)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", "")
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
     * we use a server-side token exchange approach. The ID token proves the user's identity
     * and can be used with Google's token endpoint when the app is configured with
     * the appropriate OAuth client.
     *
     * For direct YouTube API access, we'll use the ID token to get an access token
     * through Google's OAuth2 authorization flow.
     */
    suspend fun exchangeIdTokenForAccessToken(
        idToken: String,
        googleClientId: String
    ): TokenResponse = withContext(Dispatchers.IO) {
        // Use the ID token exchange grant type
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
     * Build the OAuth2 authorization URL for the consent flow.
     * This URL should be opened in a browser/Custom Tab for the user to grant YouTube access.
     */
    fun buildAuthorizationUrl(
        googleClientId: String,
        redirectUri: String,
        loginHint: String? = null
    ): String {
        val params = mutableMapOf(
            "client_id" to googleClientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to YOUTUBE_SCOPE,
            "access_type" to "offline",
            "prompt" to "consent"
        )
        loginHint?.let { params["login_hint"] = it }

        return "https://accounts.google.com/o/oauth2/v2/auth?" +
                params.entries.joinToString("&") { "${it.key}=${it.value}" }
    }

    /**
     * Sign out from Google account and clear stored tokens.
     */
    suspend fun signOut(context: Context) {
        PreferenceHelper.clearGoogleAuth()
        try {
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear credential state", e)
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
