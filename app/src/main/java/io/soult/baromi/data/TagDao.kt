package io.soult.baromi.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface TagDao {
    @Insert
    suspend fun insert(tag: Tag): Long

    @Update
    suspend fun update(tag: Tag)

    @Delete
    suspend fun delete(tag: Tag)

    @Query("SELECT * FROM tag ORDER BY label")
    suspend fun getAll(): List<Tag>

    @Query("SELECT * FROM tag WHERE label = :label")
    suspend fun getByLabel(label: String): Tag?
}
