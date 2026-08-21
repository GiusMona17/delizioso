package com.delizioso.app.data.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RecipeJsonImporterNutritionTest {

    @Test
    fun `parses complete AI enriched JSON response with macros, times, and portions`() {
        val jsonText = """
            ```json
            {
              "title": "Spaghetti alla Carbonara",
              "description": "Un grande classico della cucina romana",
              "servings": 4,
              "prepTimeMinutes": 15,
              "cookTimeMinutes": 10,
              "tags": ["Dinner", "Quick"],
              "ingredients": [
                {"quantity": "320", "unit": "g", "name": "Spaghetti"},
                {"quantity": "150", "unit": "g", "name": "Guanciale"},
                {"quantity": "6", "unit": null, "name": "Tuorli"},
                {"quantity": "50", "unit": "g", "name": "Pecorino Romano"},
                {"quantity": null, "unit": "q.b.", "name": "Pepe nero"}
              ],
              "steps": [
                "Tagliare il guanciale a listarelle e rosolarlo.",
                "Cuocere gli spaghetti in abbondante acqua salata.",
                "Sbattere i tuorli con il pecorino e una macinata di pepe.",
                "Amalgamare la pasta con il condimento a fuoco spento."
              ],
              "nutrition": {
                "caloriesKcal": 520.0,
                "proteinG": 24.5,
                "fatG": 22.0,
                "carbsG": 58.0
              }
            }
            ```
        """.trimIndent()

        val recipe = RecipeJsonImporter.parse(jsonText)
        assertNotNull(recipe)
        assertEquals("Spaghetti alla Carbonara", recipe?.title)
        assertEquals(4, recipe?.servings)
        assertEquals(15, recipe?.prepTimeMinutes)
        assertEquals(10, recipe?.cookTimeMinutes)
        assertEquals(5, recipe?.ingredients?.size)

        assertEquals("320", recipe?.ingredients?.get(0)?.quantity)
        assertEquals("g", recipe?.ingredients?.get(0)?.unit)
        assertEquals("Spaghetti", recipe?.ingredients?.get(0)?.name)

        assertNotNull(recipe?.nutrition)
        assertEquals(520.0, recipe?.nutrition?.caloriesKcal ?: 0.0, 0.01)
        assertEquals(24.5, recipe?.nutrition?.proteinG ?: 0.0, 0.01)
        assertEquals(22.0, recipe?.nutrition?.fatG ?: 0.0, 0.01)
        assertEquals(58.0, recipe?.nutrition?.carbsG ?: 0.0, 0.01)
    }
}
