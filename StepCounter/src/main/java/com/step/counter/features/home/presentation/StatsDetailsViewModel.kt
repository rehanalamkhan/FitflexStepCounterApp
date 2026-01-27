package com.step.counter.features.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.step.counter.ApplicationClass
import com.step.counter.core.data.repository.DayRepositoryImpl
import com.step.counter.core.domain.usecase.DayUseCases
import com.step.counter.features.home.data.model.StatsDetailsState
import com.step.counter.features.home.domain.usecase.StatsDetailsUseCases
import com.step.counter.features.settings.data.repository.SettingsRepositoryImpl
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.ranges.rangeTo

class StatsDetailsViewModel(
    private val dayUseCases: DayUseCases,
    statsDetailsUseCases: StatsDetailsUseCases,
    currentDateFlow: StateFlow<LocalDate>
) : ViewModel() {

    private val _day = MutableStateFlow(
        StatsDetailsState(
            date = LocalDate.MIN,
            stepsTaken = 0,
            calorieBurned = 0,
            distanceTravelled = 0.0,
            chartDateRange = currentDateFlow.value..currentDateFlow.value,
            goal = 0
        )
    )
    val day: StateFlow<StatsDetailsState> = _day.asStateFlow()

    init {
        selectDay(currentDateFlow.value)

        viewModelScope.launch {
            val firstDateFlow = statsDetailsUseCases.getFirstDate()
            firstDateFlow
                .combine(currentDateFlow) { firstDate, currentDate ->
                    firstDate..currentDate
                }.collect { dateRange ->
                    _day.value = day.value.copy(chartDateRange = dateRange)
                }
        }
    }

    private var selectDateJob: Job? = null

    fun selectDay(date: LocalDate) {
        selectDateJob?.cancel()
        selectDateJob = dayUseCases.getDay(date).onEach {
            _day.value = day.value.copy(
                date = it.date,
                stepsTaken = it.steps,
                goal = it.goal,
                calorieBurned = it.calorieBurned.roundToInt(),
                distanceTravelled = it.distanceTravelled,
            )
        }.launchIn(viewModelScope)
    }

    companion object Factory : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = checkNotNull(extras[APPLICATION_KEY]) as ApplicationClass

            val dayDatabase = application.stepCounterDatabase
            val dayRepository = DayRepositoryImpl(dayDatabase.dayDao)
            val settingsStore = application.settingsStore
            val settingsRepository = SettingsRepositoryImpl(settingsStore)
            val dayUseCases = DayUseCases(dayRepository, settingsRepository)
            val statsDetailsUseCases = StatsDetailsUseCases(dayRepository)

            return StatsDetailsViewModel(
                dayUseCases,
                statsDetailsUseCases,
                application.currentDate
            ) as T
        }
    }
}