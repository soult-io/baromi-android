package io.soult.baromi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReminderTimeValidationTest {

    @Test
    fun accepts_full_day_range() {
        ReminderTime(hour = 0, minute = 0)
        ReminderTime(hour = 23, minute = 59)
    }

    @Test
    fun rejects_hour_out_of_range() {
        assertThrows(IllegalArgumentException::class.java) { ReminderTime(hour = 24, minute = 0) }
        assertThrows(IllegalArgumentException::class.java) { ReminderTime(hour = -1, minute = 0) }
    }

    @Test
    fun rejects_minute_out_of_range() {
        assertThrows(IllegalArgumentException::class.java) { ReminderTime(hour = 0, minute = 60) }
        assertThrows(IllegalArgumentException::class.java) { ReminderTime(hour = 0, minute = -1) }
    }

    @Test
    fun defaults_are_three_per_day() {
        assertEquals(3, ReminderTime.DEFAULTS.size)
    }
}
