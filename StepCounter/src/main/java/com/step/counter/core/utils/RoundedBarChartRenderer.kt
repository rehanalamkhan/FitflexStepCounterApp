package com.step.counter.core.utils

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.github.mikephil.charting.animation.ChartAnimator
import com.github.mikephil.charting.buffer.BarBuffer
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.interfaces.dataprovider.BarDataProvider
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.renderer.BarChartRenderer
import com.github.mikephil.charting.utils.ViewPortHandler
import kotlin.collections.get
import kotlin.text.get

class RoundedBarChartRenderer(
    chart: BarChart,
    animator: ChartAnimator,
    viewPortHandler: ViewPortHandler,
    private val radius: Float,
    private val gradientIndices: Set<Int> = emptySet(),
    private val gradientStart: Int = 0,
    private val gradientEnd: Int = 0
) : BarChartRenderer(chart, animator, viewPortHandler) {

    private val gradientPaints = mutableMapOf<Int, Paint>()

    override fun drawDataSet(
        c: Canvas,
        dataSet: IBarDataSet,
        index: Int
    ) {
        val trans = mChart.getTransformer(dataSet.axisDependency)
        mBarBorderPaint.color = dataSet.barBorderColor
        mBarBorderPaint.strokeWidth = dataSet.barBorderWidth

        val phaseY = mAnimator.phaseY

        if (mBarBuffers == null) {
            initBuffers()
        }
        val buffer = mBarBuffers[index]
        buffer.setPhases(mAnimator.phaseX, phaseY)
        buffer.setDataSet(index)
        buffer.setInverted(mChart.isInverted(dataSet.axisDependency))
        buffer.setBarWidth(mChart.barData.barWidth)
        buffer.feed(dataSet)

        trans.pointValuesToPixel(buffer.buffer)

        for (j in buffer.buffer.indices step 4) {
            val left = buffer.buffer[j]
            val top = buffer.buffer[j + 1]
            val right = buffer.buffer[j + 2]
            val bottom = buffer.buffer[j + 3]

            if (!mViewPortHandler.isInBoundsLeft(right)) continue
            if (!mViewPortHandler.isInBoundsRight(left)) break

            val barIndex = j / 4

            // Use gradient paint for specific bars, regular paint for others
            val paint = if (barIndex in gradientIndices) {
                getGradientPaint(top, bottom)
            } else {
                mRenderPaint.apply {
                    color = dataSet.getColor(barIndex)
                }
            }

            val rect = RectF(left, top, right, bottom)
            c.drawRoundRect(rect, radius, radius, paint)

            // Draw border if needed
            if (dataSet.barBorderWidth > 0f) {
                c.drawRoundRect(rect, radius, radius, mBarBorderPaint)
            }
        }
    }

    private fun getGradientPaint(top: Float, bottom: Float): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, top,
                0f, bottom,
                gradientStart,
                gradientEnd,
                Shader.TileMode.CLAMP
            )
        }
    }
}
