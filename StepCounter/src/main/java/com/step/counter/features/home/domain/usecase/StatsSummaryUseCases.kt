package com.step.counter.features.home.domain.usecase

import com.step.counter.core.domain.repository.DayRepository


class StatsSummaryUseCases(
    dayRepository: DayRepository
) {

    val getSummary: GetSummary = GetSummaryImpl(dayRepository)
}