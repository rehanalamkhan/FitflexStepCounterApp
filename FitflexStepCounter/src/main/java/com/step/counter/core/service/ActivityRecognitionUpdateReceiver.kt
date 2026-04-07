package com.step.counter.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Forwards Play Services activity updates to [StepCounterService].
 *
 * Must use [Context.startService], not [Context.startForegroundService]: starting an FGS from a
 * broadcast while the app is in the background is often blocked on API 31+, so the service would
 * never receive [StepCounterService.ACTION_ACTIVITY_UPDATE] and the step gate would stay closed.
 */
class ActivityRecognitionUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val serviceIntent = Intent(context, StepCounterService::class.java).apply {
                action = StepCounterService.ACTION_ACTIVITY_UPDATE
                intent.extras?.let { putExtras(it) }
            }
            context.startService(serviceIntent)
        } catch (e: Exception) {
//            Log.e(TAG, "Failed to forward activity recognition to StepCounterService", e)
        }
    }

    companion object {
        private const val TAG = "ActivityRecognitionRx"
    }
}
