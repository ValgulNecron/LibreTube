package com.github.libretube.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.github.libretube.R
import com.github.libretube.constants.IntentData
import com.github.libretube.extensions.TAG
import com.github.libretube.extensions.toastFromMainDispatcher
import com.github.libretube.helpers.GoogleAuthHelper
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.ui.preferences.InstanceSettings.Companion.INSTANCE_DIALOG_REQUEST_KEY
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Dialog that handles Google Sign-In.
 *
 * Shows a privacy warning, then attempts sign-in via two strategies:
 * 1. Credential Manager (if GMS/microG available) - native account picker
 * 2. Browser-based OAuth2 (fallback) - opens Google consent page in browser
 *
 * The browser flow completes asynchronously via [GoogleOAuthRedirectActivity],
 * while the Credential Manager flow completes within this dialog.
 */
class GoogleSignInDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.google_privacy_warning)
            .setMessage(R.string.google_privacy_warning_message)
            .setPositiveButton(R.string.proceed) { _, _ ->
                startGoogleSignIn()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    private fun startGoogleSignIn() {
        val activity = requireActivity()
        val googleClientId = getString(R.string.google_client_id)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                when (val result = GoogleAuthHelper.signIn(activity, googleClientId)) {
                    is GoogleAuthHelper.SignInResult.CredentialManagerSuccess -> {
                        handleCredentialManagerSuccess(result, googleClientId)
                    }

                    is GoogleAuthHelper.SignInResult.BrowserFlowLaunched -> {
                        // Browser was opened - auth will complete via GoogleOAuthRedirectActivity.
                        context?.toastFromMainDispatcher(
                            getString(R.string.google_browser_sign_in_launched)
                        )
                    }

                    is GoogleAuthHelper.SignInResult.Failed -> {
                        context?.toastFromMainDispatcher(
                            getString(R.string.google_sign_in_failed, result.error)
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG(), "Google Sign-In failed", e)
                context?.toastFromMainDispatcher(
                    getString(R.string.google_sign_in_failed, e.localizedMessage ?: "Unknown error")
                )
            }
        }
    }

    private suspend fun handleCredentialManagerSuccess(
        result: GoogleAuthHelper.SignInResult.CredentialManagerSuccess,
        googleClientId: String
    ) {
        val (idToken, email) = result

        // Try to exchange the ID token for an access token
        val tokenResponse = GoogleAuthHelper.exchangeIdTokenForAccessToken(
            idToken, googleClientId
        )

        if (tokenResponse.access_token != null) {
            GoogleAuthHelper.saveTokens(tokenResponse, email)
        } else {
            // Even if token exchange fails, store the email and ID token
            // so the user can use the browser-based flow as a retry
            PreferenceHelper.setGoogleEmail(email)
            PreferenceHelper.setGoogleAccessToken(idToken)
            PreferenceHelper.setGoogleTokenExpiry(
                System.currentTimeMillis() + 3600_000 // 1 hour default
            )
        }

        context?.toastFromMainDispatcher(R.string.google_sign_in_success)

        setFragmentResult(
            INSTANCE_DIALOG_REQUEST_KEY,
            bundleOf(IntentData.googleLoginTask to true)
        )
    }
}
