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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.step.counter.core.service.StepCounterService
import com.step.counter.core.utils.isAppInForeground
import com.step.counter.core.utils.extensions.hasStepCounterSensor
import com.step.counter.core.utils.extensions.popStepCounterHostBackStack
import com.step.counter.core.utils.extensions.safeBack
import androidx.core.content.edit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    private var pendingServiceStartJob: Job? = null

    private var isStepCounterSupportedOnDevice = false
    private var unsupportedDeviceDialogShown = false

    // ─── Lifecycle ──────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate savedInstanceState=${savedInstanceState != null}")
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart lifecycle=${lifecycle.currentState}")
    }

    override fun onResume() {
        super.onResume()
        if (!isStepCounterSupportedOnDevice) {
            Log.d(TAG, "onResume: step counter sensor unsupported, skipping permission/service flow")
            return
        }
        Log.d(
            TAG,
            "onResume fragmentLifecycle=${lifecycle.currentState} " +
                "viewLifecycle=${viewLifecycleOwner.lifecycle.currentState} " +
                "processForeground=${permissionContext().isAppInForeground()}",
        )
        syncPermissionStateAndMaybeStartService("onResume")
    }

    override fun onPause() {
        pendingServiceStartJob?.cancel()
        pendingServiceStartJob = null
        super.onPause()
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

        val sensorSupported = requireContext().hasStepCounterSensor()
        Log.d(TAG, "StepCounter sensor available = $sensorSupported")
        if (!sensorSupported) {
            if (!unsupportedDeviceDialogShown) {
                unsupportedDeviceDialogShown = true
                showUnsupportedDeviceDialog()
            }
            return
        }

        isStepCounterSupportedOnDevice = true
        StepCounter.init(requireContext())

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
        if (permissions.isEmpty() || hasAllRequiredPermissions(permissions)) return false
        if (shouldShowPermissionRationale()) return true
        return !isPermanentDenialRecorded()
    }

    /**
     * Re-reads runtime permission state from the host app package manager on every resume.
     * Returning from system settings does not invoke [requestPermissionLauncher], so this path
     * must start [StepCounterService] when permissions were granted outside the launcher.
     */
    private fun syncPermissionStateAndMaybeStartService(source: String) {
        if (!isStepCounterSupportedOnDevice) {
            Log.w(TAG, "$source: step counter sensor unsupported, skipping permission sync")
            return
        }
        if (!isAdded) {
            Log.w(TAG, "$source: fragment not added, skipping permission sync")
            return
        }

        val permissions = getRequiredPermissions()
        logPermissionState(source, permissions)

        if (permissions.isEmpty() || hasAllRequiredPermissions(permissions)) {
            clearPermanentDenialPref()
            permanentlyDeniedDialogShownThisSession = false
            launchedRuntimePromptThisFragmentInstance = false
            scheduleServiceStartIfEligible(source)
            return
        }

        if (shouldShowRuntimePermissionPrompt()) {
            if (!launchedRuntimePromptThisFragmentInstance) {
                launchedRuntimePromptThisFragmentInstance = true
                Log.d(TAG, "$source: launching runtime permission prompt")
                requestPermissionLauncher.launch(permissions)
            }
            return
        }

        if (!permanentlyDeniedDialogShownThisSession) {
            permanentlyDeniedDialogShownThisSession = true
            Log.w(TAG, "$source: runtime permissions blocked, showing settings dialog")
            dispatchUiWhenStarted { showPermanentlyDeniedDialog() }
        }
    }

    private fun handlePermissionResult(permissions: Map<String, Boolean>) {
        Log.d(TAG, "handlePermissionResult results=$permissions lifecycle=${lifecycle.currentState}")
        when {
            permissions.all { it.value } -> {
                clearPermanentDenialPref()
                permanentlyDeniedDialogShownThisSession = false
                launchedRuntimePromptThisFragmentInstance = false
                // Defer service start until fragment is RESUMED and process is foreground.
                if (isResumed) {
                    scheduleServiceStartIfEligible("permissionLauncher")
                }
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
        permissionContext().getSharedPreferences(PERM_PREFS_NAME, Context.MODE_PRIVATE)

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

    private fun hasAllRequiredPermissions(permissions: Array<String>): Boolean =
        permissions.all { isPermissionGranted(it) }

    private fun isPermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(permissionContext(), permission) ==
            PackageManager.PERMISSION_GRANTED

    private fun permissionContext(): Context = requireContext().applicationContext

    private fun logPermissionState(source: String, permissions: Array<String>) {
        val permissionStates = permissions.associateWith { permission ->
            when (ContextCompat.checkSelfPermission(permissionContext(), permission)) {
                PackageManager.PERMISSION_GRANTED -> "granted"
                else -> "denied"
            }
        }
        Log.d(
            TAG,
            "$source: permissionStates=$permissionStates permanentDenialRecorded=${isPermanentDenialRecorded()} " +
                "serviceRunning=${StepCounterService.isRunning} fragmentLifecycle=${lifecycle.currentState} " +
                "processForeground=${permissionContext().isAppInForeground()}",
        )
    }

    /**
     * Defers foreground service start until the fragment is visible and the process is in the
     * foreground. Avoids [android.app.ForegroundServiceStartNotAllowedException] when returning
     * from system settings while the activity transition is still in progress.
     */
    private fun scheduleServiceStartIfEligible(source: String) {
        if (!isAdded) {
            Log.w(TAG, "$source: fragment not added, not scheduling service start")
            return
        }
        pendingServiceStartJob?.cancel()
        pendingServiceStartJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(SERVICE_START_DELAY_MS)
            startStepCounterServiceIfNeeded(source)
        }
        Log.d(TAG, "$source: scheduled service start in ${SERVICE_START_DELAY_MS}ms")
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

    private fun showUnsupportedDeviceDialog() {
        if (!isAdded || activity?.isFinishing == true) return
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.device_not_supported_title))
            .setMessage(getString(R.string.device_not_supported_message))
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
                exitBecauseUnsupportedDevice()
            }
            .show()
    }

    private fun exitBecauseUnsupportedDevice() {
        if (!isAdded) return
        Log.d(TAG, "Exiting StepCounterFragment: device lacks step counter sensor")
        when {
            popStepCounterHostBackStack() -> Unit
            safeBack() -> Unit
            else -> activity?.finish()
        }
    }

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
    private fun startStepCounterServiceIfNeeded(source: String) {
        if (!isStepCounterSupportedOnDevice) {
            Log.w(TAG, "$source: step counter sensor unsupported, skipping service start")
            return
        }
        if (!isAdded) {
            Log.w(TAG, "$source: fragment not added, skipping service start")
            return
        }
        if (!isResumed) {
            Log.w(
                TAG,
                "$source: fragment not RESUMED (lifecycle=${lifecycle.currentState}), skipping service start",
            )
            return
        }
        if (!permissionContext().isAppInForeground()) {
            Log.w(TAG, "$source: app not in foreground, skipping service start")
            return
        }

        val permissions = getRequiredPermissions()
        if (permissions.isNotEmpty() && !hasAllRequiredPermissions(permissions)) {
            Log.w(TAG, "$source: skipping StepCounterService start, missing permissions")
            return
        }
        if (StepCounterService.isRunning) {
            Log.d(TAG, "$source: StepCounterService already running, skipping duplicate start")
            return
        }

        Log.d(
            TAG,
            "$source: attempting StepCounterService start " +
                "(fragmentLifecycle=${lifecycle.currentState} processForeground=true)",
        )
        runCatching {
            ContextCompat.startForegroundService(
                permissionContext(),
                Intent(permissionContext(), StepCounterService::class.java),
            )
        }.onSuccess {
            Log.d(TAG, "$source: StepCounterService start requested")
        }.onFailure { e ->
            Log.e(TAG, "$source: failed to start StepCounterService", e)
        }
    }

    // ─── Companion ───────────────────────────────────────────────────────────
    companion object {
        private const val TAG = "StepCounterFragment"
        private const val PERM_PREFS_NAME = "step_counter_permission_state"
        private const val KEY_PERM_RUNTIME_BLOCKED = "runtime_prompt_permanently_blocked"
        private const val SERVICE_START_DELAY_MS = 300L

        /**
         * Creates the library root fragment. Host apps may present it through Navigation
         * Component or through [com.step.counter.integration.StepCounterHost].
         */
        fun newInstance(): StepCounterFragment = StepCounterFragment()
    }
}