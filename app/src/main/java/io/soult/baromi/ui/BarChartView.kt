package io.soult.baromi.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.max

/**
 * A minimal, dependency-free bar chart: evenly spaced bars over a fixed [minValue]..[maxValue]
 * y-range, themed from the Material color roles. Used for per-category daily averages (range 1..5)
 * and tag frequency (range 0..maxCount). Missing days are simply absent bars — nothing interpolates.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** One bar: a value and an accessibility label (not drawn, exposed via contentDescription). */
    data class Bar(val value: Double, val label: String)

    private var bars: List<Bar> = emptyList()
    private var minValue: Double = 0.0
    private var maxValue: Double = 1.0

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@BarChartView, com.google.android.material.R.attr.colorPrimary)
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@BarChartView, com.google.android.material.R.attr.colorOutlineVariant)
        strokeWidth = context.resources.displayMetrics.density // 1dp
    }
    private val rect = RectF()

    /** Set bars and the y-range. [maxValue] must exceed [minValue]; callers guarantee that. */
    fun setData(bars: List<Bar>, minValue: Double, maxValue: Double) {
        this.bars = bars
        this.minValue = minValue
        this.maxValue = max(maxValue, minValue + 1.0)
        contentDescription = bars.joinToString(", ") { "${it.label}: ${it.value}" }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val baseline = h - 1f
        canvas.drawLine(0f, baseline, w, baseline, axisPaint)
        if (bars.isEmpty()) return

        val span = (maxValue - minValue).toFloat()
        val slot = w / bars.size
        val barWidth = slot * 0.7f
        val sidePad = (slot - barWidth) / 2f
        for ((i, bar) in bars.withIndex()) {
            val norm = ((bar.value - minValue) / span).toFloat().coerceIn(0f, 1f)
            val top = baseline - norm * baseline
            val left = i * slot + sidePad
            rect.set(left, top, left + barWidth, baseline)
            canvas.drawRoundRect(rect, barWidth * 0.2f, barWidth * 0.2f, barPaint)
        }
    }
}
