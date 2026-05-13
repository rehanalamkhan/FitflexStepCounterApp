package com.step.counter.features.home.presentation

import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.step.counter.R
import com.step.counter.core.domain.model.Day
import com.step.counter.core.utils.RoundedBarChartRenderer
import com.step.counter.databinding.StepCounterFragmentHomeBinding
import com.step.counter.features.home.data.model.StatsDetailsState
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar

class StepCounterHomeFragment : Fragment() {

    private val grayColor = "#979797".toColorInt()
    private val primaryColor = "#8DC63F".toColorInt()

    private val viewModel: StatsDetailsViewModel by activityViewModels { StatsDetailsViewModel.Factory }
    private val statsChartPageViewModel: StatsChartPageViewModel by viewModels { StatsChartPageViewModel.Factory }

    private var _binding: StepCounterFragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var roundedRenderer: RoundedBarChartRenderer? = null
    private var previousSteps: List<Float>? = null
    private var chartValueAnimator: ValueAnimator? = null

    /** Anchor for refreshing week data when returning from Settings (same Sun→Sat range). */
    private var selectedWeekStart: LocalDate? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = StepCounterFragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val today = LocalDate.now()
        val firstDayOfWeek = today.minusDays(today.dayOfWeek.value % 7L)
        selectedWeekStart = firstDayOfWeek
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

    override fun onResume() {
        super.onResume()
        // Re-query week flow so chart picks up edited daily_goal from SharedPreferences / SettingsStore.
        selectedWeekStart?.let { statsChartPageViewModel.selectWeek(it) }
    }

    /** Same key/default as [com.step.counter.features.settings.data.source.SettingsStoreImpl]. */
    private fun currentDailyGoalFromPrefs(): Int =
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getString(PREF_KEY_DAILY_GOAL, "")?.toIntOrNull() ?: DEFAULT_DAILY_GOAL

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

        // Always read latest goal here so bar colors match Settings immediately (avoids stale Day.goal / renderer state).
        val goal = currentDailyGoalFromPrefs()

        // 1️⃣ Prepare new step values (Sun → Sat)
        val newSteps = week.take(7).map { it.steps.toFloat() }

        val gradientIndices = week.take(7)
            .mapIndexedNotNull { index, day ->
                if (day.steps >= goal) index else null
            }
            .toSet()

        // New list each draw — MPAndroidChart mutates internally; reassignment ensures grey vs gradient is correct.
        val colors = newSteps.indices.map { index ->
            if (index in gradientIndices) primaryColor else grayColor
        }

        // 2️⃣ Setup initial OR Update existing dataset
        val barData = barChart.data
        val dataSet = if (barData != null && barData.dataSetCount > 0) {
            barData.getDataSetByIndex(0) as BarDataSet
        } else {
            val entries = newSteps.mapIndexed { index, _ -> BarEntry(index.toFloat(), 0f) }
            val newDataSet = BarDataSet(entries, "")
            newDataSet.colors = colors.toMutableList()
            newDataSet.setDrawValues(false)

            val newBarData = BarData(newDataSet)
            newBarData.barWidth = 0.5f
            barChart.data = newBarData
            barChart.setFitBars(true)
            newDataSet
        }
        dataSet.colors = colors.toMutableList()
        barChart.data?.notifyDataChanged()
        barChart.notifyDataSetChanged()

        // Install / refresh custom renderer before animating so gradient vs grey matches current goal immediately.
        if (roundedRenderer == null) {
            roundedRenderer = RoundedBarChartRenderer(
                barChart,
                barChart.animator,
                barChart.viewPortHandler,
                radius = 20f,
                initialGradientIndices = gradientIndices,
                gradientStart = primaryColor,
                gradientEnd = primaryColor
            )
            barChart.renderer = roundedRenderer
        } else {
            roundedRenderer!!.gradientIndices = gradientIndices
        }

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

        // 4️⃣ Axis & style
        val maxSteps = newSteps.maxOrNull() ?: 0f
        configureXAxis(barChart)
        configureYAxis(barChart, maxSteps)
        styleChart(barChart)

        // 5️⃣ Initial animation handle removed (now handled by chartValueAnimator)
        barChart.invalidate()
    }

    private companion object {
        /** Mirrors SettingsStoreImpl preference default for first launch before any write. */
        private const val DEFAULT_DAILY_GOAL = 8000

        /** Same key as SettingsStoreImpl — keep in sync if renamed. */
        private const val PREF_KEY_DAILY_GOAL = "daily_goal"
    }

    private fun configureXAxis(barChart: BarChart) {
        val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        barChart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(days)
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            setDrawAxisLine(false)
            granularity = 1f
            activity?.let { textColor = ContextCompat.getColor(it, R.color.secondary) }
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