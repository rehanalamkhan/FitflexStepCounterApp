package com.step.counter.features.settings.presentation

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.step.counter.R
import com.step.counter.databinding.FragmentSettingsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.core.graphics.toColorInt

class SettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by activityViewModels { SettingsViewModel }


    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private var selectedTargetStep = 8000


    val data = (1000..8000 step 500).toList()

    private val stepValues = (1000..8000 step 500).map { it.toString() }.toTypedArray()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.observeSettingsChanges()
        setupNumberPicker()
        observeSettings()
        binding.getStarted.setOnClickListener {
            viewModel.updateDailyGoal(selectedTargetStep)
            val action = R.id.settingsFragmentToHomeFragment
            findNavController().navigate(action)
        }
    }

    private fun setupNumberPicker() {
        binding.widgetStepsPicker.apply {
            numberPicker.apply {
                // Configure basic properties
                minValue = 0
                maxValue = stepValues.size - 1
                displayedValues = stepValues
                wrapSelectorWheel = false
                descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS


                // Handle value changes
                setOnValueChangedListener { _, _, newVal ->
                    selectedTargetStep = stepValues[newVal].toInt()
                }

                post {
                    try {
                        val field = this.javaClass.getDeclaredField("mSelectedTextPaint")
                        field.isAccessible = true
                        val paint = field.get(this) as Paint

                        val startColor = "#EF3511".toColorInt()
                        val endColor = "#FF7253".toColorInt()

                        val shader = LinearGradient(
                            0f, 0f,
                            0f, paint.textSize,
                            startColor,
                            endColor,
                            Shader.TileMode.CLAMP
                        )

                        paint.shader = shader
                        paint.isFakeBoldText = true

                        val mItemSpacingField = this.javaClass.getDeclaredField("mItemSpacing")
                        mItemSpacingField.isAccessible = true

                        // set EXACT spacing you want (in px)
                        val spacingPx = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            0f, // 👈 real zero spacing
                            resources.displayMetrics
                        ).toInt()

                        mItemSpacingField.setInt(this, spacingPx)

                        invalidate()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            tvLabel.viewTreeObserver.addOnGlobalLayoutListener(object :
                android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    activity?.let { activity ->
                        tvLabel.viewTreeObserver.removeOnGlobalLayoutListener(this)

                        // Get colors from resources
                        val startColor = ContextCompat.getColor(activity, R.color.startColor)
                        val endColor = ContextCompat.getColor(activity, R.color.endColor)

                        // Create the LinearGradient shader
                        val textShader = LinearGradient(
                            0f,
                            0f,
                            tvLabel.width.toFloat(),
                            tvLabel.height.toFloat(),
                            intArrayOf(startColor, endColor),
                            null,
                            Shader.TileMode.CLAMP
                        )

                        tvLabel.paint.shader = textShader
                    }
                }
            })
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.getSettings().collectLatest { settings ->
                    val goal = settings.dailyGoal

                    // Find the closest value in our array
                    val index = stepValues.indexOfFirst { it.toInt() >= goal }
                    val targetIndex = if (index != -1) index else stepValues.size - 1

                    // Set the value
                    binding.widgetStepsPicker.numberPicker.value = targetIndex
                    selectedTargetStep = stepValues[targetIndex].toInt()
                }
            }
        }
    }


}