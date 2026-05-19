package com.step.counter.core.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class StepCounterServiceLauncher : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        // Android 12+ blocks foreground service starts from background receivers.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.d(TAG, "Skipping boot service start on API ${Build.VERSION.SDK_INT}: requires visible UI")
            return
        }

        if (!hasPermissions(context)) {
            Log.d(TAG, "Skipping boot service start: missing ACTIVITY_RECOGNITION")
            return
        }

        Log.d(TAG, "Boot completed — starting StepCounterService (pre-API 31)")
        val launchIntent = Intent(context.applicationContext, StepCounterService::class.java)
        ContextCompat.startForegroundService(context.applicationContext, launchIntent)
    }

    private fun hasPermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!hasPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)) {
                return false
            }
        }
        return true
    }

    private fun hasPermission(context: Context, permission: String): Boolean {
        val status = ContextCompat.checkSelfPermission(context, permission)
        return status == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "StepCounterServiceLauncher"
    }
}
