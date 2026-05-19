package com.step.counter.core.utils

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracks whether the host app process is in the foreground via [ProcessLifecycleOwner].
 * Used to avoid [android.app.ForegroundServiceStartNotAllowedException] on Android 12+.
 */
object AppForegroundState {

    private const val TAG = "AppForegroundState"

    private val isInForeground = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)

    fun ensureInitialized() {
        if (!isInitialized.compareAndSet(false, true)) return
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isInForeground.set(true)
                Log.d(TAG, "Process moved to foreground")
            }

            override fun onStop(owner: LifecycleOwner) {
                isInForeground.set(false)
                Log.d(TAG, "Process moved to background")
            }
        })
        val currentState = ProcessLifecycleOwner.get().lifecycle.currentState
        isInForeground.set(currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        Log.d(TAG, "Initialized processForeground=$isInForeground currentState=$currentState")
    }

    fun isAppInForeground(): Boolean = isInForeground.get()
}

/** True when the app process is visible (at least one activity started). */
fun Context.isAppInForeground(): Boolean {
    val app = applicationContext as? Application ?: return false
    AppForegroundState.ensureInitialized()
    return AppForegroundState.isAppInForeground()
}
