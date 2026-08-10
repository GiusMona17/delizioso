package com.delizioso.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY updatedAt DESC")
    fun observeAllWithDetails(): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeWithDetails(id: Long): Flow<RecipeWithDetails?>

    @Query("SELECT * FROM recipes WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun observeFavorites(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun search(query: String): Flow<List<RecipeEntity>>

    @Query("SELECT COUNT(*) FROM recipes")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Insert
    suspend fun insertIngredients(items: List<IngredientEntity>)

    @Insert
    suspend fun insertSteps(items: List<StepEntity>)

    @Insert
    suspend fun insertSource(source: SourceEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<RecipeTagCrossRef>)

    /**
     * Persist a full recipe atomically: recipe row + ingredients + steps + source + tags.
     * Returns the new recipe id.
     */
    @Transaction
    suspend fun insertWithDetails(details: RecipeWithDetails, tagNames: List<String> = emptyList()): Long {
        val recipeId = insertRecipe(details.recipe)
        if (details.ingredients.isNotEmpty()) {
            insertIngredients(details.ingredients.map { it.copy(recipeId = recipeId) })
        }
        if (details.steps.isNotEmpty()) {
            insertSteps(details.steps.map { it.copy(recipeId = recipeId) })
        }
        details.source?.let { insertSource(it.copy(recipeId = recipeId)) }
        if (tagNames.isNotEmpty()) {
            insertTags(tagNames.map { TagEntity(name = it.trim()) })
            insertCrossRefs(tagNames.map { RecipeTagCrossRef(recipeId = recipeId, tagName = it.trim()) })
        }
        return recipeId
    }

    @Query("UPDATE recipes SET isFavorite = :favorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query(
        "UPDATE recipes SET macrosKcal = :kcal, macrosProteinG = :proteinG, " +
            "macrosFatG = :fatG, macrosCarbsG = :carbsG, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateMacros(
        id: Long,
        kcal: Float?,
        proteinG: Float?,
        fatG: Float?,
        carbsG: Float?,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE recipes SET imageUri = :imageUri, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateImage(id: Long, imageUri: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT imageUri FROM recipes WHERE id = :id")
    suspend fun imageUriOf(id: Long): String?

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteRecipe(id: Long)

    // ---- Meal planning ----

    @Transaction
    @Query("SELECT * FROM planned_meals WHERE dateEpochDay BETWEEN :from AND :to ORDER BY dateEpochDay, id")
    fun observeMealsWithRecipe(from: Long, to: Long): Flow<List<PlannedMealWithRecipe>>

    @Insert
    suspend fun insertMeal(meal: PlannedMealEntity): Long

    @Query("DELETE FROM planned_meals WHERE id = :id")
    suspend fun deleteMeal(id: Long)

    /** Cook mode finished — flag every meal planned for that recipe on that day. */
    @Query("UPDATE planned_meals SET cooked = 1 WHERE recipeId = :recipeId AND dateEpochDay = :dateEpochDay")
    suspend fun markMealsCooked(recipeId: Long, dateEpochDay: Long)
}
