package com.step.counter.features.home.domain.usecase

import com.step.counter.core.domain.repository.DayRepository

import com.step.counter.features.settings.domain.repository.SettingsRepository

class StatsChartPageUseCases(
    dayRepository: DayRepository,
    settingsRepository: SettingsRepository
) {

    val getWeek: GetWeek = GetWeekImpl(dayRepository, settingsRepository)
}