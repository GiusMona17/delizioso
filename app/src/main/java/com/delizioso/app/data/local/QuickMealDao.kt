package com.delizioso.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickMealDao {
    @Query("SELECT * FROM quick_meals ORDER BY name ASC")
    fun getAll(): Flow<List<QuickMealEntity>>

    @Query("SELECT * FROM quick_meals WHERE defaultSlot = :slot OR defaultSlot IS NULL ORDER BY name ASC")
    fun getForSlot(slot: String): Flow<List<QuickMealEntity>>

    @Query("SELECT * FROM quick_meals WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): QuickMealEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meal: QuickMealEntity): Long

    @Update
    suspend fun update(meal: QuickMealEntity)

    @Query("DELETE FROM quick_meals WHERE id = :id")
    suspend fun delete(id: Long)
}
