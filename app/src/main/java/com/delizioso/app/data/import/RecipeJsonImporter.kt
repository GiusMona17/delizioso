package com.delizioso.app.data.import

import com.delizioso.app.data.Categories
import com.delizioso.app.data.local.IngredientEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads back the JSON an external assistant was asked for by
 * [com.delizioso.app.data.export.RecipePrompt] — the same shape the app exports,
 * so one format serves both directions.
 */
object RecipeJsonImporter {

    /** True when [text] looks like an attempt at the JSON, fences and all. */
    fun looksLikeJson(text: String): Boolean {
        val trimmed = text.trim().removePrefix("```json").removePrefix("```").trim()
        return trimmed.startsWith("{") && trimmed.contains("\"ingredients\"")
    }

    /** The recipe, or null when the text is not usable JSON. */
    fun parse(text: String, json: Json = Json { ignoreUnknownKeys = true }): StructuredRecipe? {
        val body = extractObject(text) ?: return null
        val obj = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null

        val ingredients = (obj["ingredients"] as? JsonArray).orEmpty().mapIndexedNotNull { i, element ->
            when (element) {
                // "200 g farina" — some assistants ignore the object shape.
                is JsonPrimitive -> element.content.trim().takeIf { it.isNotEmpty() }
                    ?.let { IngredientParser.split(it).copy(position = i) }
                is JsonObject -> {
                    val name = element.str("name") ?: return@mapIndexedNotNull null
                    val quantity = element.str("quantity")
                    val unit = element.str("unit")
                    IngredientEntity(
                        recipeId = 0,
                        position = i,
                        quantity = quantity,
                        unit = unit,
                        name = name,
                        rawText = listOfNotNull(quantity, unit, name)
                            .joinToString(" ")
                            .trim(),
                    )
                }
                else -> null
            }
        }
        val steps = (obj["steps"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }

        if (ingredients.isEmpty() && steps.isEmpty()) return null

        val nutritionObj = obj["nutrition"] as? JsonObject
        val nutrition = if (nutritionObj != null) {
            NutritionInfo(
                caloriesKcal = nutritionObj.double("caloriesKcal") ?: nutritionObj.double("calories"),
                proteinG = nutritionObj.double("proteinG") ?: nutritionObj.double("protein"),
                fatG = nutritionObj.double("fatG") ?: nutritionObj.double("fat"),
                carbsG = nutritionObj.double("carbsG") ?: nutritionObj.double("carbs") ?: nutritionObj.double("carbohydrates"),
            ).takeIf { it.caloriesKcal != null || it.proteinG != null || it.fatG != null || it.carbsG != null }
        } else null

        return StructuredRecipe(
            title = obj.str("title"),
            description = obj.str("description"),
            servings = obj.int("servings"),
            prepTimeMinutes = obj.int("prepTimeMinutes"),
            cookTimeMinutes = obj.int("cookTimeMinutes"),
            ingredients = ingredients,
            steps = steps,
            categories = Categories.canonicalise(
                (obj["tags"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
            ),
            nutrition = nutrition,
        )
    }

    /** Pulls the outermost JSON object out of whatever prose surrounds it. */
    private fun extractObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun JsonObject.int(key: String): Int? = str(key)?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 }

    private fun JsonObject.double(key: String): Double? = str(key)?.toDoubleOrNull()?.takeIf { it >= 0 }
}
