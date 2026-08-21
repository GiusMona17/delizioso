package com.delizioso.app.data

import com.delizioso.app.data.import.NutritionInfo
import com.delizioso.app.data.import.StructuredRecipe
import com.delizioso.app.data.local.RecipeWithDetails

/**
 * A saved recipe as the plain [StructuredRecipe] the AI layer and the edit form
 * both speak. Keeps the persistence shape out of those two.
 */
fun RecipeWithDetails.toStructuredRecipe(): StructuredRecipe = StructuredRecipe(
    title = recipe.title,
    description = recipe.description,
    servings = recipe.servings,
    prepTimeMinutes = recipe.prepTimeMinutes,
    cookTimeMinutes = recipe.cookTimeMinutes,
    imageUrl = recipe.imageUri,
    ingredients = ingredients.sortedBy { it.position },
    steps = steps.sortedBy { it.position }.map { it.text },
    categories = tags.map { it.name },
    nutrition = if (recipe.caloriesKcal != null || recipe.proteinG != null || recipe.fatG != null || recipe.carbsG != null) {
        NutritionInfo(
            caloriesKcal = recipe.caloriesKcal,
            proteinG = recipe.proteinG,
            fatG = recipe.fatG,
            carbsG = recipe.carbsG,
        )
    } else null,
)
