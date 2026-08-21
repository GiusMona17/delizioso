package com.delizioso.app.data.pantry

import com.delizioso.app.data.local.IngredientEntity
import com.delizioso.app.data.local.PantryItemEntity
import com.delizioso.app.data.local.RecipeEntity
import com.delizioso.app.data.local.RecipeWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryMatcherTest {

    @Test
    fun `normalize strips descriptor words and normalizes plurals`() {
        assertEquals("uovo", PantryMatcher.normalize("uova fresche"))
        assertEquals("pomodoro", PantryMatcher.normalize("pomodori pelati"))
        assertEquals("cipolla", PantryMatcher.normalize("cipolle dorate"))
        assertEquals("olio oliva", PantryMatcher.normalize("olio extravergine di oliva"))
        assertEquals("carrot", PantryMatcher.normalize("fresh diced carrots"))
    }

    @Test
    fun `isIngredientAvailable matches pantry items with synonyms`() {
        val pantry = listOf(
            PantryItemEntity(name = "Uova", inStock = true),
            PantryItemEntity(name = "Guanciale", inStock = true),
            PantryItemEntity(name = "Pecorino Romano", inStock = true),
            PantryItemEntity(name = "Latte", inStock = false), // out of stock
        )

        assertTrue(PantryMatcher.isIngredientAvailable("uova medie", pantry))
        assertTrue(PantryMatcher.isIngredientAvailable("guanciale a cubetti", pantry))
        assertTrue(PantryMatcher.isIngredientAvailable("pecorino grattugiato", pantry))
        assertFalse(PantryMatcher.isIngredientAvailable("latte intero", pantry))
        assertFalse(PantryMatcher.isIngredientAvailable("farina 00", pantry))
    }

    @Test
    fun `match computes correct match percentage and missing items`() {
        val recipe = RecipeWithDetails(
            recipe = RecipeEntity(id = 1L, title = "Spaghetti Carbonara"),
            ingredients = listOf(
                IngredientEntity(recipeId = 1L, position = 0, name = "Spaghetti"),
                IngredientEntity(recipeId = 1L, position = 1, name = "Guanciale"),
                IngredientEntity(recipeId = 1L, position = 2, name = "Uova fresche"),
                IngredientEntity(recipeId = 1L, position = 3, name = "Pecorino Romano"),
            ),
        )

        val pantry = listOf(
            PantryItemEntity(name = "Spaghetti", inStock = true),
            PantryItemEntity(name = "Guanciale", inStock = true),
            PantryItemEntity(name = "Uova", inStock = true),
        )

        val match = PantryMatcher.match(recipe, pantry)

        assertEquals(75, match.matchPercentage)
        assertEquals(3, match.matchedIngredients.size)
        assertEquals(listOf("Pecorino Romano"), match.missingIngredients)
        assertFalse(match.isReadyToCook)
    }

    @Test
    fun `rank sorts recipes by match percentage and ready to cook first`() {
        val carbonara = RecipeWithDetails(
            recipe = RecipeEntity(id = 1L, title = "Carbonara"),
            ingredients = listOf(
                IngredientEntity(recipeId = 1L, position = 0, name = "Uova"),
                IngredientEntity(recipeId = 1L, position = 1, name = "Pasta"),
            ),
        )
        val tiramisu = RecipeWithDetails(
            recipe = RecipeEntity(id = 2L, title = "Tiramisu"),
            ingredients = listOf(
                IngredientEntity(recipeId = 2L, position = 0, name = "Savoiardi"),
                IngredientEntity(recipeId = 2L, position = 1, name = "Mascarpone"),
                IngredientEntity(recipeId = 2L, position = 2, name = "Caffè"),
            ),
        )

        val pantry = listOf(
            PantryItemEntity(name = "Uova", inStock = true),
            PantryItemEntity(name = "Pasta", inStock = true),
        )

        val ranked = PantryMatcher.rank(listOf(tiramisu, carbonara), pantry)

        assertEquals("Carbonara", ranked.first().details.recipe.title)
        assertEquals(100, ranked.first().matchPercentage)
        assertTrue(ranked.first().isReadyToCook)
    }
}
