package com.step.counter.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.step.counter.core.domain.model.Day
import com.step.counter.core.domain.model.of
import com.step.counter.core.domain.repository.DayRepository
import com.step.counter.features.settings.domain.repository.SettingsRepository
import java.time.LocalDate

class GetDayImpl(
    private val dayRepository: DayRepository,
    private val settingsRepository: SettingsRepository,
) : GetDay {

    override fun invoke(date: LocalDate): Flow<Day> {
        val settingsFlow = settingsRepository.getSettings()
        val dayFlow = dayRepository.getDay(date)

        return settingsFlow.combine(dayFlow) { settings, day ->
            val finalDay = day ?: Day.of(date, settings, steps = 0)
            // If the record exists but has zero metrics, we update it with current settings
            if (finalDay.height == 0 || finalDay.weight == 0 || finalDay.stepLength == 0) {
                finalDay.copy(
                    goal = settings.dailyGoal,
                    height = settings.height,
                    weight = settings.weight,
                    stepLength = settings.stepLength,
                    pace = settings.pace
                )
            } else {
                finalDay
            }
        }
    }
}