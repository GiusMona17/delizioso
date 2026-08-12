package com.delizioso.app.data.export

import com.delizioso.app.data.Quantities
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.data.nutrition.MacroCalculator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Renders a saved recipe for pasting into an external assistant.
 *
 * This is the app's answer to "what about allergens, substitutions, wine
 * pairings?" — questions that need a big model and a lot of world knowledge,
 * which is exactly what an on-device 1B is worst at. Rather than pretend, the
 * recipe leaves in a shape a real assistant reads well and the user asks there.
 *
 * Section labels are English on purpose: the destination is a language model, and
 * English structural markers are the most reliably parsed. The recipe's own words
 * are untouched, in whatever language it was written.
 */
object RecipeExport {

    private val json = Json { prettyPrint = true }

    fun toMarkdown(details: RecipeWithDetails, macros: MacroCalculator.Macros? = null): String = buildString {
        val recipe = details.recipe
        appendLine("# ${recipe.title}")
        recipe.description?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine(it)
        }

        val facts = listOfNotNull(
            recipe.servings?.let { "Servings: $it" },
            recipe.prepTimeMinutes?.let { "Prep: $it min" },
            recipe.cookTimeMinutes?.let { "Cook: $it min" },
            details.tags.takeIf { it.isNotEmpty() }?.let { tags -> "Tags: " + tags.joinToString(", ") { it.name } },
        )
        if (facts.isNotEmpty()) {
            appendLine()
            facts.forEach { appendLine("- $it") }
        }

        appendLine()
        appendLine("## Ingredients")
        details.ingredients.sortedBy { it.position }.forEach { appendLine("- ${it.line()}") }

        appendLine()
        appendLine("## Instructions")
        details.steps.sortedBy { it.position }.forEachIndexed { i, step -> appendLine("${i + 1}. ${step.text}") }

        macros?.let {
            appendLine()
            appendLine("## Nutrition (calculated, ${if (it.perServing) "per serving" else "whole recipe"})")
            appendLine(
                "${it.kcal.toInt()} kcal · ${it.proteinG.toInt()} g protein · " +
                    "${it.fatG.toInt()} g fat · ${it.carbsG.toInt()} g carbs " +
                    "(from ${it.matched} of ${it.total} ingredients)"
            )
        }

        details.source?.url?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine("Source: $it")
        }
    }.trimEnd()

    fun toJson(details: RecipeWithDetails, macros: MacroCalculator.Macros? = null): String {
        val recipe = details.recipe
        val fields = buildMap {
            put("title", JsonPrimitive(recipe.title))
            put("description", recipe.description.orJsonNull())
            put("servings", recipe.servings?.let(::JsonPrimitive) ?: JsonNull)
            put("prepTimeMinutes", recipe.prepTimeMinutes?.let(::JsonPrimitive) ?: JsonNull)
            put("cookTimeMinutes", recipe.cookTimeMinutes?.let(::JsonPrimitive) ?: JsonNull)
            put("tags", JsonArray(details.tags.map { JsonPrimitive(it.name) }))
            put(
                "ingredients",
                JsonArray(
                    details.ingredients.sortedBy { it.position }.map { ingredient ->
                        JsonObject(
                            mapOf(
                                "quantity" to ingredient.quantity.orJsonNull(),
                                "unit" to ingredient.unit.orJsonNull(),
                                "name" to JsonPrimitive(ingredient.name),
                            )
                        )
                    }
                )
            )
            put("steps", JsonArray(details.steps.sortedBy { it.position }.map { JsonPrimitive(it.text) }))
            put("sourceUrl", details.source?.url.orJsonNull())
            macros?.let {
                put(
                    "nutrition",
                    JsonObject(
                        mapOf(
                            "basis" to JsonPrimitive(if (it.perServing) "per_serving" else "whole_recipe"),
                            "calculated" to JsonPrimitive(true),
                            "kcal" to JsonPrimitive(it.kcal.toInt()),
                            "proteinG" to JsonPrimitive(it.proteinG.toInt()),
                            "fatG" to JsonPrimitive(it.fatG.toInt()),
                            "carbsG" to JsonPrimitive(it.carbsG.toInt()),
                            "ingredientsRecognised" to JsonPrimitive(it.matched),
                            "ingredientsTotal" to JsonPrimitive(it.total),
                        )
                    )
                )
            }
        }
        return json.encodeToString(JsonObject.serializer(), JsonObject(fields))
    }

    private fun String?.orJsonNull() =
        if (isNullOrBlank()) JsonNull else JsonPrimitive(this)

    private fun com.delizioso.app.data.local.IngredientEntity.line(): String =
        rawText?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(quantity?.let(Quantities::parse)?.let(Quantities::format) ?: quantity, unit, name)
                .joinToString(" ")
                .trim()
}
