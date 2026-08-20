package com.delizioso.app.data.search

import com.delizioso.app.data.import.RecipeSource

class TheMealDbSearchProvider(
    private val client: TheMealDbClient
) : RecipeSearchProvider {

    override val source: RecipeSource = RecipeSource.THE_MEAL_DB

    override suspend fun searchByName(query: String): List<OnlineSearchResult> {
        val meals = client.searchByName(query)
        return meals.mapNotNull { meal ->
            val id = MealDbMapper.mealId(meal) ?: return@mapNotNull null
            val recipe = MealDbMapper.toRecipe(meal)
            OnlineSearchResult(
                id = id,
                title = recipe.title.orEmpty(),
                thumbnailUrl = recipe.imageUrl,
                source = RecipeSource.THE_MEAL_DB
            )
        }
    }

    override suspend fun searchByIngredient(ingredient: String): List<OnlineSearchResult> {
        return client.mealsWithIngredient(ingredient).map {
            OnlineSearchResult(
                id = it.id,
                title = it.title,
                thumbnailUrl = it.thumbnailUrl,
                source = RecipeSource.THE_MEAL_DB
            )
        }
    }
}
