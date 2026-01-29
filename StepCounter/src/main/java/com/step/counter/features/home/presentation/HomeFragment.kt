package com.step.counter.features.home.presentation

import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.step.counter.R
import com.step.counter.core.domain.model.Day
import com.step.counter.core.utils.RoundedBarChartRenderer
import com.step.counter.databinding.FragmentHomeBinding
import com.step.counter.features.home.data.model.StatsDetailsState
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar

class HomeFragment : Fragment() {

    private val grayColor = "#979797".toColorInt()
    private val gradientStart = "#EF3511".toColorInt()
    private val gradientEnd = "#FF7253".toColorInt()

    private val viewModel: StatsDetailsViewModel by activityViewModels { StatsDetailsViewModel }
    private val statsChartPageViewModel: StatsChartPageViewModel by viewModels { StatsChartPageViewModel.Factory }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var roundedRenderer: RoundedBarChartRenderer? = null
    private var previousSteps: List<Float>? = null
    private var chartValueAnimator: ValueAnimator? = null


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
        setGreeting()

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

        binding.menuBtn.setOnClickListener {
            findNavController().navigate(R.id.homeFragmentToSettingsFragment)
        }
    }

    private fun setGreeting() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        val greeting = when (hour) {
            in 5..11 -> getString(R.string.good_morning)
            in 12..16 -> getString(R.string.good_afternoon)
            in 17..20 -> getString(R.string.good_evening)
            else -> getString(R.string.good_night)
        }

        binding.greetingText.text = greeting
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

        // 1️⃣ Prepare new step values (Sun → Sat)
        val newSteps = week.take(7).map { it.steps.toFloat() }
        val gradientIndices = setOf(0, 2, 3, 5)

        val colors = newSteps.indices.map { index ->
            if (index in gradientIndices) gradientEnd else grayColor
        }

        // 2️⃣ Setup initial OR Update existing dataset
        val barData = barChart.data
        val dataSet = if (barData != null && barData.dataSetCount > 0) {
            barData.getDataSetByIndex(0) as BarDataSet
        } else {
            val entries = newSteps.mapIndexed { index, _ -> BarEntry(index.toFloat(), 0f) }
            val newDataSet = BarDataSet(entries, "")
            newDataSet.colors = colors
            newDataSet.setDrawValues(false)

            val newBarData = BarData(newDataSet)
            newBarData.barWidth = 0.5f
            barChart.data = newBarData
            barChart.setFitBars(true)
            newDataSet
        }
        dataSet.colors = colors

        // 3️⃣ Smooth transition animation
        chartValueAnimator?.cancel()
        val startSteps = previousSteps ?: List(7) { 0f }

        chartValueAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val animatedEntries = newSteps.mapIndexed { index, targetValue ->
                    val startValue = startSteps.getOrElse(index) { 0f }
                    val currentVal = startValue + (targetValue - startValue) * fraction
                    BarEntry(index.toFloat(), currentVal)
                }
                dataSet.values = animatedEntries
                barChart.data?.notifyDataChanged()
                barChart.notifyDataSetChanged()
                barChart.invalidate()
            }
            start()
        }
        previousSteps = newSteps

        // 4️⃣ Set renderer ONCE
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

        // 5️⃣ Axis & style
        val maxSteps = newSteps.maxOrNull() ?: 0f
        configureXAxis(barChart)
        configureYAxis(barChart, maxSteps)
        styleChart(barChart)

        // 6️⃣ Initial animation handle removed (now handled by chartValueAnimator)
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
        }
    }


}