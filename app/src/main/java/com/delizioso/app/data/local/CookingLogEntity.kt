package com.delizioso.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A record of preparing a recipe on a specific date, with notes, rating, and attached photo.
 */
@Entity(
    tableName = "cooking_logs",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("recipeId"),
        Index("dateEpochDay"),
    ],
)
data class CookingLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    /** Days since epoch (java.time.LocalDate.toEpochDay). */
    val dateEpochDay: Long,
    val notes: String,
    /** Local file path or URI for the dish photo. */
    val photoUri: String? = null,
    /** Optional 1-5 star rating. */
    val rating: Int? = null,
    val createdAtEpochMilli: Long = System.currentTimeMillis(),
)
