package io.soult.baromi.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TagDao {
    /** IGNORE on the unique label index: a duplicate label is a no-op returning -1. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: Tag): Long

    @Delete
    suspend fun delete(tag: Tag)

    @Query("SELECT * FROM tag ORDER BY label")
    suspend fun getAll(): List<Tag>
}
