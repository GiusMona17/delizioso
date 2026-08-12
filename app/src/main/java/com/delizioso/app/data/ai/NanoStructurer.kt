package com.delizioso.app.data.ai

import com.delizioso.app.data.Categories
import com.delizioso.app.data.import.CaptionRecipeParser
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

    /**
     * Structure free recipe text into [StructuredRecipe] via Gemini Nano.
     *
     * The model is asked which *lines* hold the ingredients and which hold the
     * steps, never to repeat them: one answer is capped at 256 tokens (~900
     * characters), far less than a real recipe needs. Line references also keep
     * the author's exact quantities instead of a paraphrase.
     */
    suspend fun structure(text: String): StructuredRecipe {
        // Most captions label their own sections; a regex reads those correctly every
        // time, where a small model has to count lines and sometimes miscounts. Only
        // captions without headings are worth an inference.
        CaptionRecipeParser.parse(text)?.let { parsed ->
            return parsed.copy(categories = categorise(parsed))
        }

        val lines = CaptionLines.split(text)
        if (lines.isEmpty()) throw AiUnavailableException("No recipe was found in this content")

        val prompt = buildString {
            appendLine("You label the numbered lines of a recipe post. Answer about the lines only.")
            appendLine("Return ONLY valid JSON (no markdown, no commentary) with this exact shape:")
            appendLine("""{"title":"","servings":0,"prepTimeMinutes":0,"cookTimeMinutes":0,"ingredientLines":["2-7"],"stepLines":["9-14"],"categories":[""]}""")
            appendLine("Rules:")
            appendLine("- \"ingredientLines\" and \"stepLines\": the LINE NUMBERS holding them, as ranges like \"4-9\" or single numbers. NEVER copy the line text.")
            appendLine("- Skip section headers, hashtags, macros and calls to action.")
            appendLine("- \"title\": a short dish name (copy it or write one). Use 0 for unknown numbers.")
            appendLine("- \"categories\": at most ${Categories.MAX_PER_RECIPE}, copied EXACTLY from: ${Categories.ALL.joinToString(", ")}.")
            appendLine("--- NUMBERED LINES ---")
            appendLine(CaptionLines.numbered(lines))
        }
        val response = NanoInference.generateContentWithBackoff(Generation.getClient(), prompt)
        val raw = response.candidates.firstOrNull()?.text
            ?: throw AiUnavailableException("Gemini Nano returned no content")
        return parseRecipe(raw, lines)
    }

    /**
     * Picks categories for an already-parsed recipe. Its own small call: the answer
     * is a handful of words, so it fits the 256-token ceiling easily. Best-effort —
     * a recipe without categories is still a good import.
     */
    private suspend fun categorise(recipe: StructuredRecipe): List<String> = runCatching {
        val prompt = buildString {
            appendLine("Classify this recipe. Return ONLY valid JSON: {\"categories\":[\"\"]}")
            appendLine("Choose at most ${Categories.MAX_PER_RECIPE} values copied EXACTLY from this list and nothing else:")
            appendLine(Categories.ALL.joinToString(", "))
            appendLine("---")
            appendLine(recipe.title.orEmpty())
            appendLine(recipe.ingredients.take(12).joinToString(", ") { it.name })
        }
        val raw = NanoInference.generateContentWithBackoff(Generation.getClient(), prompt)
            .candidates.firstOrNull()?.text.orEmpty()
        val obj = json.parseToJsonElement(NanoInference.stripToJson(raw)) as JsonObject
        Categories.canonicalise(
            (obj["categories"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
        )
    }.getOrDefault(emptyList())

    private fun parseRecipe(raw: String, lines: List<String>): StructuredRecipe {
        val element = runCatching { json.parseToJsonElement(NanoInference.stripToJson(raw)) }.getOrNull()
            // The answer may have been cut off mid-JSON; keep what did arrive.
            ?: NanoInference.repairTruncatedJson(raw)?.let { repaired ->
                runCatching { json.parseToJsonElement(repaired) }.getOrNull()
            }
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

        /** Reads either the line-reference key or, if the model ignored the format, the literal one. */
        fun section(referenceKey: String, literalKey: String): List<String> {
            val refs = (obj[referenceKey] as? JsonArray ?: obj[literalKey] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content }
                .orEmpty()
            return CaptionLines.resolve(lines, refs)
        }

        val ingredientsRaw = section("ingredientLines", "ingredients")
        val steps = section("stepLines", "steps")
        // A title with no ingredients and no method is not a recipe — the post was
        // something else. Treat it as a miss so the caller can say so, rather than
        // handing back a recipe whose sections are silently empty.
        if (ingredientsRaw.isEmpty() && steps.isEmpty()) {
            throw AiUnavailableException("No ingredients or steps were found in this content")
        }
        // The model is told to copy from the list; anything it invents is dropped here.
        val categories = Categories.canonicalise(
            (obj["categories"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content }
                .orEmpty()
        )

        return StructuredRecipe(
            title = title,
            description = str("description"),
            servings = int("servings"),
            prepTimeMinutes = int("prepTimeMinutes"),
            cookTimeMinutes = int("cookTimeMinutes"),
            ingredients = ingredientsRaw.mapIndexed { i, line -> IngredientParser.split(line).copy(position = i) },
            steps = steps,
            categories = categories,
        )
    }
}
