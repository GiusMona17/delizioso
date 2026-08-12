package com.delizioso.app.data.ai

import com.delizioso.app.data.UnitConverter
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
        // Units are converted in code FIRST, so the model never does arithmetic —
        // it only ever sees numbers that are already correct and is told to copy
        // them verbatim. Gemma 3 1B left cups and ounces untouched when asked to
        // convert them itself.
        val metric = UnitConverter.convert(recipe)
        val prompt = buildPrompt(metric, targetLanguage)
        val raw = engine.generate(prompt)
        android.util.Log.d("GemmaRewrite", "DIAG len=${raw.length} raw=<<<$raw>>>")
        return parse(raw, metric)
    }

    private fun buildPrompt(recipe: StructuredRecipe, targetLanguage: String): String = buildString {
        appendLine("You are a recipe editor. Rewrite the recipe below for a home cook who reads $targetLanguage.")
        appendLine("Return ONLY valid JSON, no markdown and no commentary, in exactly this shape:")
        appendLine("""{"title":"","description":"","ingredients":["..."],"steps":["..."]}""")
        appendLine("Rules:")
        appendLine("- Translate every field into $targetLanguage.")
        appendLine("- The amounts below are ALREADY correct. Copy every number and unit EXACTLY as written. Do not convert, recalculate or round anything.")
        appendLine("- Keep every ingredient, in the same order. Do not add or drop any.")
        appendLine("- Keep the same number of steps. Rewrite each one as a clear instruction in $targetLanguage, adding the technique detail a beginner needs, but never inventing times, temperatures or ingredients.")
        appendLine("- \"title\": the name of THIS dish. Do not rename it to a different dish.")
        appendLine("- \"description\": one short appetising sentence about this dish.")
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
