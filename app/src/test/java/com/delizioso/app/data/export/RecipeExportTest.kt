package com.delizioso.app.data.export

import com.delizioso.app.data.import.IngredientParser
import com.delizioso.app.data.local.RecipeEntity
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.data.local.SourceEntity
import com.delizioso.app.data.local.StepEntity
import com.delizioso.app.data.local.TagEntity
import com.delizioso.app.data.nutrition.MacroCalculator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeExportTest {

    private val details = RecipeWithDetails(
        recipe = RecipeEntity(id = 1, title = "Carbonara", description = "Romana e veloce", servings = 2, prepTimeMinutes = 10),
        ingredients = listOf("200 g spaghetti", "100 g guanciale", "2 uova")
            .mapIndexed { i, line -> IngredientParser.split(line).copy(recipeId = 1, position = i) },
        steps = listOf("Rosola il guanciale.", "Manteca fuori dal fuoco.")
            .mapIndexed { i, text -> StepEntity(recipeId = 1, position = i + 1, text = text) },
        source = SourceEntity(recipeId = 1, platform = "BLOG", url = "https://example.com/carbonara"),
        tags = listOf(TagEntity("Dinner")),
    )

    @Test
    fun `markdown carries every part of the recipe`() {
        val md = RecipeExport.toMarkdown(details, MacroCalculator.of(details.ingredients, 2))
        assertTrue(md.startsWith("# Carbonara"))
        assertTrue(md.contains("Servings: 2"))
        assertTrue(md.contains("## Ingredients"))
        assertTrue(md.contains("- 200 g spaghetti"))
        assertTrue(md.contains("## Instructions"))
        assertTrue(md.contains("1. Rosola il guanciale."))
        assertTrue(md.contains("2. Manteca fuori dal fuoco."))
        assertTrue(md.contains("## Nutrition (calculated, per serving)"))
        assertTrue(md.contains("Source: https://example.com/carbonara"))
    }

    @Test
    fun `steps and ingredients keep their order regardless of row order`() {
        val shuffled = details.copy(
            ingredients = details.ingredients.reversed(),
            steps = details.steps.reversed(),
        )
        val md = RecipeExport.toMarkdown(shuffled)
        assertTrue(md.indexOf("200 g spaghetti") < md.indexOf("2 uova"))
        assertTrue(md.indexOf("1. Rosola") < md.indexOf("2. Manteca"))
    }

    @Test
    fun `json is valid and structured`() {
        val obj = Json.parseToJsonElement(RecipeExport.toJson(details)) as JsonObject
        assertEquals("Carbonara", (obj["title"] as JsonPrimitive).content)
        assertEquals(3, (obj["ingredients"] as JsonArray).size)
        assertEquals(2, (obj["steps"] as JsonArray).size)
        val first = (obj["ingredients"] as JsonArray)[0] as JsonObject
        assertEquals("200", (first["quantity"] as JsonPrimitive).content)
        assertEquals("g", (first["unit"] as JsonPrimitive).content)
        assertEquals("spaghetti", (first["name"] as JsonPrimitive).content)
    }

    /** Quotes and newlines in a recipe must not produce broken JSON. */
    @Test
    fun `json escapes awkward characters`() {
        val awkward = details.copy(
            recipe = details.recipe.copy(title = "Pasta \"speciale\"", description = "Riga uno\nRiga due")
        )
        val obj = Json.parseToJsonElement(RecipeExport.toJson(awkward)) as JsonObject
        assertEquals("Pasta \"speciale\"", (obj["title"] as JsonPrimitive).content)
        assertEquals("Riga uno\nRiga due", (obj["description"] as JsonPrimitive).content)
    }

    @Test
    fun `a recipe with no macros simply omits the nutrition section`() {
        val md = RecipeExport.toMarkdown(details, macros = null)
        assertTrue(!md.contains("Nutrition"))
        val obj = Json.parseToJsonElement(RecipeExport.toJson(details)) as JsonObject
        assertTrue(!obj.containsKey("nutrition"))
    }
}
