package com.step.counter

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.fragment.NavHostFragment
import com.step.counter.core.service.StepCounterService
import com.step.counter.core.utils.extensions.popStepCounterHostBackStack
import androidx.core.content.edit

@Keep
open class StepCounterFragment : Fragment() {

    // ─── Permission Launcher ────────────────────────────────────────────────
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            dispatchUiWhenStarted {
                handlePermissionResult(permissions)
            }
        }

    /** Avoid stacking identical permanent-deny dialogs during one resumed session. */
    private var permanentlyDeniedDialogShownThisSession = false

    /**
     * Match previous onCreate-only behavior: auto-launch the system permission sheet at most once
     * per fragment instance unless the user taps "Grant" again from the rationale dialog.
     */
    private var launchedRuntimePromptThisFragmentInstance = false

    // ─── Lifecycle ──────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StepCounter.init(requireContext())
    }

    override fun onStart() {
        super.onStart()
        refreshPermissionStateAndMaybePrompt()
    }

    override fun onResume() {
        super.onResume()
        val permissions = getRequiredPermissions()
        if (permissions.isNotEmpty() && areAllPermissionsGranted(permissions)) {
            clearPermanentDenialPref()
            permanentlyDeniedDialogShownThisSession = false
            launchedRuntimePromptThisFragmentInstance = false
            startStepCounterService()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentContainerView(requireContext()).apply {
            id = R.id.step_counter_nav_host
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(
                    R.id.step_counter_nav_host,
                    NavHostFragment.create(R.navigation.step_counter_nav_graph)
                )
                .setPrimaryNavigationFragment(
                    childFragmentManager.findFragmentById(R.id.step_counter_nav_host)
                )
                .commit()
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (popInternalBackStack()) {
                return@addCallback
            }

            if (popStepCounterHostBackStack()) {
                return@addCallback
            }

            isEnabled = false
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

    }

    private fun popInternalBackStack(): Boolean {
        val internalNavController = (childFragmentManager
            .findFragmentById(R.id.step_counter_nav_host) as? NavHostFragment)
            ?.navController
        return internalNavController?.popBackStack() == true
    }

    // ─── Permissions ────────────────────────────────────────────────────────

    /**
     * Runs [block] once the fragment lifecycle is at least [STARTED][androidx.lifecycle.Lifecycle.State.STARTED].
     * Needed because permission callbacks can arrive before the UI is ready (especially when the system sheet is suppressed).
     */
    private fun dispatchUiWhenStarted(block: () -> Unit) {
        if (!isAdded) return
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            if (activity?.isFinishing != true) block()
            return
        }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                lifecycle.removeObserver(this)
                if (isAdded && activity?.isFinishing != true) block()
            }
        })
    }

    /**
     * Show the system prompt only when Android might still display it.
     * After permanent denial we skip [launch] (silent callback / no sheet) and show our settings dialog instead.
     */
    private fun shouldShowRuntimePermissionPrompt(): Boolean {
        val permissions = getRequiredPermissions()
        if (permissions.isEmpty() || areAllPermissionsGranted(permissions)) return false
        if (shouldShowPermissionRationale()) return true
        return !isPermanentDenialRecorded()
    }

    private fun refreshPermissionStateAndMaybePrompt() {
        val permissions = getRequiredPermissions()
        if (permissions.isEmpty() || areAllPermissionsGranted(permissions)) {
            launchedRuntimePromptThisFragmentInstance = false
            startStepCounterService()
            return
        }

        if (shouldShowRuntimePermissionPrompt()) {
            if (!launchedRuntimePromptThisFragmentInstance) {
                launchedRuntimePromptThisFragmentInstance = true
                requestPermissionLauncher.launch(permissions)
            }
            return
        }

        if (!permanentlyDeniedDialogShownThisSession) {
            permanentlyDeniedDialogShownThisSession = true
            dispatchUiWhenStarted { showPermanentlyDeniedDialog() }
        }
    }

    private fun handlePermissionResult(permissions: Map<String, Boolean>) {
        when {
            permissions.all { it.value } -> {
                clearPermanentDenialPref()
                permanentlyDeniedDialogShownThisSession = false
                launchedRuntimePromptThisFragmentInstance = false
                startStepCounterService()
            }
            shouldShowPermissionRationale() -> showPermissionRationaleDialog()
            else -> {
                recordPermanentDenialPref()
                permanentlyDeniedDialogShownThisSession = true
                showPermanentlyDeniedDialog()
            }
        }
    }

    private fun permissionPrefs() =
        requireContext().getSharedPreferences(PERM_PREFS_NAME, Context.MODE_PRIVATE)

    private fun isPermanentDenialRecorded(): Boolean =
        permissionPrefs().getBoolean(KEY_PERM_RUNTIME_BLOCKED, false)

    private fun recordPermanentDenialPref() {
        permissionPrefs().edit { putBoolean(KEY_PERM_RUNTIME_BLOCKED, true) }
    }

    private fun clearPermanentDenialPref() {
        permissionPrefs().edit { remove(KEY_PERM_RUNTIME_BLOCKED) }
    }

    private fun getRequiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.POST_NOTIFICATIONS
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION
        )
        else -> emptyArray()
    }

    private fun areAllPermissionsGranted(permissions: Array<String>): Boolean =
        permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) ==
                    PackageManager.PERMISSION_GRANTED
        }

    /**
     * Returns true if at least one denied permission can still show a rationale,
     * meaning the user denied once but hasn't selected "Don't ask again".
     */
    private fun shouldShowPermissionRationale(): Boolean =
        getRequiredPermissions().any { permission ->
            shouldShowRequestPermissionRationale(permission)
        }

    // ─── Dialogs ─────────────────────────────────────────────────────────────

    /** Denied once → explain why we need it, offer to try again */
    private fun showPermissionRationaleDialog() {
        if (!isAdded || activity?.isFinishing == true) return
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.permission_required_title))
            .setMessage(getString(R.string.permission_rationale_message))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.grant_permission)) { dialog, _ ->
                dialog.dismiss()
                permanentlyDeniedDialogShownThisSession = false
                launchedRuntimePromptThisFragmentInstance = true
                requestPermissionLauncher.launch(getRequiredPermissions())
            }
            .setNegativeButton(getString(R.string.not_now)) { dialog, _ ->
                dialog.dismiss()
                showFeatureUnavailableState()
            }
            .show()
    }

    /** Denied twice / permanently → send user to App Settings */
    private fun showPermanentlyDeniedDialog() {
        if (!isAdded || activity?.isFinishing == true) return
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.permission_denied_title))
            .setMessage(getString(R.string.permission_denied_message))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.open_settings)) { dialog, _ ->
                dialog.dismiss()
                permanentlyDeniedDialogShownThisSession = false
                openAppSettings()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
                showFeatureUnavailableState()
            }
            .show()
    }

    private fun openAppSettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
            )
        }.onFailure {
            // Fallback: open general settings if specific page fails
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    /** Show a gentle empty/disabled state inside the fragment UI */
    private fun showFeatureUnavailableState() {
        (childFragmentManager.findFragmentById(R.id.step_counter_nav_host)
                as? NavHostFragment)?.let {
            // Optional: navigate to a "permission denied" destination in your nav graph
            // it.navController.navigate(R.id.permissionDeniedFragment)
        }
    }

    // ─── Service ─────────────────────────────────────────────────────────────
    private fun startStepCounterService() {
        if (!isAdded) return
        runCatching {
            ContextCompat.startForegroundService(
                requireContext(),
                Intent(requireContext(), StepCounterService::class.java)
            )
        }.onFailure { e ->
            Log.e(TAG, "Failed to start StepCounterService", e)
        }
    }

    // ─── Companion ───────────────────────────────────────────────────────────
    companion object {
        private const val TAG = "StepCounterFragment"
        private const val PERM_PREFS_NAME = "step_counter_permission_state"
        private const val KEY_PERM_RUNTIME_BLOCKED = "runtime_prompt_permanently_blocked"

        /**
         * Creates the library root fragment. Host apps may present it through Navigation
         * Component or through [com.step.counter.integration.StepCounterHost].
         */
        fun newInstance(): StepCounterFragment = StepCounterFragment()
    }
}