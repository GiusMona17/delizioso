package com.delizioso.app.data.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeJsonImporterTest {

    private val good = """
        {
          "title": "Pasta al pomodoro",
          "description": "Veloce",
          "servings": 2,
          "prepTimeMinutes": 5,
          "cookTimeMinutes": 15,
          "tags": ["Dinner", "Pasta"],
          "ingredients": [
            {"quantity": "200", "unit": "g", "name": "spaghetti"},
            {"quantity": "300", "unit": "g", "name": "passata"},
            {"quantity": null, "unit": null, "name": "sale"}
          ],
          "steps": ["Cuoci la pasta.", "Scalda la passata."]
        }
    """.trimIndent()

    @Test
    fun `reads the requested shape`() {
        val recipe = RecipeJsonImporter.parse(good)!!
        assertEquals("Pasta al pomodoro", recipe.title)
        assertEquals(2, recipe.servings)
        assertEquals(15, recipe.cookTimeMinutes)
        assertEquals(3, recipe.ingredients.size)
        assertEquals("200", recipe.ingredients[0].quantity)
        assertEquals("g", recipe.ingredients[0].unit)
        assertEquals("spaghetti", recipe.ingredients[0].name)
        assertEquals("200 g spaghetti", recipe.ingredients[0].rawText)
        // A null quantity must not become the string "null".
        assertNull(recipe.ingredients[2].quantity)
        assertEquals(listOf("Cuoci la pasta.", "Scalda la passata."), recipe.steps)
        assertEquals(listOf("Dinner", "Pasta"), recipe.categories)
    }

    /** Assistants fence their answers and chat before them; neither is an error. */
    @Test
    fun `survives markdown fences and surrounding prose`() {
        val messy = "Certo! Ecco la ricetta:\n\n```json\n$good\n```\n\nSpero sia utile!"
        val recipe = RecipeJsonImporter.parse(messy)!!
        assertEquals("Pasta al pomodoro", recipe.title)
        assertEquals(3, recipe.ingredients.size)
    }

    /** Some answers ignore the object shape and hand back plain strings. */
    @Test
    fun `accepts ingredients written as plain strings`() {
        val loose = """{"title":"Torta","ingredients":["200 g farina","2 uova"],"steps":["Mescola."]}"""
        val recipe = RecipeJsonImporter.parse(loose)!!
        assertEquals(listOf("farina", "uova"), recipe.ingredients.map { it.name })
        assertEquals("200", recipe.ingredients[0].quantity)
    }

    @Test
    fun `rejects json that holds no recipe`() {
        assertNull(RecipeJsonImporter.parse("""{"title":"Vuota","ingredients":[],"steps":[]}"""))
        assertNull(RecipeJsonImporter.parse("not json at all"))
        assertNull(RecipeJsonImporter.parse(""))
    }

    @Test
    fun `recognises its own format before parsing it`() {
        assertTrue(RecipeJsonImporter.looksLikeJson(good))
        assertTrue(RecipeJsonImporter.looksLikeJson("```json\n$good\n```"))
        assertTrue(!RecipeJsonImporter.looksLikeJson("Ingredienti\n200 g farina"))
    }

    /** The paste field is one door: JSON and captions both come through it. */
    @Test
    fun `the paste flow routes json to this importer`() {
        val recipe = PastedRecipeParser.parse(good)
        assertEquals("Pasta al pomodoro", recipe.title)
        assertEquals(3, recipe.ingredients.size)
    }
}
