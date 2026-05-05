package com.step.counter.features.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.step.counter.StepCounter
import com.step.counter.core.data.repository.DayRepositoryImpl
import com.step.counter.core.domain.model.Day
import com.step.counter.features.home.domain.usecase.StatsChartPageUseCases
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

import com.step.counter.features.settings.data.repository.SettingsRepositoryImpl

class StatsChartPageViewModel(
    private val statsChartPageUseCases: StatsChartPageUseCases
) : ViewModel() {

    private val _week = MutableStateFlow<List<Day>>(emptyList())
    val week: StateFlow<List<Day>> = _week.asStateFlow()

    private var getWeekJob: Job? = null
    fun selectWeek(firstDate: LocalDate) {
        getWeekJob?.cancel()
        getWeekJob = viewModelScope.launch {
            statsChartPageUseCases.getWeek(firstDate).collect { week ->
                _week.value = week
            }
        }
    }

    companion object Factory : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            StepCounter.init(application)

            val forestDatabase = StepCounter.stepCounterDatabase
            val dayRepository = DayRepositoryImpl(forestDatabase.dayDao)
            val settingsStore = StepCounter.settingsStore
            val settingsRepository = SettingsRepositoryImpl(settingsStore)
            val statsChartPageUseCases = StatsChartPageUseCases(dayRepository, settingsRepository)

            return StatsChartPageViewModel(statsChartPageUseCases) as T
        }
    }
}