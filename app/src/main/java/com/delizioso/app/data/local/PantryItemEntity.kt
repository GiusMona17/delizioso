package com.delizioso.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An ingredient or staple item stored in the user's pantry/refrigerator.
 */
@Entity(
    tableName = "pantry_items",
    indices = [
        Index("name"),
        Index("inStock"),
    ]
)
data class PantryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "Pantry",
    val quantity: String? = null,
    val expiresAtEpochDay: Long? = null,
    val inStock: Boolean = true,
    val addedAtEpochMilli: Long = System.currentTimeMillis(),
)
