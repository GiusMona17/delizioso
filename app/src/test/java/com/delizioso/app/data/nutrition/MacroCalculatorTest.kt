package com.delizioso.app.data.nutrition

import com.delizioso.app.data.import.IngredientParser
import com.delizioso.app.data.import.StructuredRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroCalculatorTest {

    private fun recipe(servings: Int?, vararg lines: String) = StructuredRecipe(
        title = "Test",
        servings = servings,
        ingredients = lines.mapIndexed { i, line -> IngredientParser.split(line).copy(position = i) },
    )

    @Test
    fun `sums a whole recipe and divides by servings`() {
        // 200g spaghetti 742 + 100g guanciale 450 + 2 eggs 143 + 50g pecorino 193.5
        val macros = MacroCalculator.of(
            recipe(2, "200 g spaghetti", "100 g guanciale", "2 uova", "50 g pecorino")
        )!!
        assertEquals(764.25, macros.kcal, 0.01)
        assertEquals(4, macros.matched)
        assertEquals(4, macros.total)
        assertTrue(macros.perServing)
    }

    @Test
    fun `pieces are weighed, not counted`() {
        // One egg is 50 g, so two eggs are 100 g — not "2 grams".
        val macros = MacroCalculator.of(recipe(1, "2 uova", "100 g farina", "200 ml latte"))!!
        assertEquals(143.0 + 364.0 + 61.0 * 2.06, macros.kcal, 0.5)
    }

    @Test
    fun `volumes use the ingredient's density`() {
        // 30 ml of oil is 27.6 g, not 30 g.
        val macros = MacroCalculator.of(recipe(1, "30 ml olio d'oliva", "100 g pasta", "1 spicchio aglio"))!!
        assertEquals(884.0 * 0.276 + 371.0 + 149.0 * 0.03, macros.kcal, 0.5)
    }

    @Test
    fun `seasoning without an amount still counts as recognised`() {
        val macros = MacroCalculator.of(recipe(1, "200 g riso", "sale q.b.", "acqua"))!!
        assertEquals(3, macros.matched)
        assertEquals(720.0, macros.kcal, 0.01)
    }

    @Test
    fun `a longer name wins over a shorter one`() {
        val peanut = NutritionTable.lookup("burro di arachidi")!!
        assertEquals(588.0, peanut.kcal, 0.01)
        assertEquals(717.0, NutritionTable.lookup("burro")!!.kcal, 0.01)
        assertEquals(380.0, NutritionTable.lookup("zucchero di canna")!!.kcal, 0.01)
    }

    /** Guessing from two known ingredients out of eight would be a made-up number. */
    @Test
    fun `too few recognised ingredients means no estimate at all`() {
        assertNull(
            MacroCalculator.of(
                recipe(2, "200 g farina", "100 g pasta", "2 wobblefruit", "3 snorkleberries", "1 gribblenut", "2 flurb")
            )
        )
        assertNull(MacroCalculator.of(recipe(2)))
    }

    @Test
    fun `without servings the total is for the whole recipe`() {
        val macros = MacroCalculator.of(recipe(null, "100 g pasta", "100 g pomodori"))!!
        assertEquals(371.0 + 18.0, macros.kcal, 0.01)
        assertTrue(!macros.perServing)
    }

    @Test
    fun `stored AI macros take priority over ingredient calculation`() {
        val details = com.delizioso.app.data.local.RecipeWithDetails(
            recipe = com.delizioso.app.data.local.RecipeEntity(
                title = "Carbonara",
                servings = 4,
                caloriesKcal = 550.0,
                proteinG = 26.0,
                fatG = 24.0,
                carbsG = 60.0,
            ),
            ingredients = listOf(
                com.delizioso.app.data.local.IngredientEntity(recipeId = 0, position = 0, name = "spaghetti", quantity = "320", unit = "g")
            ),
            steps = emptyList(),
        )
        val macros = MacroCalculator.of(details)!!
        assertEquals(550.0, macros.kcal, 0.01)
        assertEquals(26.0, macros.proteinG, 0.01)
        assertEquals(24.0, macros.fatG, 0.01)
        assertEquals(60.0, macros.carbsG, 0.01)
        assertTrue(macros.perServing)
    }
}
