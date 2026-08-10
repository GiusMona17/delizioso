package com.delizioso.app.data

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
)
