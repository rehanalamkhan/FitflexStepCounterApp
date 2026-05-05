package com.step.counter.features.home.domain.usecase

import com.step.counter.core.domain.model.Day
import com.step.counter.core.domain.model.of
import com.step.counter.core.domain.repository.DayRepository
import com.step.counter.features.settings.domain.model.Settings
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
        val settingsFlow = settingsRepository.getSettings()
        val daysFlow = dayRepository.getDays(startingAt..endingAt)
        return settingsFlow.combine(daysFlow) { settings, days ->
            val patchedDays = days.map { mergeDayWithLatestSettings(it, settings) }
            buildSevenDayWeek(startingAt, endingAt, patchedDays, settings)
        }
    }

    /** Keeps persisted steps/metrics but always applies current daily goal from settings (chart compares to latest goal). */
    private fun mergeDayWithLatestSettings(day: Day, settings: Settings): Day {
        val missingLegacyMetrics =
            day.height == 0 || day.weight == 0 || day.stepLength == 0
        return if (missingLegacyMetrics) {
            day.copy(
                goal = settings.dailyGoal,
                height = settings.height,
                weight = settings.weight,
                stepLength = settings.stepLength,
                pace = settings.pace,
            )
        } else {
            day.copy(goal = settings.dailyGoal)
        }
    }

    /** Sun→Sat inclusive; gaps filled with zeros using **current** goal from settings (never goal=0). */
    private fun buildSevenDayWeek(
        start: LocalDate,
        end: LocalDate,
        days: List<Day>,
        settings: Settings,
    ): List<Day> {
        val result = ArrayList<Day>(7)
        var date = start
        while (date <= end) {
            val existing = days.singleOrNull { it.date == date }
            result.add(existing ?: Day.of(date, settings, steps = 0))
            date = date.plusDays(1)
        }
        return result
    }
}