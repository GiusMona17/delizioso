package com.delizioso.app.data.ai

import com.delizioso.app.data.import.StructuredRecipe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** AI-estimated macros per serving (heuristic — not nutrition facts). */
data class MacrosEstimate(
    val kcal: Float?,
    val proteinG: Float?,
    val fatG: Float?,
    val carbsG: Float?,
)

data class IngredientSubstitution(
    val ingredient: String,
    val suggestion: String,
)

data class RecipeAdvice(
    val macros: MacrosEstimate?,
    val substitutions: List<IngredientSubstitution>,
)

/**
 * On-device AI advice for a saved recipe: macro estimates per serving and
 * ingredient substitutions. Estimates are LLM heuristics — the UI labels them as such.
 */
class NanoAdvisor(
    private val consentProvider: suspend () -> Boolean,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    suspend fun advice(recipe: StructuredRecipe): RecipeAdvice {
        if (!consentProvider()) {
            throw AiUnavailableException("On-device AI consent required")
        }
        NanoInference.ensureDownloaded()
        val model = com.google.mlkit.genai.prompt.Generation.getClient()
        val prompt = buildString {
            appendLine("You are a nutrition-savvy cooking assistant. Given a recipe, estimate macros per serving and suggest ingredient substitutions.")
            appendLine("Return ONLY valid JSON (no markdown, no commentary) with this exact shape:")
            appendLine("""{"macros":{"kcal":0.0,"proteinG":0.0,"fatG":0.0,"carbsG":0.0},"substitutions":[{"ingredient":"","suggestion":""}]}""")
            appendLine("Rules: estimate per serving using common nutrition knowledge; use 0.0 for unknown values; suggest at most 3 substitutions for the most impactful ingredients; if the recipe is empty, return empty arrays.")
            appendLine("---")
            appendLine(recipe.toPlainText().take(2500))
        }
        val response = NanoInference.generateContentWithBackoff(model, prompt)
        val raw = response.candidates.firstOrNull()?.text
            ?: throw AiUnavailableException("Gemini Nano returned no content")
        return parseAdvice(raw)
    }

    private fun parseAdvice(raw: String): RecipeAdvice {
        val element = runCatching { json.parseToJsonElement(NanoInference.stripToJson(raw)) }.getOrNull()
            ?: throw AiUnavailableException("Gemini Nano output was not valid JSON")
        val obj = element as? JsonObject
            ?: throw AiUnavailableException("Gemini Nano output was not an object")

        fun float(value: JsonPrimitive?): Float? =
            value?.content?.toFloatOrNull()?.takeIf { it > 0f }

        val macros = (obj["macros"] as? JsonObject)?.let { m ->
            MacrosEstimate(
                kcal = float(m["kcal"] as? JsonPrimitive),
                proteinG = float(m["proteinG"] as? JsonPrimitive),
                fatG = float(m["fatG"] as? JsonPrimitive),
                carbsG = float(m["carbsG"] as? JsonPrimitive),
            )
        }

        val substitutions = (obj["substitutions"] as? JsonArray)
            ?.mapNotNull { item ->
                (item as? JsonObject)?.let { s ->
                    val ingredient = (s["ingredient"] as? JsonPrimitive)?.content?.trim().orEmpty()
                    val suggestion = (s["suggestion"] as? JsonPrimitive)?.content?.trim().orEmpty()
                    if (ingredient.isBlank() || suggestion.isBlank()) null
                    else IngredientSubstitution(ingredient, suggestion)
                }
            }
            .orEmpty()

        return RecipeAdvice(macros = macros, substitutions = substitutions)
    }
}
