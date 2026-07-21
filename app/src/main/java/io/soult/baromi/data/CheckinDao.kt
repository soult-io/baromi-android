package io.soult.baromi.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CheckinDao {
    @Insert
    suspend fun insert(checkin: Checkin): Long

    @Insert
    suspend fun insertValue(value: CheckinValue)

    @Delete
    suspend fun delete(checkin: Checkin)

    @Query("SELECT * FROM checkin WHERE id = :id")
    suspend fun getById(id: Long): Checkin?

    @Query("SELECT * FROM checkin ORDER BY answered_time DESC, id DESC")
    suspend fun getAll(): List<Checkin>

    // IGNORE keeps tag attachment idempotent (no crash on a double-tap composite-PK conflict).
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun attachTag(link: CheckinTag)

    @Query("SELECT COUNT(*) FROM checkin_tag")
    suspend fun countTagLinks(): Int

    // Whole record, newest first, for history + CSV export.
    @Transaction
    @Query("SELECT * FROM checkin ORDER BY answered_time DESC, id DESC")
    suspend fun getAllWithDetails(): List<CheckinWithDetails>

    @Transaction
    @Query("SELECT * FROM checkin WHERE id = :id")
    suspend fun getWithDetails(id: Long): CheckinWithDetails?
}
