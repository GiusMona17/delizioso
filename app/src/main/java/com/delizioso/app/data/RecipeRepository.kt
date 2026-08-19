package com.delizioso.app.data

import com.delizioso.app.data.local.PlannedMealEntity
import com.delizioso.app.data.local.PlannedMealWithRecipe
import com.delizioso.app.data.local.RecipeDao
import com.delizioso.app.data.local.RecipeEntity
import com.delizioso.app.data.import.StructuredRecipe
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.data.local.StepEntity
import kotlinx.coroutines.flow.Flow

/** Single entry point for recipe persistence. */
class RecipeRepository(private val dao: RecipeDao) {

    val allWithDetails: Flow<List<RecipeWithDetails>> = dao.observeAllWithDetails()

    fun byId(id: Long): Flow<RecipeWithDetails?> = dao.observeWithDetails(id)

    fun favorites(): Flow<List<RecipeEntity>> = dao.observeFavorites()

    fun count(): Flow<Int> = dao.observeCount()

    suspend fun save(details: RecipeWithDetails, tagNames: List<String> = emptyList()): Long =
        dao.insertWithDetails(details, tagNames)

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    /**
     * Save an edit, keeping the fields the form doesn't own (photo, favourite,
     * createdAt) as they were.
     */
    suspend fun update(
        id: Long,
        recipe: StructuredRecipe,
        categories: List<String>,
    ) {
        val existing = dao.recipeById(id) ?: return
        dao.updateWithDetails(
            recipe = existing.copy(
                title = recipe.title.orEmpty().ifBlank { existing.title },
                description = recipe.description,
                servings = recipe.servings,
                prepTimeMinutes = recipe.prepTimeMinutes,
                cookTimeMinutes = recipe.cookTimeMinutes,
                updatedAt = System.currentTimeMillis(),
            ),
            ingredients = recipe.ingredients.mapIndexed { i, ing -> ing.copy(position = i) },
            steps = recipe.steps.mapIndexed { i, text -> StepEntity(recipeId = id, position = i + 1, text = text) },
            tagNames = categories,
        )
    }

    suspend fun updateSourceText(id: Long, rawText: String?) = dao.updateSourceText(id, rawText)

    suspend fun currentImage(id: Long): String? = dao.imageUriOf(id)

    suspend fun setImage(id: Long, imageUri: String?) = dao.updateImage(id, imageUri)

    // ---- Meal planning ----

    fun mealsBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<PlannedMealWithRecipe>> =
        dao.observeMealsWithRecipe(fromEpochDay, toEpochDay)

    suspend fun addMeal(meal: PlannedMealEntity): Long = dao.insertMeal(meal)

    suspend fun removeMeal(id: Long) = dao.deleteMeal(id)

    suspend fun markMealsCooked(recipeId: Long, dateEpochDay: Long) = dao.markMealsCooked(recipeId, dateEpochDay)

    suspend fun delete(id: Long) = dao.deleteRecipe(id)
}
