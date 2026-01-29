package com.step.counter

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.step.counter.core.service.StepCounterService
import com.step.counter.core.utils.extensions.setInsets


class StepCounterMainActivity : FragmentActivity() {

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        setInsets(findViewById<ConstraintLayout>(R.id.mainLayout))

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        startStepCounterService()
    }

    private fun startStepCounterService() {
        val intent = Intent(this, StepCounterService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}