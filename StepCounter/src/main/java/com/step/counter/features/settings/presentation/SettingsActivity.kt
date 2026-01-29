package com.step.counter.features.settings.presentation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.MaterialToolbar
import com.step.counter.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_settings)
        setupActionBar()
    }

    private fun setupActionBar() {
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
}