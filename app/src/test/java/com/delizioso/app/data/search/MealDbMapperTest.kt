package com.delizioso.app.data.search

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealDbMapperTest {

    private fun meal(body: String): JsonObject =
        Json.parseToJsonElement(body) as JsonObject

    /** Shape captured live from search.php?s=arrabiata on 2026-08-19. */
    private val arrabiata = meal(
        """
        {
          "idMeal": "52771",
          "strMeal": "Spicy Arrabiata Penne",
          "strCategory": "Vegetarian",
          "strArea": "Italian",
          "strInstructions": "Bring a large pot of water to a boil.\r\nAdd the penne.\r\n\r\n3. Serve immediately.",
          "strMealThumb": "https://www.themealdb.com/images/media/meals/x.jpg",
          "strSource": null,
          "strIngredient1": "penne rigate", "strMeasure1": "1 pound",
          "strIngredient2": "olive oil",    "strMeasure2": "1/4 cup",
          "strIngredient3": "",             "strMeasure3": "",
          "strIngredient4": null,           "strMeasure4": null,
          "strIngredient5": "parsley",      "strMeasure5": " ",
          "strIngredient20": "",            "strMeasure20": ""
        }
        """.trimIndent()
    )

    @Test
    fun `maps the fields the API actually has`() {
        val recipe = MealDbMapper.toRecipe(arrabiata)
        assertEquals("Spicy Arrabiata Penne", recipe.title)
        assertEquals("https://www.themealdb.com/images/media/meals/x.jpg", recipe.imageUrl)
        assertEquals("52771", MealDbMapper.mealId(arrabiata))
    }

    /** Empty slots arrive as "", " " or null anywhere in the 1..20 range. */
    @Test
    fun `skips empty ingredient slots without leaving blanks`() {
        val recipe = MealDbMapper.toRecipe(arrabiata)
        assertEquals(
            listOf("1 pound penne rigate", "1/4 cup olive oil", "parsley"),
            recipe.ingredients.map { it.rawText },
        )
        assertEquals(listOf(0, 1, 2), recipe.ingredients.map { it.position })
    }

    @Test
    fun `splits instructions into steps and drops their numbering`() {
        val recipe = MealDbMapper.toRecipe(arrabiata)
        assertEquals(
            listOf("Bring a large pot of water to a boil.", "Add the penne.", "Serve immediately."),
            recipe.steps,
        )
    }

    /** strCategory and strArea go through the fixed vocabulary; unknowns drop. */
    @Test
    fun `categories are canonicalised and Italian is not one of ours`() {
        assertEquals(listOf("Vegetarian"), MealDbMapper.toRecipe(arrabiata).categories)
    }

    /** The API has no servings or timings. Inventing them would be worse than null. */
    @Test
    fun `servings and times stay null`() {
        val recipe = MealDbMapper.toRecipe(arrabiata)
        assertNull(recipe.servings)
        assertNull(recipe.prepTimeMinutes)
        assertNull(recipe.cookTimeMinutes)
    }

    @Test
    fun `a meal with nothing usable maps to an empty recipe rather than throwing`() {
        val recipe = MealDbMapper.toRecipe(meal("""{"idMeal":"1","strMeal":"X"}"""))
        assertEquals("X", recipe.title)
        assertTrue(recipe.ingredients.isEmpty())
        assertTrue(recipe.steps.isEmpty())
    }
}
