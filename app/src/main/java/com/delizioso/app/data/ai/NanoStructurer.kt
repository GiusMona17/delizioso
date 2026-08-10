package com.delizioso.app.data.ai

import com.delizioso.app.data.import.IngredientParser
import com.delizioso.app.data.import.StructuredRecipe
import com.google.mlkit.genai.prompt.Generation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Gemini Nano is unavailable / consent not granted / output unusable. */
class AiUnavailableException(
    message: String,
    /** True if retrying (e.g. after AICore BUSY) may help. */
    val retryable: Boolean = false,
) : Exception(message)

/**
 * On-device recipe structuring via the ML Kit GenAI Prompt API (Gemini Nano v3).
 * Requires runtime user consent (checked here) and the AICore model (auto-download).
 * Shared plumbing (availability/download/BUSY backoff) lives in [NanoInference].
 */
class NanoStructurer(
    private val consentProvider: suspend () -> Boolean,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    /** Whether Gemini Nano can be used (consent + feature status). */
    suspend fun availability(): NanoInference.Availability =
        NanoInference.availability(consentProvider())

    /** Downloads Gemini Nano (no-op when already available). */
    suspend fun ensureDownloaded() = NanoInference.ensureDownloaded()

    /** Structure free recipe text into [StructuredRecipe] via Gemini Nano (JSON output). */
    suspend fun structure(text: String): StructuredRecipe {
        val prompt = buildString {
            appendLine("You extract recipes from messy text found in social media captions and web pages.")
            appendLine("Return ONLY valid JSON (no markdown, no commentary) with this exact shape:")
            appendLine("""{"title":"","description":"","servings":0,"prepTimeMinutes":0,"cookTimeMinutes":0,"ingredients":["..."],"steps":["..."]}""")
            appendLine("Rules: keep ingredient quantities exactly as written; split instructions into numbered steps; use 0 for unknown numbers and empty string for unknown text; if the text has no recipe, return an empty title.")
            appendLine("---")
            appendLine(text.take(3000))
        }
        val response = NanoInference.generateContentWithBackoff(Generation.getClient(), prompt)
        val raw = response.candidates.firstOrNull()?.text
            ?: throw AiUnavailableException("Gemini Nano returned no content")
        return parseRecipe(raw)
    }

    private fun parseRecipe(raw: String): StructuredRecipe {
        val element = runCatching { json.parseToJsonElement(NanoInference.stripToJson(raw)) }.getOrNull()
            ?: throw AiUnavailableException("Gemini Nano output was not valid JSON")
        val obj = element as? JsonObject
            ?: throw AiUnavailableException("Gemini Nano output was not an object")
        fun str(key: String): String? =
            (obj[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() && it != "null" }

        val title = str("title")
        if (title.isNullOrBlank()) {
            throw AiUnavailableException("No recipe was found in this content")
        }
        fun int(key: String): Int? = str(key)?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }

        val ingredientsRaw = (obj["ingredients"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }
            .orEmpty()
        val steps = (obj["steps"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }
            .orEmpty()

        return StructuredRecipe(
            title = title,
            description = str("description"),
            servings = int("servings"),
            prepTimeMinutes = int("prepTimeMinutes"),
            cookTimeMinutes = int("cookTimeMinutes"),
            ingredients = ingredientsRaw.mapIndexed { i, line -> IngredientParser.split(line).copy(position = i) },
            steps = steps,
        )
    }
}
