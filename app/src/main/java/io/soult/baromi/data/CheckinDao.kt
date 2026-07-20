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

    @Update
    suspend fun update(checkin: Checkin)

    @Delete
    suspend fun delete(checkin: Checkin)

    @Query("SELECT * FROM checkin WHERE id = :id")
    suspend fun getById(id: Long): Checkin?

    // id is a deterministic tiebreaker so same-millisecond check-ins export in a stable order.
    @Query("SELECT * FROM checkin ORDER BY answered_time DESC, id DESC")
    suspend fun getAll(): List<Checkin>

    @Transaction
    @Query("SELECT * FROM checkin WHERE id = :id")
    suspend fun getWithTags(id: Long): CheckinWithTags?

    // IGNORE keeps tag attachment idempotent — re-attaching the same (checkin, tag) is a no-op,
    // so a double-tap in the capture UI can't crash on the composite-PK conflict.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun attachTag(link: CheckinTag)

    @Query("SELECT COUNT(*) FROM checkin_tag")
    suspend fun countTagLinks(): Int
}
