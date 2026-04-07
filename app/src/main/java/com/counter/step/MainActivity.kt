package com.counter.step

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.step.counter.R
import com.step.counter.StepCounterMainActivity
import com.step.counter.features.home.presentation.StatsDetailsViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                openMainActivity()
            } else {
                openPermissionSettings()
            }
        }

    private val viewModel: StatsDetailsViewModel by viewModels { StatsDetailsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        observeStatsFromViewModel()
        askForRequiredPermissions()
    }

    /**
     * Observes [StatsDetailsViewModel.day] (steps, distance, calories, goal) while this activity
     * is started. Replace the [Log] line with UI updates (TextView, Compose, etc.) as needed.
     */
    private fun observeStatsFromViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.day.collect { stats ->

                    val stepsText = resources.getQuantityString(
                        com.step.counter.R.plurals.step_count_format, stats.stepsTaken,
                    )
                    val distanceText = getString(
                        R.string.distance_travelled_format, stats.distanceTravelled
                    )

                    val caloriesBurned = stats.calorieBurned
                }
            }
        }
    }

    private fun askForRequiredPermissions() {
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.ACTIVITY_RECOGNITION,
                Manifest.permission.POST_NOTIFICATIONS
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
                Manifest.permission.ACTIVITY_RECOGNITION
            )
            else -> emptyArray()
        }

        if (permissions.isEmpty()) {
            openMainActivity()
            return
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) ==
                PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            openMainActivity()
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun openMainActivity() {
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(
                Intent(
                    this,
                    StepCounterMainActivity::class.java
                )
            )
            finish()
        }, 500)
    }

    private fun openPermissionSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
