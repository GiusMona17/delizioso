package com.delizioso.app.data.export

object QuickMealPrompt {

    fun buildPrompt(foodDescription: String, targetLanguage: String): String = buildString {
        appendLine("Below is a description of food or a quick meal eaten.")
        appendLine("Estimate the nutritional values and convert it into a valid JSON object — no markdown, no commentary —")
        appendLine("in exactly this shape:")
        appendLine()
        appendLine(SCHEMA)
        appendLine()
        appendLine("Rules:")
        appendLine("- Write the \"name\" concisely in $targetLanguage.")
        appendLine("- Estimate realistic macros (caloriesKcal, proteinG, fatG, carbsG as numbers).")
        appendLine()
        appendLine("--- FOOD DESCRIPTION ---")
        appendLine(foodDescription.trim())
    }

    private const val SCHEMA = """{
  "name": "Concise food title",
  "caloriesKcal": 380,
  "proteinG": 14,
  "fatG": 12,
  "carbsG": 54
}"""
}
