package io.soult.baromi.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TagDao {
    /** IGNORE on the unique label index: a duplicate label is a no-op returning -1. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: Tag): Long

    @Update
    suspend fun update(tag: Tag)

    @Delete
    suspend fun delete(tag: Tag)

    @Query("SELECT * FROM tag ORDER BY label")
    suspend fun getAll(): List<Tag>

    @Query("SELECT * FROM tag WHERE enabled = 1 ORDER BY label")
    suspend fun getEnabled(): List<Tag>
}
