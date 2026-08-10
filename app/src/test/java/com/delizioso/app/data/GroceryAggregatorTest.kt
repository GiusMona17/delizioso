package com.delizioso.app.data

import com.delizioso.app.data.local.IngredientEntity
import com.delizioso.app.data.local.RecipeEntity
import com.delizioso.app.data.local.RecipeWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroceryAggregatorTest {

    private fun recipe(id: Long, title: String, ingredients: List<IngredientEntity>) =
        RecipeWithDetails(
            recipe = RecipeEntity(id = id, title = title),
            ingredients = ingredients,
        )

    private fun ing(recipeId: Long, pos: Int, qty: String?, unit: String?, name: String) =
        IngredientEntity(
            recipeId = recipeId,
            position = pos,
            quantity = qty,
            unit = unit,
            name = name,
            rawText = listOfNotNull(qty, unit, name).joinToString(" "),
        )

    @Test
    fun `merges numeric quantities across recipes`() {
        val recipes = listOf(
            recipe(
                1, "Pasta",
                listOf(
                    ing(1, 0, "2", "cups", "flour"),
                    ing(1, 1, "1/2", "tsp", "salt"),
                ),
            ),
            recipe(
                2, "Bread",
                listOf(
                    ing(2, 0, "1", "cup", "flour"),
                    ing(2, 1, "1", "tsp", "salt"),
                ),
            ),
        )
        val items = GroceryAggregator.aggregate(recipes)
        assertEquals(2, items.size)
        val flour = items.first { it.name == "flour" }
        assertEquals("3 cups flour", flour.line)
        assertTrue(flour.isMerged)
        val salt = items.first { it.name == "salt" }
        assertEquals("1.5 tsp salt", salt.line)
        assertTrue(salt.isMerged)
    }

    @Test
    fun `non numeric lines are kept as-is`() {
        val recipes = listOf(
            recipe(
                1, "Soup",
                listOf(IngredientEntity(recipeId = 1, position = 0, name = "love", rawText = "a pinch of love")),
            )
        )
        val items = GroceryAggregator.aggregate(recipes)
        assertEquals(1, items.size)
        assertEquals("a pinch of love", items.first().line)
        assertFalse(items.first().isMerged)
    }

    @Test
    fun `empty recipes produce empty list`() {
        assertEquals(emptyList<GroceryItem>(), GroceryAggregator.aggregate(emptyList()))
    }
}
