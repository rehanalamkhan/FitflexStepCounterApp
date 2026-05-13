package com.step.counter.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import com.step.counter.R
import com.step.counter.StepCounter
import com.step.counter.core.data.repository.DayRepositoryImpl
import com.step.counter.core.domain.usecase.DayUseCases
import com.step.counter.features.settings.data.repository.SettingsRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

class StepCounterService : LifecycleService(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var controller: StepCounterController
    private var activityRecognitionPendingIntent: PendingIntent? = null

    private var isStepCountingAllowed = false
    private var lastValidActivityTime = 0L
    private var activityUpdatesHandledCount = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activityRecognitionFallback: Runnable? = null

    companion object {
        private const val TAG = "StepCounterService"
        private const val NOTIFICATION_CHANNEL_ID = "step_counter_channel"
        private const val NOTIFICATION_ID = 0x1

        private const val PENDING_INTENT_ID = 0x1
        private const val ACTIVITY_RECOGNITION_PI_REQUEST_CODE = 0x2
        private const val ACTIVITY_UPDATE_INTERVAL_MS = 5_000L
        private const val ACTIVITY_GATE_TIMEOUT_MS = 10_000L
        private const val AR_FALLBACK_OPEN_GATE_MS = 12_000L

        private val STEP_ACTIVITIES = setOf(
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_FOOT,
        )

        private val BLOCK_ACTIVITIES = setOf(
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.STILL,
        )

        const val ACTION_ACTIVITY_UPDATE = "com.step.counter.ACTIVITY_UPDATE"
        const val ACTION_STOP_SERVICE   = "com.step.counter.STOP_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        if (Build.VERSION.SDK_INT >= VERSION_CODES.O) {
            registerNotificationChannel(createNotificationChannel())
        }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        registerStepCounter(sensorManager)

        StepCounter.init(this)

        val settingsStore = StepCounter.settingsStore
        val settingsRepository = SettingsRepositoryImpl(settingsStore)
        val dayDatabase = StepCounter.stepCounterDatabase
        val dayRepository = DayRepositoryImpl(dayDatabase.dayDao)
        val dayUseCases = DayUseCases(dayRepository, settingsRepository)

        controller = StepCounterController(dayUseCases, lifecycleScope, StepCounter.currentDate)

        registerActivityRecognition()

        startForeground(NOTIFICATION_ID, createNotification(controller.stats.value))

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.stats.collect {
                    notificationManager.notify(NOTIFICATION_ID, createNotification(it))
                }
            }
        }
    }

    // ─── Activity Recognition ──────────────────────────────────────────────────

    private fun registerActivityRecognition() {
        val intent = Intent(this, ActivityRecognitionUpdateReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            ACTIVITY_RECOGNITION_PI_REQUEST_CODE,
            intent,
            activityRecognitionPendingIntentFlags(),
        )
        activityRecognitionPendingIntent = pendingIntent

        ActivityRecognition.getClient(this)
            .requestActivityUpdates(ACTIVITY_UPDATE_INTERVAL_MS, pendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "Activity recognition registered (interval=${ACTIVITY_UPDATE_INTERVAL_MS}ms)")
                scheduleActivityRecognitionGateFallback()
            }
            .addOnFailureListener {
                Log.e(TAG, "Activity recognition failed to register", it)
                cancelActivityRecognitionGateFallback()
                isStepCountingAllowed = true
            }
    }

    private fun scheduleActivityRecognitionGateFallback() {
        cancelActivityRecognitionGateFallback()
        val runnable = Runnable {
            if (activityUpdatesHandledCount == 0) {
                Log.w(TAG, "No activity update reached service — opening step gate (fallback)")
                isStepCountingAllowed = true
            }
        }
        activityRecognitionFallback = runnable
        mainHandler.postDelayed(runnable, AR_FALLBACK_OPEN_GATE_MS)
    }

    private fun cancelActivityRecognitionGateFallback() {
        activityRecognitionFallback?.let { mainHandler.removeCallbacks(it) }
        activityRecognitionFallback = null
    }

    private fun handleActivityUpdate(intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) {
            Log.w(TAG, "handleActivityUpdate: no AR result in intent")
            return
        }
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        activityUpdatesHandledCount++
        val activities = result.probableActivities

        activities.forEach {
            Log.v(TAG, "AR candidate: ${activityName(it.type)} confidence=${it.confidence}%")
        }

        val topActivity = activities.maxByOrNull { it.confidence } ?: return
        Log.d(TAG, "Top activity: ${activityName(topActivity.type)} @ ${topActivity.confidence}%")

        when {
            topActivity.type in STEP_ACTIVITIES && topActivity.confidence >= 50 -> {
                Log.d(TAG, "Gate OPEN — walking/running/on-foot")
                isStepCountingAllowed = true
                lastValidActivityTime = System.currentTimeMillis()
            }
            topActivity.type in BLOCK_ACTIVITIES && topActivity.confidence >= 75 -> {
                val elapsed = System.currentTimeMillis() - lastValidActivityTime
                if (elapsed > ACTIVITY_GATE_TIMEOUT_MS) {
                    Log.d(
                        TAG,
                        "Gate CLOSED — ${activityName(topActivity.type)} (${elapsed}ms since last walk/run)",
                    )
                    isStepCountingAllowed = false
                } else {
                    Log.v(TAG, "Gate stays OPEN — grace (${elapsed}ms < ${ACTIVITY_GATE_TIMEOUT_MS}ms)")
                }
            }
            else -> {
                Log.v(TAG, "Gate unchanged — ambiguous activity")
            }
        }
    }

    private fun activityName(type: Int) = when (type) {
        DetectedActivity.WALKING -> "WALKING"
        DetectedActivity.RUNNING -> "RUNNING"
        DetectedActivity.ON_FOOT -> "ON_FOOT"
        DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
        DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
        DetectedActivity.STILL -> "STILL"
        DetectedActivity.TILTING -> "TILTING"
        DetectedActivity.UNKNOWN -> "UNKNOWN"
        else -> "OTHER($type)"
    }

    private fun activityRecognitionPendingIntentFlags(): Int {
        val base = PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= VERSION_CODES.S) base or PendingIntent.FLAG_MUTABLE
        else base
    }

    // ─── onStartCommand ───────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        if (intent?.action == ACTION_ACTIVITY_UPDATE) {
            handleActivityUpdate(intent)
        } else if (intent?.action == ACTION_STOP_SERVICE) {
            if (Build.VERSION.SDK_INT >= VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            Log.d(TAG, "Stopping foreground service (user action)")
            stopSelf()
        }
        return START_STICKY
    }

    // ─── Sensor ───────────────────────────────────────────────────────────────

    private fun registerStepCounter(sensorManager: SensorManager) {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) {
            Log.e(TAG, "TYPE_STEP_COUNTER sensor not available on this device")
            return
        }
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        Log.d(TAG, "Step counter sensor registered (delay=NORMAL)")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val steps = event.values[0].toInt()
        Log.v(TAG, "Step sensor event total=$steps gateOpen=$isStepCountingAllowed")
        if (!isStepCountingAllowed) return
        controller.onStepCountChanged(steps, LocalDate.now())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ─── Notification ─────────────────────────────────────────────────────────

    private fun createNotification(state: StepCounterState): Notification = state.run {
        val title = resources.getQuantityString(R.plurals.step_count, steps, steps)
        val progress = if (goal == 0) 0 else steps * 100 / goal
        val content = getString(R.string.step_counter_stats, calorieBurned, distanceTravelled, progress)

        NotificationCompat.Builder(this@StepCounterService, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fitflex_logo)
            .setContentTitle(title)
            .setContentText(content)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .addAction(0, getString(R.string.stop_service), stopServicePendingIntent)
            .build()
    }

    private val stopServicePendingIntent: PendingIntent
        get() {
            val intent = Intent(this, StepCounterService::class.java)
                .setAction(ACTION_STOP_SERVICE)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getService(this, 0x3, intent, flags)
        }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    override fun onDestroy() {
        Log.d(TAG, "onDestroy — flushing pending steps")
        runBlocking(Dispatchers.IO) {
            controller.flushPendingStepsToDatabase()
        }
        Log.d(TAG, "onDestroy — teardown AR + sensor listener")
        cancelActivityRecognitionGateFallback()
        activityRecognitionPendingIntent?.let {
            ActivityRecognition.getClient(this).removeActivityUpdates(it)
        }
        activityRecognitionPendingIntent = null
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    // ─── Notification Channel ─────────────────────────────────────────────────

    @RequiresApi(VERSION_CODES.O)
    private fun createNotificationChannel() = NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        getString(R.string.step_counter_channel),
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply { setShowBadge(false) }

    @RequiresApi(VERSION_CODES.O)
    private fun registerNotificationChannel(channel: NotificationChannel) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }
}