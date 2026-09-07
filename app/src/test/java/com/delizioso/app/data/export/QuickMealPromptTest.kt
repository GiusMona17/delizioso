package com.delizioso.app.data.export

import org.junit.Assert.assertTrue
import org.junit.Test

class QuickMealPromptTest {

    @Test
    fun `buildPrompt includes description targetLanguage and expected json keys`() {
        val prompt = QuickMealPrompt.buildPrompt("Latte e biscotti", "Italian")
        assertTrue(prompt.contains("Latte e biscotti"))
        assertTrue(prompt.contains("Italian"))
        assertTrue(prompt.contains("caloriesKcal"))
        assertTrue(prompt.contains("proteinG"))
        assertTrue(prompt.contains("fatG"))
        assertTrue(prompt.contains("carbsG"))
    }
}
