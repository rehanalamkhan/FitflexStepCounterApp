package com.counter.step

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.counter.step.databinding.ActivityMainBinding
import com.step.counter.StepCounterFragment
import com.step.counter.features.home.presentation.StatsDetailsViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val viewModel: StatsDetailsViewModel by viewModels { StatsDetailsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        observeStatsFromViewModel()
        loadStepCounterFragment()
    }

    /**
     * Observes [StatsDetailsViewModel.day] (steps, distance, calories, goal) while this activity
     * is started. Replace the [Log] line with UI updates (TextView, Compose, etc.) as needed.
     */
    private fun observeStatsFromViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.day.collect { stats ->

                    val stepsText = resources.getQuantityString(
                        com.step.counter.R.plurals.step_count_format, stats.stepsTaken,
                    )
                    val distanceText = getString(
                        com.step.counter.R.string.distance_travelled_format, stats.distanceTravelled
                    )

                    val caloriesBurned = stats.calorieBurned
                }
            }
        }
    }

    private fun loadStepCounterFragment() {
        if (supportFragmentManager.findFragmentByTag("StepCounter") == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    com.counter.step.R.id.fragment_container,
                    StepCounterFragment.newInstance(),
                    "StepCounter"
                )
                .commit()
        }
    }
}
