package com.delizioso.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A reusable quick/simple meal entry in the user's catalog. */
@Entity(
    tableName = "quick_meals",
    indices = [
        Index("name"),
        Index("defaultSlot"),
    ],
)
data class QuickMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** BREAKFAST | LUNCH | DINNER | SNACK | null (any). */
    val defaultSlot: String? = null,
    val caloriesKcal: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val carbsG: Double? = null,
    val createdAtEpochMilli: Long = System.currentTimeMillis(),
)
