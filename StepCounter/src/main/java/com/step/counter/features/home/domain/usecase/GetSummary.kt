package com.step.counter.features.home.domain.usecase

import com.step.counter.core.domain.model.StatsSummary
import com.step.counter.core.domain.model.of
import com.step.counter.core.domain.repository.DayRepository

interface GetSummary {
    suspend operator fun invoke(): StatsSummary
}

class GetSummaryImpl(
    private val dayRepository: DayRepository
) : GetSummary {

    override suspend operator fun invoke(): StatsSummary {
        val allDays = dayRepository.getAllDays()
        return StatsSummary.of(allDays)
    }
}