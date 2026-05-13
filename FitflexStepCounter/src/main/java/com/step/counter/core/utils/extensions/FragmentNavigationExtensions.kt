package com.step.counter.core.utils.extensions

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.step.counter.integration.StepCounterHost

/**
 * Pops the nearest Navigation back stack when this fragment is hosted inside one.
 * Otherwise pops the parent [FragmentManager] back stack.
 */
fun Fragment.safeBack(): Boolean {
    if (!isAdded) return false

    val navPopped = runCatching { findNavController().popBackStack() }.getOrDefault(false)
    if (navPopped) return true

    if (parentFragmentManager.backStackEntryCount > 0) {
        parentFragmentManager.popBackStack()
        return true
    }

    return false
}

/**
 * Navigates through the nearest [NavController] when available.
 * Returns false when no Navigation host is attached to this fragment.
 */
fun Fragment.safeNavigate(@IdRes destinationId: Int, args: Bundle? = null): Boolean {
    if (!isAdded) return false

    return runCatching {
        val navController = findNavController()
        if (args == null) {
            navController.navigate(destinationId)
        } else {
            navController.navigate(destinationId, args)
        }
        true
    }.getOrDefault(false)
}

/**
 * Pops the host that presented [com.step.counter.StepCounterFragment], whether that host uses
 * Navigation Component or a plain [FragmentManager] transaction.
 */
fun Fragment.popStepCounterHostBackStack(): Boolean {
    if (!isAdded) return false

    val parentNavPopped = runCatching { findNavController().popBackStack() }.getOrDefault(false)
    if (parentNavPopped) return true

    val backStackName = tag?.takeIf { it.isNotEmpty() }
        ?: StepCounterHost.DEFAULT_BACK_STACK_NAME
    val entryCount = parentFragmentManager.backStackEntryCount
    for (index in entryCount - 1 downTo 0) {
        if (parentFragmentManager.getBackStackEntryAt(index).name == backStackName) {
            parentFragmentManager.popBackStack(backStackName, 0)
            return true
        }
    }

    if (parentFragmentManager.backStackEntryCount > 0) {
        parentFragmentManager.popBackStack()
        return true
    }

    return false
}
