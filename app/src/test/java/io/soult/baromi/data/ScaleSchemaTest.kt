package io.soult.baromi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 1..5 scale is a frozen contract — these values are derived from the spec. */
class ScaleSchemaTest {

    @Test
    fun bounds_are_one_to_five() {
        assertEquals(1, ScaleSchema.MIN)
        assertEquals(5, ScaleSchema.MAX)
    }

    @Test
    fun in_range_guards_bounds() {
        assertFalse(ScaleSchema.inRange(0))
        assertTrue(ScaleSchema.inRange(1))
        assertTrue(ScaleSchema.inRange(5))
        assertFalse(ScaleSchema.inRange(6))
    }

    @Test
    fun require_in_range_throws_below_bound() {
        assertThrows(IllegalArgumentException::class.java) { ScaleSchema.requireInRange("value", 0) }
    }

    @Test
    fun require_in_range_throws_above_bound() {
        assertThrows(IllegalArgumentException::class.java) { ScaleSchema.requireInRange("value", 6) }
    }

    @Test
    fun require_in_range_allows_extremes() {
        ScaleSchema.requireInRange("value", 1)
        ScaleSchema.requireInRange("value", 5)
    }

    @Test
    fun version_is_set() {
        assertTrue(ScaleSchema.VERSION >= 1)
    }
}
