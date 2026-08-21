package com.delizioso.app.data

import com.delizioso.app.data.local.IngredientEntity
import com.delizioso.app.data.local.RecipeEntity
import com.delizioso.app.data.local.RecipeWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroceryAggregatorTest {

    @Test
    fun `aggregate converts and sums compatible weight units`() {
        val recipe1 = RecipeWithDetails(
            recipe = RecipeEntity(id = 1L, title = "Dish A"),
            ingredients = listOf(
                IngredientEntity(recipeId = 1L, position = 0, name = "Farina 00", quantity = "500", unit = "g"),
            ),
        )
        val recipe2 = RecipeWithDetails(
            recipe = RecipeEntity(id = 2L, title = "Dish B"),
            ingredients = listOf(
                IngredientEntity(recipeId = 2L, position = 0, name = "Farina 00", quantity = "1", unit = "kg"),
            ),
        )

        val aggregated = GroceryAggregator.aggregate(listOf(recipe1, recipe2))

        assertEquals(1, aggregated.size)
        val item = aggregated.first()
        assertEquals("1.5 kg Farina 00", item.line)
        assertTrue(item.isMerged)
        assertEquals(listOf("Dish A", "Dish B"), item.recipeTitles)
    }

    @Test
    fun `aggregate sums item counts with matching count units`() {
        val recipe1 = RecipeWithDetails(
            recipe = RecipeEntity(id = 1L, title = "Carbonara"),
            ingredients = listOf(
                IngredientEntity(recipeId = 1L, position = 0, name = "Uova fresche", quantity = "3", unit = "uova"),
            ),
        )
        val recipe2 = RecipeWithDetails(
            recipe = RecipeEntity(id = 2L, title = "Tiramisu"),
            ingredients = listOf(
                IngredientEntity(recipeId = 2L, position = 0, name = "Uova", quantity = "4", unit = "uova"),
            ),
        )

        val aggregated = GroceryAggregator.aggregate(listOf(recipe1, recipe2))

        assertEquals(1, aggregated.size)
        val item = aggregated.first()
        assertEquals("7 uova Uova fresche", item.line)
        assertTrue(item.isMerged)
    }
}
