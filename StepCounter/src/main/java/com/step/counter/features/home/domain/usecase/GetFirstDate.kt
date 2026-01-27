package com.step.counter.features.home.domain.usecase

import com.step.counter.core.domain.repository.DayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

interface GetFirstDate {

    operator fun invoke(): Flow<LocalDate>
}

class GetFirstDateImpl(
    private val dayRepository: DayRepository
) : GetFirstDate {

    override fun invoke(): Flow<LocalDate> {
        return dayRepository.getFirstDay().map { it?.date ?: LocalDate.now() }
    }
}