package com.step.counter.features.home.presentation

import java.time.LocalDate
import java.time.DayOfWeek


import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.step.counter.databinding.FragmentHomeBinding
import androidx.core.graphics.toColorInt
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.model.GradientColor
import com.step.counter.R
import com.step.counter.core.domain.model.Day
import com.step.counter.core.utils.RoundedBarChartRenderer
import com.step.counter.features.home.data.model.StatsDetailsState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import kotlin.getValue


class HomeFragment : Fragment() {

    private val grayColor = "#979797".toColorInt()
    private val gradientStart = "#EF3511".toColorInt()
    private val gradientEnd = "#FF7253".toColorInt()

    private val viewModel: StatsDetailsViewModel by activityViewModels { StatsDetailsViewModel }
    private val statsChartPageViewModel: StatsChartPageViewModel by viewModels { StatsChartPageViewModel.Factory }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var isChartAnimated = false
    private var roundedRenderer: RoundedBarChartRenderer? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val today = LocalDate.now()
        val firstDayOfWeek = today.minusDays(today.dayOfWeek.value % 7L)
        statsChartPageViewModel.selectWeek(firstDayOfWeek)


        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                statsChartPageViewModel.week.collect { week ->
                    Log.d("stats***", "onViewCreated: week: $week")
                    if (week.isNotEmpty()) {
                        updateBarChart(binding.barChart, week)
                    }
                }
            }
        }
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                    viewModel.day.collect {
                        Log.d("stats***", "onViewCreated: statsDetailsState: $it")
                        updateUserInterface(it)
                    }

            }
        }
    }

    private fun updateUserInterface(statsDetailsState: StatsDetailsState) =
        statsDetailsState.apply {
            val stepsText = resources.getQuantityString(
                R.plurals.step_count_format, stepsTaken,
            )
            val distanceText = getString(
                R.string.distance_travelled_format, distanceTravelled
            )

            with(binding) {

                todayTargetText.text =
                    getString(R.string.target_steps, goal.toString())

                with(distanceLayout) {
                    image.setImageResource(R.drawable.ic_distance)
                    valueText.text = distanceText
                    descText.text = getString(R.string.distance)
                }
                with(stepsLayout) {
                    image.setImageResource(R.drawable.ic_steps)
                    valueText.text = stepsTaken.toString()
                    descText.text = stepsText
                }
                with(caloryLayout) {
                    image.setImageResource(R.drawable.ic_kcal)
                    valueText.text = calorieBurned.toString()
                    descText.text = getString(R.string.kcal)
                }
            }
        }

    private fun updateBarChart(barChart: BarChart, week: List<Day>) {

        // 1️⃣ Prepare step values (Sun → Sat)
        val steps = week.take(7).map { it.steps.toFloat() }
        val gradientIndices = setOf(0, 2, 3, 5)

        val entries = ArrayList<BarEntry>()
        steps.forEachIndexed { index, value ->
            entries.add(BarEntry(index.toFloat(), value))
        }

        val colors = steps.indices.map { index ->
            if (index in gradientIndices) gradientEnd else grayColor
        }

        // 2️⃣ Update existing dataset OR create once
        val barData = barChart.data
        if (barData != null && barData.dataSetCount > 0) {

            val dataSet = barData.getDataSetByIndex(0) as BarDataSet
            dataSet.values = entries
            dataSet.colors = colors

            barData.notifyDataChanged()
            barChart.notifyDataSetChanged()

        } else {

            val dataSet = BarDataSet(entries, "")
            dataSet.colors = colors
            dataSet.setDrawValues(false)

            val newBarData = BarData(dataSet)
            newBarData.barWidth = 0.5f
            barChart.data = newBarData
            barChart.setFitBars(true)
        }

        // 3️⃣ Set renderer ONCE
        if (roundedRenderer == null) {
            roundedRenderer = RoundedBarChartRenderer(
                barChart,
                barChart.animator,
                barChart.viewPortHandler,
                radius = 20f,
                gradientIndices = gradientIndices,
                gradientStart = gradientStart,
                gradientEnd = gradientEnd
            )
            barChart.renderer = roundedRenderer
        }

        // 4️⃣ Axis & style
        val maxSteps = steps.maxOrNull() ?: 0f
        configureXAxis(barChart)
        configureYAxis(barChart, maxSteps)
        styleChart(barChart)

        // 5️⃣ Animate only ONCE
        if (!isChartAnimated) {
            barChart.animateY(800)
            isChartAnimated = true
        }

        barChart.invalidate()
    }


    private fun setupBarChart(barChart: BarChart) {
        val steps = listOf(4000f, 4500f, 3500f, 5600f, 4900f, 3100f, 3700f)
        val gradientIndices = setOf(0, 2, 3, 5) // Indices that should have gradient

        val entries = ArrayList<BarEntry>()
        steps.forEachIndexed { index, value ->
            entries.add(BarEntry(index.toFloat(), value))
        }

        val dataSet = BarDataSet(entries, "")

        // Set colors for each bar
        val colors = ArrayList<Int>()
        steps.indices.forEach { index ->
            if (index in gradientIndices) {
                colors.add(gradientEnd)
            } else {
                colors.add(grayColor)
            }
        }
        dataSet.colors = colors
        dataSet.setDrawValues(false)

        val barData = BarData(dataSet)
        barData.barWidth = 0.5f

        barChart.data = barData
        barChart.setFitBars(true)

        // Rounded bars renderer with gradient support
        barChart.renderer = RoundedBarChartRenderer(
            barChart,
            barChart.animator,
            barChart.viewPortHandler,
            radius = 20f,
            gradientIndices = gradientIndices,
            gradientStart = gradientStart,
            gradientEnd = gradientEnd
        )

        configureXAxis(barChart)
        configureYAxis(barChart)
        styleChart(barChart)

        barChart.invalidate()
    }

    private fun configureXAxis(barChart: BarChart) {
        val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        barChart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(days)
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            setDrawAxisLine(false)
            granularity = 1f
            textColor = Color.DKGRAY
            textSize = 12f
        }
    }

    private fun configureYAxis(barChart: BarChart, maxSteps: Float = 8000f) {
        val yAxisMax =
            if (maxSteps < 1000f) 1000f else if (maxSteps < 8000f) 8000f else maxSteps * 1.2f

        barChart.axisLeft.apply {
            axisMinimum = 0f
            axisMaximum = yAxisMax
            granularity = yAxisMax / 8f
            setDrawGridLines(false)
            setDrawAxisLine(false)
            textColor = Color.DKGRAY
        }
        barChart.axisRight.isEnabled = false
    }

    private fun styleChart(barChart: BarChart) {
        barChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setScaleEnabled(false)
            setDrawGridBackground(false)
            setExtraOffsets(10f, 10f, 10f, 10f)
            animateY(800)
        }
    }


}