package com.delizioso.app.data.import

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class QuickMealData(
    val name: String,
    val caloriesKcal: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val carbsG: Double? = null,
)

object QuickMealJsonParser {

    fun parse(text: String, json: Json = Json { ignoreUnknownKeys = true }): QuickMealData? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end <= start) return null
        val body = text.substring(start, end + 1)
        val obj = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null

        fun num(key: String): Double? {
            val prim = obj[key] as? JsonPrimitive ?: return null
            return prim.content.replace(',', '.').toDoubleOrNull()
        }

        fun str(key: String): String? =
            (obj[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() && it != "null" }

        val name = str("name") ?: str("title") ?: return null
        return QuickMealData(
            name = name,
            caloriesKcal = num("caloriesKcal") ?: num("calories"),
            proteinG = num("proteinG") ?: num("protein"),
            fatG = num("fatG") ?: num("fat"),
            carbsG = num("carbsG") ?: num("carbs"),
        )
    }
}
