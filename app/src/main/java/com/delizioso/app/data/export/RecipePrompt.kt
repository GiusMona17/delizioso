package com.delizioso.app.data.export

/**
 * The prompt handed to an external assistant to restructure a messy caption.
 *
 * Every account writes its ingredients and method differently — bullets, emoji,
 * run-on paragraphs, three languages — and the deterministic parser only wins
 * when the caption declares its own sections. Rather than grow a model inside the
 * app for the rest, this hands the raw text to whichever assistant the user
 * already has and asks for it back in the shape the app imports.
 *
 * The requested JSON is deliberately the same shape [RecipeExport.toJson] emits,
 * so there is one format in the app rather than two, and a recipe exported from
 * here can be fed straight back in.
 */
object RecipePrompt {

    fun forCaption(caption: String, targetLanguage: String): String = buildString {
        appendLine("Below is the description of a recipe, copied from social media.")
        appendLine("Reorganise it and return ONLY a JSON object — no markdown, no commentary —")
        appendLine("in exactly this shape:")
        appendLine()
        appendLine(SCHEMA)
        appendLine()
        appendLine("Rules:")
        appendLine("- Write every field in $targetLanguage.")
        appendLine("- Convert imperial amounts to grams, millilitres and °C.")
        appendLine("- Split \"quantity\", \"unit\" and \"name\": {\"quantity\":\"200\",\"unit\":\"g\",\"name\":\"farina\"}.")
        appendLine("  Use null for quantity or unit when the text gives none.")
        appendLine("- \"steps\": one instruction per entry, in order, without numbering.")
        appendLine("- Drop hashtags, greetings, calls to action and anything about the account.")
        appendLine("- Never invent an ingredient, a time or a temperature that is not in the text.")
        appendLine("- \"title\": the name of the dish. If the text never names it, describe it in a few words.")
        appendLine("- \"servings\", \"prepTimeMinutes\", \"cookTimeMinutes\": numbers, or null when unstated.")
        appendLine("- \"tags\": at most 3, chosen from: $TAGS")
        appendLine()
        appendLine("--- RECIPE TEXT ---")
        appendLine(caption.trim())
    }

    private const val SCHEMA = """{
  "title": "",
  "description": "",
  "servings": null,
  "prepTimeMinutes": null,
  "cookTimeMinutes": null,
  "tags": [],
  "ingredients": [{"quantity": "", "unit": "", "name": ""}],
  "steps": [""]
}"""

    private val TAGS: String
        get() = com.delizioso.app.data.Categories.ALL.joinToString(", ")
}
