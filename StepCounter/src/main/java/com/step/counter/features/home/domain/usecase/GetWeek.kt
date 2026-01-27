package com.step.counter.features.home.domain.usecase

import com.step.counter.core.domain.model.Day
import com.step.counter.core.domain.repository.DayRepository
import com.step.counter.features.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

interface GetWeek {

    operator fun invoke(startingAt: LocalDate): Flow<List<Day>>
}

class GetWeekImpl(
    private val dayRepository: DayRepository,
    private val settingsRepository: SettingsRepository
) : GetWeek {

    override fun invoke(startingAt: LocalDate): Flow<List<Day>> {
        val endingAt = startingAt.plusDays(6)
        val setingsFlow = settingsRepository.getSettings()
        val daysFlow = dayRepository.getDays(startingAt..endingAt)
        return setingsFlow.combine(daysFlow) { settings, days ->
            days.map { day ->
                if (day.height == 0 || day.weight == 0 || day.stepLength == 0) {
                    day.copy(
                        goal = settings.dailyGoal,
                        height = settings.height,
                        weight = settings.weight,
                        stepLength = settings.stepLength,
                        pace = settings.pace
                    )
                } else {
                    day
                }
            }
        }
    }
}