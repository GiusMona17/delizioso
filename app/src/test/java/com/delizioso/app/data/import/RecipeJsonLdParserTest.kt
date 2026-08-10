package com.delizioso.app.data.import

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeJsonLdParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** BBC-style: Recipe node nested inside an application/json hydration block. */
    private val bbcStyle = """
        <html><head>
        <script type="application/ld+json">{"@context":"https://schema.org","@type":"BreadcrumbList","itemListElement":[]}</script>
        </head><body>
        <script type="application/json">{"props":{"page":{"schema":{"@context":"https://schema.org","@id":"...#Recipe","@type":"Recipe","name":"Spaghetti Puttanesca","description":"Cook up this classic sauce in one pan.","recipeYield":"4 servings","prepTime":"PT15M","cookTime":"PT20M","recipeCategory":"Dinner, Pasta","recipeIngredient":["3 tbsp olive oil","1 onion, finely chopped","400g can chopped tomatoes","2 large garlic cloves, crushed"],"recipeInstructions":[{"@type":"HowToStep","text":"Heat the oil in a large pan."},{"@type":"HowToStep","text":"Add the onion and cook for 5 mins."}]}}}}</script>
        </body></html>
    """

    private val classicLdJson = """
        <html><body>
        <script type="application/ld+json">
        {"@context":"https://schema.org","@type":"Recipe","name":"Avocado Toast","recipeYield":"2","totalTime":"PT10M","recipeIngredient":["2 slices sourdough","1 ripe avocado"],"recipeInstructions":"Toast the bread. Mash the avocado and spread."}
        </script>
        </body></html>
    """

    @Test
    fun `parses recipe nested in application-json hydration block`() {
        val recipe = RecipeJsonLdParser.parse(bbcStyle, json)
        assertNotNull(recipe)
        assertEquals("Spaghetti Puttanesca", recipe!!.title)
        assertEquals(4, recipe.servings)
        assertEquals(15, recipe.prepTimeMinutes)
        assertEquals(20, recipe.cookTimeMinutes)
        assertEquals(4, recipe.ingredients.size)
        assertEquals("3 tbsp olive oil", recipe.ingredients.first().rawText)
        assertEquals("3", recipe.ingredients.first().quantity)
        assertEquals(2, recipe.steps.size)
        assertEquals("Heat the oil in a large pan.", recipe.steps.first())
    }

    @Test
    fun `parses classic ld-json with string instructions`() {
        val recipe = RecipeJsonLdParser.parse(classicLdJson, json)
        assertNotNull(recipe)
        assertEquals("Avocado Toast", recipe!!.title)
        assertEquals(2, recipe.servings)
        assertEquals(10, recipe.prepTimeMinutes)
        // A plain-string instruction stays as one step (sentence splitting is LLM work).
        assertEquals(1, recipe.steps.size)
        assertEquals("Toast the bread. Mash the avocado and spread.", recipe.steps.single())
    }

    @Test
    fun `returns null when no recipe node`() {
        assertNull(RecipeJsonLdParser.parse("<html><script type=\"application/ld+json\">{\"@type\":\"Article\"}</script></html>", json))
    }
}
