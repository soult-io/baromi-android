package io.soult.baromi.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReminderTimeDao {
    /** IGNORE on the unique (hour,minute) index: a duplicate time is a no-op returning -1. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(time: ReminderTime): Long

    @Delete
    suspend fun delete(time: ReminderTime)

    @Query("SELECT * FROM reminder_time ORDER BY hour, minute")
    suspend fun getAll(): List<ReminderTime>

    @Query("SELECT COUNT(*) FROM reminder_time")
    suspend fun count(): Int
}
