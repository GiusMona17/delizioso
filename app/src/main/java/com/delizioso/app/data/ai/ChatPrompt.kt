package com.delizioso.app.data.ai

import com.delizioso.app.data.import.StructuredRecipe
import java.util.Locale

/**
 * The "ask about this recipe" prompt, shared by every model that can answer it.
 *
 * Neither engine has a conversation object, so each turn re-sends the recipe plus
 * the history as one prompt. How much history survives is the caller's problem —
 * Nano has to count tokens against a small window, Gemma can simply keep more.
 */
object ChatPrompt {

    /**
     * [language] is named outright rather than left as "the language the user
     * writes in": a 1B model reliably ignores that indirection and answers in
     * English regardless of what it was asked.
     */
    fun system(language: String): String =
        "You are a friendly cooking assistant answering questions about ONE specific recipe, " +
            "shown below. Write your entire answer in $language. Answer in 2-4 short sentences. " +
            "Give practical kitchen advice: substitutions, techniques, timings, storage, scaling. " +
            "Refer to this recipe's own ingredients by name rather than giving generic advice. " +
            "If the recipe does not contain the answer, say what you would do generally and be " +
            "clear that it is a suggestion. Never invent precise nutrition figures."

    /** The device language, in English, which is how a model expects to be told. */
    fun deviceLanguage(): String =
        Locale.getDefault().getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }

    fun build(
        recipe: StructuredRecipe,
        history: List<ChatMessage>,
        question: String,
        language: String = deviceLanguage(),
    ): String = buildString {
        appendLine(system(language))
        appendLine("--- RECIPE ---")
        appendLine(recipe.toPlainText().take(2500))
        if (history.isNotEmpty()) {
            appendLine("--- CONVERSATION SO FAR ---")
            history.forEach { message ->
                val who = if (message.role == ChatMessage.Role.USER) "User" else "Assistant"
                appendLine("$who: ${message.text}")
            }
        }
        appendLine("--- QUESTION ---")
        appendLine("User: $question")
        append("Assistant:")
    }
}
