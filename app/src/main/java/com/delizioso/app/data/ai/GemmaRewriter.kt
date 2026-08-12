package com.delizioso.app.data.ai

import com.delizioso.app.data.import.IngredientParser
import com.delizioso.app.data.import.StructuredRecipe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/**
 * "Tidy up this recipe" — the one job worth a big model.
 *
 * Extraction stays with the deterministic parser, which is exact. This instead
 * *rewrites*: translates a foreign caption, converts cups and ounces into grams
 * and millilitres, and turns terse one-liners into steps you can actually cook
 * from. It is user-invoked, never automatic, because it necessarily paraphrases
 * and the original wording is worth keeping until asked otherwise.
 */
class GemmaRewriter(
    private val engine: GemmaEngine,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    fun isAvailable(): Boolean = engine.isInstalled()

    /**
     * Returns the rewritten recipe. Ingredients and steps are replaced; the
     * photo, source and categories are the caller's to keep.
     */
    suspend fun rewrite(
        recipe: StructuredRecipe,
        targetLanguage: String = Locale.getDefault().displayLanguage,
    ): StructuredRecipe {
        val prompt = buildPrompt(recipe, targetLanguage)
        val raw = engine.generate(prompt)
        return parse(raw, recipe)
    }

    private fun buildPrompt(recipe: StructuredRecipe, targetLanguage: String): String = buildString {
        appendLine("You are a recipe editor. Rewrite the recipe below for a home cook who reads $targetLanguage.")
        appendLine("Return ONLY valid JSON, no markdown and no commentary, in exactly this shape:")
        appendLine("""{"title":"","description":"","ingredients":["..."],"steps":["..."]}""")
        appendLine("Rules:")
        appendLine("- Write every field in $targetLanguage.")
        appendLine("- Convert imperial amounts to metric: cups/oz/lb to grams or millilitres, °F to °C. Round sensibly (e.g. 1 cup flour = 120 g).")
        appendLine("- Amounts ALREADY metric must be copied unchanged. Never invent quantities that were not there.")
        appendLine("- Keep every ingredient. Do not add or drop any.")
        appendLine("- Rewrite each step as one clear instruction, adding the technique detail a beginner needs, but never inventing times or temperatures that were not implied.")
        appendLine("- \"description\": one short appetising sentence.")
        appendLine("--- RECIPE ---")
        recipe.title?.let { appendLine("Title: $it") }
        recipe.servings?.let { appendLine("Servings: $it") }
        appendLine("Ingredients:")
        recipe.ingredients.forEach { ingredient ->
            appendLine("- " + (ingredient.rawText ?: listOfNotNull(ingredient.quantity, ingredient.unit, ingredient.name).joinToString(" ")))
        }
        appendLine("Steps:")
        recipe.steps.forEachIndexed { index, step -> appendLine("${index + 1}. $step") }
    }

    private fun parse(raw: String, original: StructuredRecipe): StructuredRecipe {
        val element = runCatching { json.parseToJsonElement(NanoInference.stripToJson(raw)) }.getOrNull()
            ?: NanoInference.repairTruncatedJson(raw)?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
            ?: throw AiUnavailableException("The model's answer was not valid JSON")
        val obj = element as? JsonObject
            ?: throw AiUnavailableException("The model's answer was not an object")

        fun strings(key: String): List<String> = (obj[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }
            .orEmpty()

        val ingredients = strings("ingredients")
        val steps = strings("steps")
        // A rewrite that loses the recipe is worse than no rewrite at all.
        if (ingredients.isEmpty() || steps.isEmpty()) {
            throw AiUnavailableException("The model did not return a complete recipe")
        }

        fun str(key: String) = (obj[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() }

        return original.copy(
            title = str("title") ?: original.title,
            description = str("description") ?: original.description,
            ingredients = ingredients.mapIndexed { i, line -> IngredientParser.split(line).copy(position = i) },
            steps = steps,
        )
    }
}
