package com.step.counter.features.home.util

import androidx.annotation.AttrRes
import com.google.android.material.R
import com.step.counter.core.domain.model.Day
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*

operator fun ClosedRange<LocalDate>.iterator() = object : Iterator<LocalDate> {

    private var current = start.minusDays(1)

    override fun hasNext(): Boolean {
        return current.isBefore(endInclusive)
    }

    override fun next(): LocalDate {
        if (current.isBefore(endInclusive)) {
            current = current.plusDays(1)
        }
        return current
    }
}

fun List<Day>.toChartValues(
    max: Int,
    locale: Locale,
    activeDay: LocalDate
): List<ChartValue<LocalDate>> = map {
    val value = it.steps / max.toDouble()
    val weekdayName = it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    val isSelected = it.date.isEqual(activeDay)
    val barColor =
        if (isSelected) android.R.attr.colorPrimary
        else R.attr.colorPrimaryContainer
    val textColor =
        if (isSelected) android.R.attr.colorPrimary
        else R.attr.colorOnSurface
    ChartValue(
        it.date,
        value = value,
        label = weekdayName,
        barColor = barColor,
        textColor = textColor
    )
}

data class ChartValue<T>(

    val id: T,

    val value: Double,

    val label: String,

    @field:AttrRes
    val barColor: Int,

    @field:AttrRes
    val textColor: Int,
)