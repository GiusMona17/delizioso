package com.delizioso.app.data.docs

import com.delizioso.app.data.GroceryAggregator
import com.delizioso.app.data.GroceryCategories
import com.delizioso.app.data.GroceryItem
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.local.CustomGroceryLine
import com.delizioso.app.data.pantry.PantryMatcher
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/** Where the documents' input comes from; a fake in tests. */
fun interface DeliziosoDocsSource {
    suspend fun load(): DeliziosoDocsInput
}

/**
 * Reads the plan, the pantry and the grocery state into the builder's input. The open shopping list repeats what
 * GroceryViewModel computes for its default 7 days (planned recipes aggregated, plus the owner's own lines), then drops
 * what is ticked and what the pantry already has.
 */
class DeliziosoDocsLoader(
    private val repository: RecipeRepository,
    private val checked: suspend () -> Set<String>,
    private val custom: suspend () -> List<CustomGroceryLine>,
) : DeliziosoDocsSource {

    override suspend fun load(): DeliziosoDocsInput = load(Instant.now(), ZoneId.systemDefault())

    suspend fun load(now: Instant, zone: ZoneId): DeliziosoDocsInput {
        val today = LocalDate.ofInstant(now, zone)
        val planned = repository.mealsBetween(today.toEpochDay(), today.plusDays(DeliziosoAgenda.MEALS_AHEAD).toEpochDay()).first()

        val meals = planned.map { m ->
            MealInput(
                date = LocalDate.ofEpochDay(m.meal.dateEpochDay),
                slot = m.meal.slot,
                title = m.recipe?.title ?: m.meal.customName ?: "Meal",
                servings = m.meal.servings,
                kcal = (m.meal.caloriesKcal ?: m.recipe?.caloriesKcal)?.roundToInt(),
                recipeId = m.meal.recipeId,
                cooked = m.meal.cooked,
                isSide = m.meal.isSide,
            )
        }
        return DeliziosoDocsInput(
            today = today,
            generatedAt = now,
            timezone = zone.id,
            meals = meals,
            shopping = shoppingList(today),
            pantry = repository.pantryItems.first(),
        )
    }

    private suspend fun shoppingList(today: LocalDate): List<ShoppingLine> {
        val week = repository.mealsBetween(today.toEpochDay(), today.plusDays(SHOPPING_DAYS - 1).toEpochDay()).first()
        val plannedIds = week.map { it.meal.recipeId }.toSet()
        val recipes = repository.allWithDetails.first().filter { it.recipe.id in plannedIds }
        val own = custom().sortedBy { it.line }.map {
            GroceryItem(
                name = it.line,
                line = it.line,
                isMerged = false,
                category = GroceryCategories.of(it.line),
            )
        }
        val inStock = repository.inStockPantryItems.first()
        val ticked = checked()
        return (GroceryAggregator.aggregate(recipes) + own)
            .filter { it.line !in ticked && !PantryMatcher.isIngredientAvailable(it.name, inStock) }
            .map { ShoppingLine(it.line, it.category) }
    }

    private companion object {
        const val SHOPPING_DAYS = 7L
    }
}
