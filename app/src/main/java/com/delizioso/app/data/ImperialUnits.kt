package com.delizioso.app.data

import com.delizioso.app.data.import.StructuredRecipe

/**
 * Spots the units an Italian kitchen has no feel for — cups, ounces, °F.
 *
 * Detection is deterministic on purpose: it decides whether to *offer* the AI
 * rewrite, so it has to be instant and never wrong about the offer itself.
 * The conversion is the model's job; this only answers "is it worth asking?".
 */
object ImperialUnits {

    private val MARKERS = Regex(
        """(?<![\p{L}])(cups?|oz|ounces?|fl\.?\s?oz|lbs?|pounds?|tbsp|tablespoons?|tsp|teaspoons?|sticks?\s+of\s+butter|°?\s?F\b|fahrenheit|quarts?|pints?|gallons?|inch(es)?)(?![\p{L}])""",
        RegexOption.IGNORE_CASE,
    )

    fun isPresentIn(text: String): Boolean = MARKERS.containsMatchIn(text)

    /** True when any ingredient or step uses an imperial unit. */
    fun isPresentIn(recipe: StructuredRecipe): Boolean {
        val haystack = buildString {
            recipe.ingredients.forEach { appendLine(it.rawText ?: "${it.quantity.orEmpty()} ${it.unit.orEmpty()} ${it.name}") }
            recipe.steps.forEach { appendLine(it) }
        }
        return isPresentIn(haystack)
    }
}
