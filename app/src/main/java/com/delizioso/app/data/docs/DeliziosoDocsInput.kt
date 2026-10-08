package com.delizioso.app.data.docs

import com.delizioso.app.data.local.PantryItemEntity
import java.time.Instant
import java.time.LocalDate

/** A planned meal with its title already resolved (recipe title or the hand-typed name). */
data class MealInput(
    val date: LocalDate,
    /** BREAKFAST | LUNCH | DINNER | SNACK, as stored. */
    val slot: String,
    val title: String,
    val servings: Int,
    val kcal: Int?,
    val recipeId: Long?,
    val cooked: Boolean,
    val isSide: Boolean,
)

/** One line of the open shopping list: not ticked, not in the pantry. */
data class ShoppingLine(
    val line: String,
    val category: String,
)

data class DeliziosoDocsInput(
    val today: LocalDate,
    val generatedAt: Instant,
    /** IANA name, e.g. "Europe/Rome". */
    val timezone: String,
    val meals: List<MealInput>,
    val shopping: List<ShoppingLine>,
    val pantry: List<PantryItemEntity>,
)
