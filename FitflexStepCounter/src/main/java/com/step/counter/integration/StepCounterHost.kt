package com.step.counter.integration

import androidx.annotation.IdRes
import androidx.fragment.app.FragmentManager
import com.step.counter.StepCounterFragment

/**
 * Entry point for apps that host [StepCounterFragment] with [FragmentManager] instead of
 * Navigation Component.
 */
object StepCounterHost {

    const val DEFAULT_FRAGMENT_TAG = "StepCounter"
    const val DEFAULT_BACK_STACK_NAME = "StepCounter"

    /**
     * Replaces [containerId] with [StepCounterFragment] and optionally records a back stack entry.
     *
     * Use a normal app-owned container such as `R.id.fragment_container`. Do not target a
     * `NavHostFragment` container from the activity [FragmentManager].
     */
    @JvmOverloads
    fun show(
        fragmentManager: FragmentManager,
        @IdRes containerId: Int,
        tag: String = DEFAULT_FRAGMENT_TAG,
        backStackName: String = DEFAULT_BACK_STACK_NAME,
        addToBackStack: Boolean = true,
    ): StepCounterFragment {
        val existing = fragmentManager.findFragmentByTag(tag) as? StepCounterFragment
        if (existing != null) {
            return existing
        }

        val fragment = StepCounterFragment.newInstance()
        val transaction = fragmentManager.beginTransaction()
            .replace(containerId, fragment, tag)

        if (addToBackStack) {
            transaction.addToBackStack(backStackName)
        }

        transaction.commit()
        return fragment
    }
}
