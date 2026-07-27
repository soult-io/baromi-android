package io.soult.baromi.stats

import kotlin.math.max

/**
 * Pure bar-chart geometry: what fraction of the plot height a value fills over a fixed y-range.
 * Android-free so the range flooring and clamping are unit-tested without rendering.
 */
object BarMetrics {

    /** Top of the y-axis, never below [minValue] + 1 so a degenerate range still leaves height. */
    fun effectiveMax(minValue: Double, maxValue: Double): Double = max(maxValue, minValue + 1.0)

    /** 0..1 height fraction for [value] over [minValue]..[maxValue] (clamped to the range). */
    fun heightFraction(value: Double, minValue: Double, maxValue: Double): Double {
        val span = effectiveMax(minValue, maxValue) - minValue
        return ((value - minValue) / span).coerceIn(0.0, 1.0)
    }
}
