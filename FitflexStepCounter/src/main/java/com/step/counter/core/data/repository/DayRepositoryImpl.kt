package com.step.counter.core.data.repository

import kotlinx.coroutines.flow.Flow
import com.step.counter.core.data.source.DayDao
import com.step.counter.core.domain.model.Day
import com.step.counter.core.domain.model.DaySettings
import com.step.counter.core.domain.repository.DayRepository
import java.time.LocalDate

class DayRepositoryImpl(
    private val dao: DayDao
) : DayRepository {


    override fun getFirstDay(): Flow<Day?> {
        return dao.getFirstDay()
    }

    override fun getDay(date: LocalDate): Flow<Day?> {
        return dao.getDay(date)
    }

    override suspend fun getAllDays(): List<Day> {
        return dao.getAllDays()
    }

    override fun getDays(range: ClosedRange<LocalDate>): Flow<List<Day>> {
        return dao.getDays(range.start, range.endInclusive)
    }

    override suspend fun upsertDay(day: Day) {
        dao.upsertDay(day)
    }

    override suspend fun updateDaySettings(daySettings: DaySettings) {
        dao.updateDaySettings(daySettings)
    }
}