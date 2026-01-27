package com.step.counter.features.home.data.model

import java.time.LocalDate

data class StatsDetailsState(
    val date: LocalDate,
    val stepsTaken: Int,
    val goal: Int,
    val calorieBurned: Int,
    val distanceTravelled: Double,
    val chartDateRange: ClosedRange<LocalDate>
)