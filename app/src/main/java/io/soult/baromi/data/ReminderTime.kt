package io.soult.baromi.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A configured daily reminder time (local wall-clock hour:minute). */
@Entity(tableName = "reminder_time")
data class ReminderTime(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
) {
    init {
        require(hour in 0..23) { "hour out of range 0..23: $hour" }
        require(minute in 0..59) { "minute out of range 0..59: $minute" }
    }

    companion object {
        const val MIN_PER_DAY = 1
        const val MAX_PER_DAY = 8

        /** Seeded on first run (3/day). */
        val DEFAULTS = listOf(
            ReminderTime(hour = 9, minute = 0),
            ReminderTime(hour = 14, minute = 0),
            ReminderTime(hour = 20, minute = 0),
        )
    }
}
