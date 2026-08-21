package com.delizioso.app.data

import com.delizioso.app.data.import.StructuredRecipe
import com.delizioso.app.data.local.IngredientEntity
import com.delizioso.app.data.local.RecipeWithDetails

/**
 * Helpers to detect if recipe ingredients cannot scale when changing servings.
 */
fun IngredientEntity.isFixed(): Boolean {
    if (!quantity.isNullOrBlank()) return false
    val u = unit?.lowercase()?.trim() ?: ""
    val n = name.lowercase().trim()
    val r = (rawText ?: "").lowercase().trim()

    // "q.b.", "quanto basta", "to taste", "a piacere" are qualitative by nature and not considered "fixed" errors
    val isQb = u.contains("q.b") || u.contains("qb") ||
        n.contains("q.b") || n.contains("quanto basta") || n.contains("a piacere") || n.contains("to taste") ||
        r.contains("q.b") || r.contains("quanto basta") || r.contains("a piacere") || r.contains("to taste")

    return !isQb
}

fun RecipeWithDetails.hasFixedIngredients(): Boolean {
    if (recipe.servings == null || recipe.servings <= 0) return true
    return ingredients.any { it.isFixed() }
}

fun StructuredRecipe.hasFixedIngredients(): Boolean {
    if (servings == null || servings <= 0) return true
    return ingredients.any { it.isFixed() }
}
