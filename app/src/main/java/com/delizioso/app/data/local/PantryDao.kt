package com.delizioso.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PantryDao {

    @Query("SELECT * FROM pantry_items ORDER BY inStock DESC, name ASC")
    fun getAll(): Flow<List<PantryItemEntity>>

    @Query("SELECT * FROM pantry_items WHERE inStock = 1 ORDER BY name ASC")
    fun getInStock(): Flow<List<PantryItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PantryItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PantryItemEntity>)

    @Update
    suspend fun update(item: PantryItemEntity)

    @Query("UPDATE pantry_items SET inStock = :inStock WHERE id = :id")
    suspend fun setInStock(id: Long, inStock: Boolean)

    @Query("DELETE FROM pantry_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pantry_items WHERE inStock = 0")
    suspend fun clearOutOfStock()
}
