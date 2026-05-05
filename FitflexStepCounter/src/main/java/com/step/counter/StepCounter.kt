package com.step.counter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.step.counter.BuildConfig
import com.step.counter.core.data.source.StepCounterDatabase
import com.step.counter.core.domain.model.Day
import com.step.counter.core.domain.model.of
import com.step.counter.features.settings.data.source.SettingsStore
import com.step.counter.features.settings.data.source.SettingsStoreImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

object StepCounter {

    private var isInitialized = false

    lateinit var settingsStore: SettingsStore
        private set
    lateinit var stepCounterDatabase: StepCounterDatabase
        private set

    val currentDate = MutableStateFlow<LocalDate>(LocalDate.now())

    private val debugSeedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (isInitialized) return
        
        val appContext = context.applicationContext

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
        settingsStore = SettingsStoreImpl(sharedPreferences)

        stepCounterDatabase = Room.databaseBuilder(
            appContext,
            StepCounterDatabase::class.java,
            StepCounterDatabase.DATABASE_NAME
        ).build()

        registerMidnightTimer(appContext)

        // Debug-only: today's row is overwritten with dummy steps so bar chart / gradient can be verified offline.
      /*  if (BuildConfig.DEBUG) {
            debugSeedScope.launch {
                runCatching { seedDebugDummyStepsForToday() }
                    .onFailure { Log.e(TAG, "Debug dummy steps seed failed", it) }
            }
        }*/
        
        isInitialized = true
    }

    /**
     * Writes [DEBUG_DUMMY_STEPS] for **today** (UTC/local calendar via [LocalDate.now]).
     * Runs only when `BuildConfig.DEBUG`; uses current settings for goal/metrics on that row.
     */
    private suspend fun seedDebugDummyStepsForToday() {
        val settings = settingsStore.getSettings().first()
        val today = LocalDate.now()
        val day = Day.of(today, settings, steps = DEBUG_DUMMY_STEPS)
        stepCounterDatabase.dayDao.upsertDay(day)
        Log.d(TAG, "Debug seed: upserted today=$today steps=$DEBUG_DUMMY_STEPS goal=${settings.dailyGoal}")
    }

    private fun registerMidnightTimer(context: Context) {
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(midnightBroadcastReceiver, intentFilter)
    }

    private val midnightBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val today = LocalDate.now()
            if (today != currentDate.value) {
                currentDate.value = today
            }
        }
    }

    private const val TAG = "StepCounter"
    private const val DEBUG_DUMMY_STEPS = 995
}
