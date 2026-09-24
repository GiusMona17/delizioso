package com.delizioso.app.data.export

import com.delizioso.app.data.import.IngredientParser
import com.delizioso.app.data.local.CookingLogEntity
import com.delizioso.app.data.local.RecipeEntity
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.data.local.SourceEntity
import com.delizioso.app.data.local.StepEntity
import com.delizioso.app.data.local.TagEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipePromptTest {

    private val details = RecipeWithDetails(
        recipe = RecipeEntity(
            id = 1,
            title = "Spaghetti alla Carbonara",
            description = "Ricetta tipica romana",
            servings = 4,
            prepTimeMinutes = 15,
            cookTimeMinutes = 10,
        ),
        ingredients = listOf("320 g spaghetti", "150 g guanciale", "4 tuorli", "50 g pecorino")
            .mapIndexed { i, line -> IngredientParser.split(line).copy(recipeId = 1, position = i) },
        steps = listOf("Rosolare il guanciale.", "Sbattere i tuorli con il pecorino.", "Mantecare la pasta fuori dal fuoco.")
            .mapIndexed { i, text -> StepEntity(recipeId = 1, position = i + 1, text = text) },
        source = SourceEntity(recipeId = 1, platform = "BLOG", url = "https://example.com/carbonara"),
        tags = listOf(TagEntity("Primi"), TagEntity("Romana")),
        cookingLogs = listOf(
            CookingLogEntity(
                id = 10,
                recipeId = 1,
                dateEpochDay = 20000,
                notes = "Usare acqua di cottura ben calda per una crema perfetta",
                rating = 5,
            )
        )
    )

    @Test
    fun `forRecipeQuestions includes full recipe markdown and cooking notes`() {
        val prompt = RecipePrompt.forRecipeQuestions(details, "Italian")

        assertTrue(prompt.contains("You are an expert culinary assistant"))
        assertTrue(prompt.contains("Please communicate in Italian"))
        assertTrue(prompt.contains("# Spaghetti alla Carbonara"))
        assertTrue(prompt.contains("320 g spaghetti"))
        assertTrue(prompt.contains("Rosolare il guanciale"))
        assertTrue(prompt.contains("--- PREVIOUS COOKING NOTES ---"))
        assertTrue(prompt.contains("Usare acqua di cottura ben calda per una crema perfetta"))
        assertTrue(prompt.contains("Ready! Please greet me in Italian"))
    }

    @Test
    fun `forRecipeQuestions omits notes section when no logs have notes`() {
        val withoutLogs = details.copy(cookingLogs = emptyList())
        val prompt = RecipePrompt.forRecipeQuestions(withoutLogs, "English")

        assertTrue(prompt.contains("# Spaghetti alla Carbonara"))
        assertFalse(prompt.contains("--- PREVIOUS COOKING NOTES ---"))
        assertTrue(prompt.contains("Please communicate in English"))
    }

    @Test
    fun `forRecipeWithDetails includes enrichment schema`() {
        val prompt = RecipePrompt.forRecipeWithDetails(details, "Italian")

        assertTrue(prompt.contains("Enrich, structure, and convert it into a valid JSON object"))
        assertTrue(prompt.contains("Write every field in Italian"))
        assertTrue(prompt.contains("--- RECIPE CONTENT ---"))
        assertTrue(prompt.contains("# Spaghetti alla Carbonara"))
    }

    @Test
    fun `forCaption creates enrichment prompt with raw caption`() {
        val caption = "Prendi 200g di farina e due uova per fare la pasta fresca"
        val prompt = RecipePrompt.forCaption(caption, "Italian")

        assertTrue(prompt.contains("--- RECIPE CONTENT ---"))
        assertTrue(prompt.contains(caption))
    }
}
