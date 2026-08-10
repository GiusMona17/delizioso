package com.delizioso.app.data.import

import com.delizioso.app.data.Categories
import com.delizioso.app.data.local.IngredientEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * schema.org Recipe JSON-LD extractor.
 *
 * Spike finding: the Recipe node is often NOT in a standalone `application/ld+json`
 * script — BBC Good Food embeds it inside an `application/json` hydration block
 * (nested under a `schema:` key). So we scan both script types and walk nested JSON
 * for a node whose `@type` is "Recipe".
 */
object RecipeJsonLdParser {

    fun parse(html: String, json: Json = Json { ignoreUnknownKeys = true }): StructuredRecipe? {
        val blocks = extractScriptBlocks(html)
        for (block in blocks) {
            val element = runCatching { json.parseToJsonElement(block) }.getOrNull() ?: continue
            val recipe = findRecipe(element) ?: continue
            return mapRecipe(recipe)
        }
        return null
    }

    private fun extractScriptBlocks(html: String): List<String> {
        val result = mutableListOf<String>()
        val pattern = Regex(
            """<script[^>]*type="(application/ld\+json|application/json)"[^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        for (match in pattern.findAll(html)) {
            match.groupValues[2].let { if (it.isNotBlank()) result += it }
        }
        return result
    }

    private fun findRecipe(element: JsonElement): JsonObject? {
        if (element is JsonObject) {
            val type = element["@type"]
            val isRecipe = when (type) {
                is JsonPrimitive -> type.content == "Recipe"
                is JsonArray -> type.any { (it as? JsonPrimitive)?.content == "Recipe" }
                else -> false
            }
            if (isRecipe) return element
            // Walk nested objects/arrays (e.g. BBC's `schema` key inside app-json).
            for (value in element.values) {
                findRecipe(value)?.let { return it }
            }
        } else if (element is JsonArray) {
            for (value in element) {
                findRecipe(value)?.let { return it }
            }
        }
        return null
    }

    private fun mapRecipe(obj: JsonObject): StructuredRecipe = StructuredRecipe(
        title = firstString(obj, "name", "headline"),
        description = firstString(obj, "description"),
        servings = parseServings(firstString(obj, "recipeYield")),
        prepTimeMinutes = parseIsoDuration(firstString(obj, "prepTime"))
            ?: parseIsoDuration(firstString(obj, "totalTime")),
        cookTimeMinutes = parseIsoDuration(firstString(obj, "cookTime")),
        imageUrl = firstImage(obj["image"]),
        ingredients = firstStringList(obj, "recipeIngredient")
            .map { IngredientParser.split(it) }
            .mapIndexed { i, ing -> ing.copy(position = i) },
        steps = parseInstructions(obj["recipeInstructions"]),
        // The site already classified this recipe — map its own words onto our
        // vocabulary rather than spending an inference on it.
        categories = Categories.canonicalise(
            firstStringList(obj, "recipeCategory") +
                firstStringList(obj, "recipeCuisine") +
                firstStringList(obj, "keywords").flatMap { it.split(',') }
        ),
    )

    private fun firstString(obj: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            when (val value = obj[key]) {
                is JsonPrimitive -> return value.content.trim().takeIf { it.isNotBlank() && it != "null" }
                is JsonArray -> value.firstOrNull()?.let { (it as? JsonPrimitive)?.content?.trim() }?.takeIf { it.isNotBlank() }?.let { return it }
                else -> {}
            }
        }
        return null
    }

    private fun firstStringList(obj: JsonObject, key: String): List<String> {
        val value = obj[key] ?: return emptyList()
        return when (value) {
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf { s -> s.isNotBlank() } }
            is JsonPrimitive -> listOf(value.content.trim()).filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun firstImage(value: JsonElement?): String? = when (value) {
        is JsonPrimitive -> value.content.takeIf { it.startsWith("http") }
        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.content }.firstOrNull { it.startsWith("http") }
        is JsonObject -> value["url"]?.let { (it as? JsonPrimitive)?.content }?.takeIf { it.startsWith("http") }
        else -> null
    }

    /** recipeInstructions may be a string, HowToStep objects, or a mix. */
    private fun parseInstructions(value: JsonElement?): List<String> {
        if (value == null) return emptyList()
        return when (value) {
            is JsonPrimitive -> listOf(value.content.trim()).filter { it.isNotBlank() }
            is JsonArray -> value.mapNotNull { step ->
                when (step) {
                    is JsonPrimitive -> step.content.trim().takeIf { it.isNotBlank() }
                    is JsonObject -> {
                        firstString(step, "text", "name")
                            ?: step["itemListElement"]?.let { parseInstructions(it) }?.joinToString(" ")
                    }
                    else -> null
                }
            }
            is JsonObject -> {
                val list = value["itemListElement"]
                if (list != null) parseInstructions(list) else firstString(value, "text")?.let { listOf(it) } ?: emptyList()
            }
        }
    }

    private fun parseServings(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        return Regex("""\d+""").find(raw)?.value?.toIntOrNull()
    }

    /** "PT15M" / "PT1H30M" → minutes. */
    private fun parseIsoDuration(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val m = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?""").find(raw.uppercase()) ?: return null
        val hours = m.groupValues[1].toIntOrNull() ?: 0
        val minutes = m.groupValues[2].toIntOrNull() ?: 0
        return hours * 60 + minutes
    }
}
