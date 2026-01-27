package com.step.counter.features.home.domain.usecase

import com.step.counter.core.domain.repository.DayRepository


class StatsDetailsUseCases(
    dayRepository: DayRepository
) {

    val getFirstDate: GetFirstDate = GetFirstDateImpl(dayRepository)
}