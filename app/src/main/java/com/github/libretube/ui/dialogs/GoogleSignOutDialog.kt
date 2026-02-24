package com.github.libretube.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.github.libretube.R
import com.github.libretube.constants.IntentData
import com.github.libretube.helpers.GoogleAuthHelper
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.ui.preferences.InstanceSettings
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class GoogleSignOutDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val email = PreferenceHelper.getGoogleEmail()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.google_sign_out)
            .setMessage(getString(R.string.google_signed_in_as, email))
            .setPositiveButton(R.string.google_sign_out) { _, _ ->
                lifecycleScope.launch {
                    GoogleAuthHelper.signOut(requireContext())
                }
                Toast.makeText(context, R.string.google_sign_out_success, Toast.LENGTH_SHORT).show()

                setFragmentResult(
                    InstanceSettings.INSTANCE_DIALOG_REQUEST_KEY,
                    bundleOf(IntentData.googleLogoutTask to true)
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
