package com.github.libretube.ui.preferences

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.libretube.R
import com.github.libretube.api.PipedMediaServiceRepository
import com.github.libretube.api.RetrofitInstance
import com.github.libretube.api.obj.PipedInstance
import com.github.libretube.constants.IntentData
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.databinding.SimpleOptionsRecyclerBinding
import com.github.libretube.extensions.TAG
import com.github.libretube.extensions.toastFromMainThread
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.repo.GoogleAccountSubscriptionsRepository
import com.github.libretube.ui.adapters.InstancesAdapter
import com.github.libretube.ui.base.BasePreferenceFragment
import com.github.libretube.ui.dialogs.CreateCustomInstanceDialog
import com.github.libretube.ui.dialogs.CustomInstancesListDialog
import com.github.libretube.ui.dialogs.DeleteAccountDialog
import com.github.libretube.ui.dialogs.GoogleSignInDialog
import com.github.libretube.ui.dialogs.GoogleSignOutDialog
import com.github.libretube.ui.dialogs.LoginDialog
import com.github.libretube.ui.dialogs.LogoutDialog
import com.github.libretube.ui.models.InstancesModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl

class InstanceSettings : BasePreferenceFragment() {
    private val token get() = PreferenceHelper.getToken()
    private var instances = mutableListOf<PipedInstance>()
    private val customInstancesModel: InstancesModel by activityViewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.instance_settings, rootKey)

        val instancePref = findPreference<ListPreference>(PreferenceKeys.FETCH_INSTANCE)!!
        val authInstanceToggle = findPreference<SwitchPreferenceCompat>(
            PreferenceKeys.AUTH_INSTANCE_TOGGLE
        )!!
        val authInstance = findPreference<ListPreference>(PreferenceKeys.AUTH_INSTANCE)!!
        val instancePrefs = listOf(instancePref, authInstance)
        val appContext = requireContext().applicationContext

        lifecycleScope.launch {
            customInstancesModel.fetchCustomInstances {
                appContext.toastFromMainThread(it.message.orEmpty())
            }
        }

        lifecycleScope.launch {
            customInstancesModel.instances.collect { updatedInstances ->
                instances = updatedInstances
                // update the instances to also show custom ones
                initInstancesPref(instancePrefs)
            }
        }

        authInstance.setOnPreferenceChangeListener { _, _ ->
            RetrofitInstance.apiLazyMgr.reset()
            logoutAndUpdateUI()
            true
        }

        authInstanceToggle.setOnPreferenceChangeListener { _, _ ->
            RetrofitInstance.apiLazyMgr.reset()
            logoutAndUpdateUI()
            true
        }

        val customInstance = findPreference<Preference>(PreferenceKeys.CUSTOM_INSTANCE)
        customInstance?.setOnPreferenceClickListener {
            CustomInstancesListDialog()
                .show(childFragmentManager, CreateCustomInstanceDialog::class.java.name)
            true
        }

        val login = findPreference<Preference>(PreferenceKeys.LOGIN_REGISTER)
        val logout = findPreference<Preference>(PreferenceKeys.LOGOUT)
        val deleteAccount = findPreference<Preference>(PreferenceKeys.DELETE_ACCOUNT)

        login?.isVisible = token.isEmpty()
        logout?.isVisible = token.isNotEmpty()
        deleteAccount?.isEnabled = token.isNotEmpty()

        // Google Account preferences
        val googleSignIn = findPreference<Preference>(PreferenceKeys.GOOGLE_SIGN_IN)
        val googleSignOut = findPreference<Preference>(PreferenceKeys.GOOGLE_SIGN_OUT)
        val googleImportSubs = findPreference<Preference>(PreferenceKeys.GOOGLE_IMPORT_SUBS)
        val googleImportHistory = findPreference<Preference>(PreferenceKeys.GOOGLE_IMPORT_HISTORY)

        val isGoogleConnected = PreferenceHelper.isGoogleAccountConnected()
        googleSignIn?.isVisible = !isGoogleConnected
        googleSignOut?.isVisible = isGoogleConnected
        googleSignOut?.summary = if (isGoogleConnected) {
            getString(R.string.google_signed_in_as, PreferenceHelper.getGoogleEmail())
        } else null
        googleImportSubs?.isEnabled = isGoogleConnected
        googleImportHistory?.isEnabled = isGoogleConnected

        childFragmentManager.setFragmentResultListener(
            INSTANCE_DIALOG_REQUEST_KEY,
            this
        ) { _, resultBundle ->
            val isLoggedIn = resultBundle.getBoolean(IntentData.loginTask)
            val isLoggedOut = resultBundle.getBoolean(IntentData.logoutTask)
            val isGoogleLoggedIn = resultBundle.getBoolean(IntentData.googleLoginTask)
            val isGoogleLoggedOut = resultBundle.getBoolean(IntentData.googleLogoutTask)

            if (isLoggedIn) {
                login?.isVisible = false
                logout?.isVisible = true
                deleteAccount?.isEnabled = true
            } else if (isLoggedOut) {
                logoutAndUpdateUI()
            }

            if (isGoogleLoggedIn) {
                googleSignIn?.isVisible = false
                googleSignOut?.isVisible = true
                googleSignOut?.summary = getString(
                    R.string.google_signed_in_as,
                    PreferenceHelper.getGoogleEmail()
                )
                googleImportSubs?.isEnabled = true
                googleImportHistory?.isEnabled = true
            } else if (isGoogleLoggedOut) {
                googleSignIn?.isVisible = true
                googleSignOut?.isVisible = false
                googleSignOut?.summary = null
                googleImportSubs?.isEnabled = false
                googleImportHistory?.isEnabled = false
            }
        }

        login?.setOnPreferenceClickListener {
            LoginDialog().show(childFragmentManager, LoginDialog::class.java.name)
            true
        }

        logout?.setOnPreferenceClickListener {
            LogoutDialog().show(childFragmentManager, LogoutDialog::class.java.name)
            true
        }

        deleteAccount?.setOnPreferenceClickListener {
            DeleteAccountDialog()
                .show(childFragmentManager, DeleteAccountDialog::class.java.name)
            true
        }

        googleSignIn?.setOnPreferenceClickListener {
            GoogleSignInDialog().show(childFragmentManager, GoogleSignInDialog::class.java.name)
            true
        }

        googleSignOut?.setOnPreferenceClickListener {
            GoogleSignOutDialog().show(childFragmentManager, GoogleSignOutDialog::class.java.name)
            true
        }

        googleImportSubs?.setOnPreferenceClickListener {
            importGoogleSubscriptions()
            true
        }

        googleImportHistory?.setOnPreferenceClickListener {
            importGoogleWatchHistory()
            true
        }

        findPreference<SwitchPreferenceCompat>(PreferenceKeys.FULL_LOCAL_MODE)?.setOnPreferenceChangeListener { _, newValue ->
            // when the full local mode gets enabled, the fetch instance is no longer used and replaced
            // fully by local extraction. thus, the user has to be logged out from the fetch instance
            if (newValue == true && !authInstanceToggle.isChecked) logoutAndUpdateUI()
            true
        }
    }

    private fun importGoogleSubscriptions() {
        if (!PreferenceHelper.isGoogleAccountConnected()) {
            Toast.makeText(context, R.string.google_not_connected, Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(context, R.string.google_importing_subscriptions, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val repo = GoogleAccountSubscriptionsRepository()
                val count = repo.importFromGoogleAccount()
                context?.toastFromMainThread(
                    getString(R.string.google_import_subs_success, count)
                )
            } catch (e: Exception) {
                Log.e(TAG(), "Failed to import Google subscriptions", e)
                context?.toastFromMainThread(
                    getString(R.string.google_import_subs_failed, e.localizedMessage ?: "Unknown error")
                )
            }
        }
    }

    private fun importGoogleWatchHistory() {
        if (!PreferenceHelper.isGoogleAccountConnected()) {
            Toast.makeText(context, R.string.google_not_connected, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val googleClientId = getString(R.string.google_client_id)
                val accessToken = com.github.libretube.helpers.GoogleAuthHelper
                    .getValidAccessToken(googleClientId)
                    ?: throw Exception("Failed to get access token")

                // Get the user's channel to find their "likes" and activity
                val myChannel = RetrofitInstance.youtubeDataApi.getMyChannel(
                    accessToken = accessToken
                )

                val channelItem = myChannel.items.firstOrNull()
                    ?: throw Exception("Could not find your YouTube channel")

                // Note: YouTube doesn't expose watch history via the Data API.
                // The "likes" playlist is the closest publicly available data.
                // For full watch history import, users should use Google Takeout
                // (which is already supported via the existing import feature).
                context?.toastFromMainThread(
                    getString(R.string.google_import_history_summary)
                )
            } catch (e: Exception) {
                Log.e(TAG(), "Failed to import Google watch history", e)
                context?.toastFromMainThread(
                    getString(R.string.google_import_subs_failed, e.localizedMessage ?: "Unknown error")
                )
            }
        }
    }

    /**
     * Refresh Google account UI state when returning from browser-based OAuth flow.
     * The browser flow completes via GoogleOAuthRedirectActivity which stores the tokens,
     * then the user navigates back here.
     */
    override fun onResume() {
        super.onResume()

        val isGoogleConnected = PreferenceHelper.isGoogleAccountConnected()
        findPreference<Preference>(PreferenceKeys.GOOGLE_SIGN_IN)?.isVisible = !isGoogleConnected
        findPreference<Preference>(PreferenceKeys.GOOGLE_SIGN_OUT)?.apply {
            isVisible = isGoogleConnected
            summary = if (isGoogleConnected) {
                getString(R.string.google_signed_in_as, PreferenceHelper.getGoogleEmail())
            } else null
        }
        findPreference<Preference>(PreferenceKeys.GOOGLE_IMPORT_SUBS)?.isEnabled = isGoogleConnected
        findPreference<Preference>(PreferenceKeys.GOOGLE_IMPORT_HISTORY)?.isEnabled = isGoogleConnected
    }

    private fun initInstancesPref(instancePrefs: List<ListPreference>) = runCatching {
        // add the currently used instances to the list if they're currently down / not part
        // of the public instances list
        for (apiUrl in listOf(PipedMediaServiceRepository.apiUrl, RetrofitInstance.authUrl)) {
            if (instances.none { it.apiUrl == apiUrl }) {
                val origin = apiUrl.toHttpUrl().host
                instances.add(PipedInstance(origin, apiUrl, isCurrentlyDown = true))
            }
        }

        instances.sortBy { it.name }

        // If any preference dialog is visible in this fragment, it's one of the instance selection
        // dialogs. In order to prevent UX issues, we don't update the instances list then.
        if (isDialogVisible) return@runCatching

        for (instancePref in instancePrefs) {
            // add custom instances to the list preference
            instancePref.entries = instances.map { it.name }.toTypedArray()
            instancePref.entryValues = instances.map { it.apiUrl }.toTypedArray()
            instancePref.summaryProvider =
                Preference.SummaryProvider<ListPreference> { preference ->
                    preference.entry
                }
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference.key in arrayOf(
                PreferenceKeys.FETCH_INSTANCE,
                PreferenceKeys.AUTH_INSTANCE
            )
        ) {
            showInstanceSelectionDialog(preference as ListPreference)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    private fun showInstanceSelectionDialog(preference: ListPreference) {
        var selectedInstance = preference.value
        val selectedIndex = instances.indexOfFirst { it.apiUrl == selectedInstance }

        val layoutInflater = LayoutInflater.from(context)
        val binding = SimpleOptionsRecyclerBinding.inflate(layoutInflater)
        binding.optionsRecycler.layoutManager = LinearLayoutManager(context)

        val instances = ImmutableList.copyOf(this.instances)
        binding.optionsRecycler.adapter = InstancesAdapter(selectedIndex) {
            selectedInstance = instances[it].apiUrl
        }.also { it.submitList(instances) }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(preference.title)
            .setView(binding.root)
            .setPositiveButton(R.string.okay) { _, _ ->
                preference.value = selectedInstance
                resetForNewInstance()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun logoutAndUpdateUI() {
        PreferenceHelper.setToken("")
        Toast.makeText(context, getString(R.string.loggedout), Toast.LENGTH_SHORT).show()
        findPreference<Preference>(PreferenceKeys.LOGIN_REGISTER)?.isVisible = true
        findPreference<Preference>(PreferenceKeys.LOGOUT)?.isVisible = false
        findPreference<Preference>(PreferenceKeys.DELETE_ACCOUNT)?.isEnabled = false
    }

    private fun resetForNewInstance() {
        val authInstanceToggle = findPreference<SwitchPreferenceCompat>(
            PreferenceKeys.AUTH_INSTANCE_TOGGLE
        )!!

        if (!authInstanceToggle.isChecked) {
            logoutAndUpdateUI()
        }
        RetrofitInstance.apiLazyMgr.reset()
        ActivityCompat.recreate(requireActivity())
    }

    companion object {
        const val INSTANCE_DIALOG_REQUEST_KEY = "instance_dialog_request_key"
    }
}
