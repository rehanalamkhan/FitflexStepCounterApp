package com.step.counter

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import com.step.counter.core.service.StepCounterService
import com.step.counter.core.utils.extensions.setInsets
import com.step.counter.databinding.StepCounterActivityMainBinding


class StepCounterMainActivity : FragmentActivity() {

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val binding = StepCounterActivityMainBinding.inflate(layoutInflater)
        setInsets(binding.root)
        setContentView(binding.root)
        PreferenceManager.setDefaultValues(this, R.xml.settings, false)

        StepCounter.init(this)
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        navController.setGraph(R.navigation.nav_graph)

        startStepCounterService()
    }

    private fun startStepCounterService() {
        val intent = Intent(this, StepCounterService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}