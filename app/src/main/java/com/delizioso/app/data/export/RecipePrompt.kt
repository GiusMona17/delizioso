package com.delizioso.app.data.export

import com.delizioso.app.data.import.StructuredRecipe
import com.delizioso.app.data.local.RecipeWithDetails

/**
 * The prompt handed to an external AI assistant to structure, enrich, and calculate
 * recipe metadata (macros, preparation & cooking time, portions, scaleable ingredients).
 */
object RecipePrompt {

    fun forCaption(caption: String, targetLanguage: String): String =
        buildPrompt(caption.trim(), targetLanguage)

    fun forRecipe(recipe: StructuredRecipe, targetLanguage: String): String =
        buildPrompt(recipe.toPlainText().trim(), targetLanguage)

    fun forRecipeWithDetails(details: RecipeWithDetails, targetLanguage: String): String =
        buildPrompt(RecipeExport.toMarkdown(details).trim(), targetLanguage)

    /**
     * Prompt handed to an external AI assistant (ChatGPT, Claude, Gemini, etc.)
     * allowing the user to chat, ask questions, request substitutions, or get cooking advice
     * based on the recipe in its current saved state.
     */
    fun forRecipeQuestions(details: RecipeWithDetails, targetLanguage: String): String = buildString {
        appendLine("You are an expert culinary assistant. I am cooking the following saved recipe.")
        appendLine("Please review the recipe details and be ready to answer my questions about it.")
        appendLine("You can assist with:")
        appendLine("- Ingredient substitutions and dietary adaptations")
        appendLine("- Cooking techniques, equipment advice, and step-by-step guidance")
        appendLine("- Temperature, timing, and doneness cues")
        appendLine("- Scaling portions and wine or side-dish pairings")
        appendLine("- Storage, leftovers, freezing, and reheating recommendations")
        appendLine()
        appendLine("Please communicate in $targetLanguage.")
        appendLine()
        appendLine("--- RECIPE ---")
        appendLine(RecipeExport.toMarkdown(details).trim())
        if (details.cookingLogs.isNotEmpty()) {
            val notes = details.cookingLogs.mapNotNull { it.notes.takeIf { n -> n.isNotBlank() } }
            if (notes.isNotEmpty()) {
                appendLine()
                appendLine("--- PREVIOUS COOKING NOTES ---")
                notes.take(3).forEach { appendLine("- $it") }
            }
        }
        appendLine()
        appendLine("---")
        appendLine("Ready! Please greet me in $targetLanguage, briefly acknowledge what we are cooking, and ask how you can help.")
    }

    private fun buildPrompt(recipeContent: String, targetLanguage: String): String = buildString {
        appendLine("Below is the text/description of a recipe.")
        appendLine("Enrich, structure, and convert it into a valid JSON object — no markdown, no commentary —")
        appendLine("in exactly this shape:")
        appendLine()
        appendLine(SCHEMA)
        appendLine()
        appendLine("Rules:")
        appendLine("- Write every field in $targetLanguage.")
        appendLine("- Convert imperial amounts to grams, millilitres and °C.")
        appendLine("- Split \"quantity\", \"unit\" and \"name\": e.g. {\"quantity\":\"320\",\"unit\":\"g\",\"name\":\"spaghetti\"}.")
        appendLine("  Ensure \"quantity\" is a clean numeric string (or fraction like \"1/2\") so the app can scale portions mathematically.")
        appendLine("  For qualitative ingredients (to taste), use null quantity and \"q.b.\" as unit: {\"quantity\":null,\"unit\":\"q.b.\",\"name\":\"sale\"}.")
        appendLine("- \"servings\": number of portions (e.g. 4). If unstated in text, provide a realistic estimate (e.g. 2 or 4).")
        appendLine("- \"prepTimeMinutes\" and \"cookTimeMinutes\": integer numbers in minutes.")
        appendLine("- \"nutrition\": calculated or stated macros per serving (caloriesKcal, proteinG, fatG, carbsG as numbers).")
        appendLine("- \"steps\": one clear instruction per entry, in order, without numbering.")
        appendLine("- \"title\": the clear name of the dish.")
        appendLine("- \"tags\": at most 3, chosen from: $TAGS")
        appendLine()
        appendLine("--- RECIPE CONTENT ---")
        appendLine(recipeContent)
    }

    private const val SCHEMA = """{
  "title": "",
  "description": "",
  "servings": 4,
  "prepTimeMinutes": 15,
  "cookTimeMinutes": 20,
  "tags": [],
  "ingredients": [
    {"quantity": "320", "unit": "g", "name": "spaghetti"},
    {"quantity": null, "unit": "q.b.", "name": "sale"}
  ],
  "steps": [""],
  "nutrition": {
    "caloriesKcal": 450,
    "proteinG": 22,
    "fatG": 18,
    "carbsG": 55
  }
}"""

    private val TAGS: String
        get() = com.delizioso.app.data.Categories.ALL.joinToString(", ")
}
