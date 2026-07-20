package io.soult.baromi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The scale direction is a frozen contract — these values are derived from the spec. */
class ScaleSchemaTest {

    @Test
    fun bounds_are_one_to_five() {
        assertEquals(1, ScaleSchema.MIN)
        assertEquals(5, ScaleSchema.MAX)
    }

    @Test
    fun anxiety_higher_means_calmer() {
        // Anxiety must run the same direction as the others: 5 = better = calm.
        assertEquals("very anxious" to "very calm", ScaleSchema.axes["anxiety"])
    }

    @Test
    fun mood_and_energy_higher_is_better() {
        assertEquals("very low" to "very good", ScaleSchema.axes["mood"])
        assertEquals("drained" to "energized", ScaleSchema.axes["energy"])
    }

    @Test
    fun in_range_guards_bounds() {
        assertFalse(ScaleSchema.inRange(0))
        assertTrue(ScaleSchema.inRange(1))
        assertTrue(ScaleSchema.inRange(5))
        assertFalse(ScaleSchema.inRange(6))
    }

    @Test
    fun version_is_set() {
        assertTrue(ScaleSchema.VERSION >= 1)
    }
}
