package com.delizioso.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.delizioso.app.R

/** A meal planned on a specific day/slot. */
@Entity(
    tableName = "planned_meals",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("recipeId")],
)
data class PlannedMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    /** Days since epoch (java.time.LocalDate.toEpochDay). */
    val dateEpochDay: Long,
    /** BREAKFAST | LUNCH | DINNER | SNACK. */
    val slot: String,
    val servings: Int = 2,
    /** Set once the user finishes cook mode for this meal (drives the planner's "cooked" dot). */
    val cooked: Boolean = false,
    /** True if this is an attached side dish, sauce, or bread for the slot. */
    val isSide: Boolean = false,
)

object MealSlot {
    const val BREAKFAST = "BREAKFAST"
    const val LUNCH = "LUNCH"
    const val DINNER = "DINNER"
    const val SNACK = "SNACK"
    val all = listOf(BREAKFAST, LUNCH, DINNER, SNACK)

    fun label(slot: String): String = when (slot) {
        BREAKFAST -> "Breakfast"
        LUNCH -> "Lunch"
        DINNER -> "Dinner"
        SNACK -> "Snacks"
        else -> slot.lowercase().replaceFirstChar { it.uppercase() }
    }

    /** String resource for the display label of [slot] (localized at the UI boundary). */
    fun labelRes(slot: String): Int = when (slot) {
        BREAKFAST -> R.string.data_breakfast
        LUNCH -> R.string.data_lunch
        DINNER -> R.string.data_dinner
        SNACK -> R.string.data_snacks
        else -> R.string.data_snacks
    }
}

/** Planned meal with its recipe loaded. */
data class PlannedMealWithRecipe(
    @Embedded val meal: PlannedMealEntity,
    @Relation(parentColumn = "recipeId", entityColumn = "id")
    val recipe: RecipeEntity? = null,
)
