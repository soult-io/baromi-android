package io.soult.baromi.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT * FROM category ORDER BY position, id")
    suspend fun getAll(): List<Category>

    @Query("SELECT * FROM category WHERE enabled = 1 ORDER BY position, id")
    suspend fun getEnabled(): List<Category>

    @Query("SELECT COUNT(*) FROM category")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM category WHERE builtin = 0")
    suspend fun customCount(): Int

    @Query("SELECT MAX(position) FROM category")
    suspend fun maxPosition(): Int?
}
