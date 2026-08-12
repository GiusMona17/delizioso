package com.delizioso.app.data

import com.delizioso.app.data.import.IngredientParser
import com.delizioso.app.data.import.StructuredRecipe
import kotlin.math.roundToInt

/**
 * Imperial → metric, done in code.
 *
 * Deliberately not the model's job: converting cups and ounces is arithmetic
 * with fixed factors, and a small LLM gets it wrong (or silently leaves it
 * alone, which is what Gemma 3 1B did). Doing it here means the numbers are
 * exact and testable, and the model is left with the part it is actually good
 * at — translating and rewriting text whose quantities are already correct.
 */
object UnitConverter {

    /** One US cup, the reference volume the density table is written against. */
    private const val CUP_ML = 240.0

    /** Millilitres per imperial volume unit (US customary). */
    private val VOLUME_ML = mapOf(
        "cup" to 240.0,
        "cups" to 240.0,
        "tbsp" to 15.0,
        "tablespoon" to 15.0,
        "tablespoons" to 15.0,
        "tsp" to 5.0,
        "teaspoon" to 5.0,
        "teaspoons" to 5.0,
        "fl oz" to 30.0,
        "quart" to 950.0,
        "quarts" to 950.0,
        "pint" to 473.0,
        "pints" to 473.0,
        "gallon" to 3785.0,
    )

    /** Grams per imperial weight unit. */
    private val WEIGHT_G = mapOf(
        "oz" to 28.0,
        "ounce" to 28.0,
        "ounces" to 28.0,
        "lb" to 454.0,
        "lbs" to 454.0,
        "pound" to 454.0,
        "pounds" to 454.0,
    )

    /**
     * Grams in one cup of a dry ingredient. Volume-to-weight depends on what is
     * being measured, so only well-known staples are converted to grams; anything
     * else stays in millilitres, which is always true.
     */
    private val GRAMS_PER_CUP = mapOf(
        "flour" to 120.0,
        "farina" to 120.0,
        "sugar" to 200.0,
        "zucchero" to 200.0,
        "brown sugar" to 220.0,
        "rice" to 185.0,
        "riso" to 185.0,
        "butter" to 227.0,
        "burro" to 227.0,
        "oats" to 90.0,
        "avena" to 90.0,
        "breadcrumbs" to 108.0,
        "pangrattato" to 108.0,
        "parmesan" to 100.0,
        "parmigiano" to 100.0,
    )

    /** "2/3 cups", "1 1/2 tbsp", "10 oz" — amount plus imperial unit. */
    private val AMOUNT_UNIT = Regex(
        """(\d+\s+\d+/\d+|\d+/\d+|\d+(?:[.,]\d+)?)\s*""" +
            """(fl\.?\s?oz|cups?|tbsps?|tablespoons?|tsps?|teaspoons?|ounces?|oz|lbs?|pounds?|quarts?|pints?|gallons?)""" +
            """(?![\p{L}])""",
        RegexOption.IGNORE_CASE,
    )

    /** "350 F", "350°F", "350 degrees F". */
    private val FAHRENHEIT = Regex(
        """(\d{2,3})\s*(?:°\s*F\b|degrees?\s*F\b|F\b(?!\p{L}))""",
        RegexOption.IGNORE_CASE,
    )

    /** Converts every ingredient and step of [recipe]; metric input is untouched. */
    fun convert(recipe: StructuredRecipe): StructuredRecipe = recipe.copy(
        ingredients = recipe.ingredients.mapIndexed { index, ingredient ->
            val source = ingredient.rawText
                ?: listOfNotNull(ingredient.quantity, ingredient.unit, ingredient.name).joinToString(" ")
            IngredientParser.split(convertLine(source)).copy(position = index)
        },
        steps = recipe.steps.map(::convertLine),
    )

    /** Rewrites the imperial amounts in one line of free text. */
    fun convertLine(text: String): String {
        val withTemps = FAHRENHEIT.replace(text) { match ->
            val f = match.groupValues[1].toDouble()
            // Ovens are set in tens: 350F->180, 400F->200, 425F->220.
            "${round((f - 32.0) * 5.0 / 9.0, step = 10)} °C"
        }
        return AMOUNT_UNIT.replace(withTemps) { match ->
            val amount = Quantities.parse(match.groupValues[1]) ?: return@replace match.value
            val unit = match.groupValues[2].lowercase().replace(Regex("""\s+"""), " ")
            convertAmount(amount, unit, withTemps) ?: match.value
        }
    }

    private fun convertAmount(amount: Double, unit: String, context: String): String? {
        WEIGHT_G[unit]?.let { grams -> return "${round(amount * grams)} g" }

        val millilitres = VOLUME_ML[unit] ?: VOLUME_ML[unit.trimEnd('s')] ?: return null
        // A cup of flour is not a cup of milk: use grams only for staples we know.
        // This applies to spoons as well — "2 tbsp sugar" is 25 g, not 30 ml.
        gramsPerCup(context)?.let { perCup ->
            return "${round(amount * millilitres * perCup / CUP_ML)} g"
        }
        return "${round(amount * millilitres)} ml"
    }

    private fun gramsPerCup(context: String): Double? {
        val lower = context.lowercase()
        // Longest name first, so "brown sugar" beats "sugar".
        return GRAMS_PER_CUP.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { lower.contains(it.key) }
            ?.value
    }

    /** Kitchen-friendly rounding: no one measures 158.4 ml. */
    private fun round(value: Double, step: Int = 5): String {
        if (value < 10) return (value * 10).roundToInt().div(10.0).toString().removeSuffix(".0")
        return ((value / step).roundToInt() * step).toString()
    }
}
