package com.step.counter.core.service

import android.util.Log
import com.step.counter.core.domain.usecase.DayUseCases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

class StepCounterController(
    private val dayUseCases: DayUseCases,
    private val coroutineScope: CoroutineScope,
    currentDateFlow: StateFlow<LocalDate>,
) {

    companion object {
        private const val TAG = "StepCounterController"

        /** Persist step deltas to Room in multiples of this size to reduce writes. */
        private const val STEP_PERSIST_BATCH_SIZE = 50
    }

    private val _stats = MutableStateFlow(StepCounterState(LocalDate.now(), 0, 0, 0.0, 0))
    val stats: StateFlow<StepCounterState> = _stats.asStateFlow()

    private var getStatsJob: Job? = null

    init {
        coroutineScope.launch {
            currentDateFlow.collect { getStats(it) }
        }
    }

    private fun getStats(date: LocalDate) {
        getStatsJob?.cancel()
        Log.d(TAG, "Observing Day stats for date=$date")

        getStatsJob = dayUseCases.getDay(date).onEach { day ->
            _stats.value = day.run {
                StepCounterState(
                    date = date,
                    steps = steps,
                    goal = goal,
                    distanceTravelled = distanceTravelled,
                    calorieBurned = calorieBurned.roundToInt()
                )
            }
            Log.v(TAG, "Stats emit steps=${day.steps} goal=${day.goal} date=$date")
        }.launchIn(coroutineScope)
    }

    private val rawStepSensorReadings = MutableStateFlow(StepCounterEvent(0, LocalDate.MIN))
    private var previousStepCount: Int? = null

    /** Steps not yet written for [pendingStepsDate] (remainder after last batch of [STEP_PERSIST_BATCH_SIZE]). */
    private var pendingStepsNotPersisted = 0
    private var pendingStepsDate: LocalDate? = null

    init {
        rawStepSensorReadings.drop(1).onEach { event ->
            val stepCountDifference = event.stepCount - (previousStepCount ?: event.stepCount)
            previousStepCount = event.stepCount
            Log.v(
                TAG,
                "Sensor reading total=${event.stepCount} delta=$stepCountDifference date=${event.eventDate}",
            )
            if (stepCountDifference > 0) {
                persistStepDeltaInBatches(event.eventDate, stepCountDifference)
            }
        }.launchIn(coroutineScope)
    }

    private suspend fun persistStepDeltaInBatches(date: LocalDate, delta: Int) {
        if (pendingStepsDate != null && pendingStepsDate != date) {
            Log.d(TAG, "Calendar day changed pending bucket ${pendingStepsDate} → $date; flushing remainder")
            flushPendingRemainder()
        }
        if (pendingStepsDate == null) {
            pendingStepsDate = date
        }

        pendingStepsNotPersisted += delta
        Log.d(
            TAG,
            "Accumulating delta=+$delta date=$date pendingNotPersisted=$pendingStepsNotPersisted",
        )
        while (pendingStepsNotPersisted >= STEP_PERSIST_BATCH_SIZE) {
            Log.d(TAG, "Persisting batch size=$STEP_PERSIST_BATCH_SIZE to DB date=$date")
            dayUseCases.incrementStepCount(date, STEP_PERSIST_BATCH_SIZE)
            pendingStepsNotPersisted -= STEP_PERSIST_BATCH_SIZE
        }
    }

    private suspend fun flushPendingRemainder() {
        val date = pendingStepsDate ?: run {
            Log.d(TAG, "flushPendingRemainder: nothing pending")
            return
        }
        if (pendingStepsNotPersisted > 0) {
            Log.d(TAG, "Flushing remainder steps=$pendingStepsNotPersisted date=$date")
            dayUseCases.incrementStepCount(date, pendingStepsNotPersisted)
            pendingStepsNotPersisted = 0
        } else {
            Log.d(TAG, "flushPendingRemainder: date=$date had zero remainder")
        }
        pendingStepsDate = null
    }

    /**
     * Writes any steps accumulated since the last batch write (remainder under [STEP_PERSIST_BATCH_SIZE]).
     * Call when stopping the foreground service so totals are not lost.
     */
    suspend fun flushPendingStepsToDatabase() {
        Log.d(TAG, "flushPendingStepsToDatabase (service shutdown)")
        flushPendingRemainder()
    }

    fun onStepCountChanged(newStepCount: Int, eventDate: LocalDate) {
        Log.v(TAG, "onStepCountChanged total=$newStepCount date=$eventDate")
        rawStepSensorReadings.value = StepCounterEvent(newStepCount, eventDate)
    }
}
