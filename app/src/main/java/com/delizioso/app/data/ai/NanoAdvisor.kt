package com.delizioso.app.data.ai

import com.delizioso.app.data.import.StructuredRecipe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** AI-estimated macros per serving (heuristic — not nutrition facts). */
data class MacrosEstimate(
    val kcal: Float?,
    val proteinG: Float?,
    val fatG: Float?,
    val carbsG: Float?,
)

/**
 * On-device macro estimates for a saved recipe. These are LLM heuristics, not
 * nutrition data — the UI labels them as such. Anything conversational
 * (substitutions, technique questions) belongs to [NanoChat].
 */
class NanoAdvisor(
    private val consentProvider: suspend () -> Boolean,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    suspend fun macros(recipe: StructuredRecipe): MacrosEstimate {
        if (!consentProvider()) {
            throw AiUnavailableException("On-device AI consent required")
        }
        NanoInference.ensureDownloaded()
        val model = com.google.mlkit.genai.prompt.Generation.getClient()
        val prompt = buildString {
            appendLine("You are a nutrition-savvy cooking assistant. Given a recipe, estimate the macros per serving.")
            appendLine("Return ONLY valid JSON (no markdown, no commentary) with this exact shape:")
            appendLine("""{"macros":{"kcal":0.0,"proteinG":0.0,"fatG":0.0,"carbsG":0.0}}""")
            appendLine("Rules: estimate per serving using common nutrition knowledge; use 0.0 for any value you cannot estimate.")
            appendLine("---")
            appendLine(recipe.toPlainText().take(2500))
        }
        val response = NanoInference.generateContentWithBackoff(model, prompt)
        val raw = response.candidates.firstOrNull()?.text
            ?: throw AiUnavailableException("Gemini Nano returned no content")
        return parseMacros(raw)
    }

    private fun parseMacros(raw: String): MacrosEstimate {
        val element = runCatching { json.parseToJsonElement(NanoInference.stripToJson(raw)) }.getOrNull()
            ?: throw AiUnavailableException("Gemini Nano output was not valid JSON")
        val obj = element as? JsonObject
            ?: throw AiUnavailableException("Gemini Nano output was not an object")

        fun float(value: JsonPrimitive?): Float? =
            value?.content?.toFloatOrNull()?.takeIf { it > 0f }

        // Some answers nest under "macros", some return the fields at the top level.
        val m = (obj["macros"] as? JsonObject) ?: obj
        return MacrosEstimate(
            kcal = float(m["kcal"] as? JsonPrimitive),
            proteinG = float(m["proteinG"] as? JsonPrimitive),
            fatG = float(m["fatG"] as? JsonPrimitive),
            carbsG = float(m["carbsG"] as? JsonPrimitive),
        )
    }
}
