package io.soult.baromi.stats

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure bar geometry — plain JUnit. */
class BarMetricsTest {

    @Test
    fun fraction_spans_the_range() {
        assertEquals(0.0, BarMetrics.heightFraction(0.0, 0.0, 5.0), 1e-9)
        assertEquals(1.0, BarMetrics.heightFraction(5.0, 0.0, 5.0), 1e-9)
        assertEquals(0.6, BarMetrics.heightFraction(3.0, 0.0, 5.0), 1e-9)
    }

    @Test
    fun values_outside_the_range_clamp() {
        assertEquals(1.0, BarMetrics.heightFraction(9.0, 0.0, 5.0), 1e-9)
        assertEquals(0.0, BarMetrics.heightFraction(-2.0, 0.0, 5.0), 1e-9)
    }

    @Test
    fun nonzero_baseline() {
        // Raw axis 1..5: value 3 → (3-1)/4 = 0.5.
        assertEquals(0.5, BarMetrics.heightFraction(3.0, 1.0, 5.0), 1e-9)
    }

    @Test
    fun degenerate_range_is_floored_to_one() {
        assertEquals(1.0, BarMetrics.effectiveMax(0.0, 0.0), 1e-9)
        assertEquals(0.0, BarMetrics.heightFraction(0.0, 0.0, 0.0), 1e-9)
        assertEquals(1.0, BarMetrics.heightFraction(1.0, 0.0, 0.0), 1e-9)
    }
}
