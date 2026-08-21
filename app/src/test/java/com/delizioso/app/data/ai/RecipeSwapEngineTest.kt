package com.delizioso.app.data.ai

import com.delizioso.app.data.local.PantryItemEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeSwapEngineTest {

    @Test
    fun `buildPrompt for VEGAN requests vegan substitutions`() {
        val prompt = RecipeSwapEngine.buildPrompt(SwapPreset.VEGAN)
        assertTrue(prompt.contains("vegan", ignoreCase = true))
    }

    @Test
    fun `buildPrompt for GLUTEN_FREE requests gluten-free substitutions`() {
        val prompt = RecipeSwapEngine.buildPrompt(SwapPreset.GLUTEN_FREE)
        assertTrue(prompt.contains("gluten-free", ignoreCase = true))
    }

    @Test
    fun `buildPrompt for PANTRY embeds in-stock pantry items`() {
        val pantry = listOf(
            PantryItemEntity(id = 1L, name = "Pasta", inStock = true),
            PantryItemEntity(id = 2L, name = "Olive Oil", inStock = true),
            PantryItemEntity(id = 3L, name = "Truffles", inStock = false),
        )
        val prompt = RecipeSwapEngine.buildPrompt(SwapPreset.PANTRY, pantry)
        assertTrue(prompt.contains("Pasta"))
        assertTrue(prompt.contains("Olive Oil"))
        assertTrue(!prompt.contains("Truffles"))
    }
}
