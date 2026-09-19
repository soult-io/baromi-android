package io.soult.baromi.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface CheckinDao {
    @Insert
    suspend fun insert(checkin: Checkin): Long

    @Insert
    suspend fun insertValue(value: CheckinValue)

    @Update
    suspend fun update(checkin: Checkin)

    @Delete
    suspend fun delete(checkin: Checkin)

    // Edit path replaces a check-in's edited categories + its tags inside a transaction. Scoped to
    // the supplied categories so a value for a since-disabled category (absent from the edit form)
    // is preserved rather than silently dropped.
    @Query("DELETE FROM checkin_value WHERE checkin_id = :checkinId AND category_id IN (:categoryIds)")
    suspend fun deleteValuesForCategories(checkinId: Long, categoryIds: List<Long>)

    @Query("DELETE FROM checkin_tag WHERE checkin_id = :checkinId")
    suspend fun clearTags(checkinId: Long)

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
