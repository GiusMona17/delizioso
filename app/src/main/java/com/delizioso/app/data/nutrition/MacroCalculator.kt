package com.delizioso.app.data.nutrition

import com.delizioso.app.data.Quantities
import com.delizioso.app.data.import.StructuredRecipe
import com.delizioso.app.data.local.IngredientEntity

/**
 * Adds up a recipe's macros from [NutritionTable], in code.
 *
 * Every number is the sum of amounts the user can see and correct, and the
 * result reports how many ingredients it actually recognised — so a partial
 * match is visible rather than hidden behind a confident total.
 */
object MacroCalculator {

    /** Totals for one serving, plus how much of the recipe they account for. */
    data class Macros(
        val kcal: Double,
        val proteinG: Double,
        val fatG: Double,
        val carbsG: Double,
        val matched: Int,
        val total: Int,
        /** False when the recipe never said how many servings it makes. */
        val perServing: Boolean,
        /**
         * Ingredients the table didn't know, named so the user can see exactly
         * what is missing from the total and rename it if it was a typo.
         */
        val unmatched: List<String> = emptyList(),
    )

    /** Below this share of recognised ingredients the total is not worth showing. */
    private const val MIN_COVERAGE = 0.6

    private val GRAMS_PER_UNIT = mapOf(
        "g" to 1.0, "gr" to 1.0, "gram" to 1.0, "grams" to 1.0,
        "grammo" to 1.0, "grammi" to 1.0,
        "kg" to 1000.0, "chilo" to 1000.0, "chili" to 1000.0,
        "oz" to 28.0, "ounce" to 28.0, "ounces" to 28.0,
        "lb" to 454.0, "lbs" to 454.0, "pound" to 454.0, "pounds" to 454.0,
        "pinch" to 0.5, "pizzico" to 0.5, "dash" to 0.5,
    )

    private val ML_PER_UNIT = mapOf(
        "ml" to 1.0, "millilitre" to 1.0, "millilitri" to 1.0,
        "cl" to 10.0, "dl" to 100.0,
        "l" to 1000.0, "litro" to 1000.0, "litri" to 1000.0, "liter" to 1000.0, "liters" to 1000.0,
        "tbsp" to 15.0, "tablespoon" to 15.0, "tablespoons" to 15.0,
        "cucchiaio" to 15.0, "cucchiai" to 15.0,
        "tsp" to 5.0, "teaspoon" to 5.0, "teaspoons" to 5.0,
        "cucchiaino" to 5.0, "cucchiaini" to 5.0,
        "cup" to 240.0, "cups" to 240.0,
    )

    /** Units that mean "one of these", weighed via [NutritionTable.Nutrient.gramsPerPiece]. */
    private val PIECE_UNITS = setOf(
        "", "piece", "pieces", "clove", "cloves", "spicchio", "spicchi",
        "large", "medium", "small", "whole", "intero", "intera",
    )

    fun of(recipe: StructuredRecipe): Macros? = of(recipe.ingredients, recipe.servings)

    fun of(ingredients: List<IngredientEntity>, servingCount: Int?): Macros? {
        if (ingredients.isEmpty()) return null
        var kcal = 0.0
        var protein = 0.0
        var fat = 0.0
        var carbs = 0.0
        var matched = 0
        val unmatched = mutableListOf<String>()

        for (ingredient in ingredients) {
            val nutrient = NutritionTable.lookup(ingredient.name)
            if (nutrient == null) {
                unmatched += ingredient.name
                continue
            }
            val grams = gramsOf(ingredient, nutrient)
            if (grams == null) {
                // "salt to taste" carries no amount and no calories: counting it as
                // unrecognised would understate coverage for no gain.
                if (nutrient.kcal == 0.0) matched++ else unmatched += ingredient.name
                continue
            }
            matched++
            val hundreds = grams / 100.0
            kcal += nutrient.kcal * hundreds
            protein += nutrient.proteinG * hundreds
            fat += nutrient.fatG * hundreds
            carbs += nutrient.carbsG * hundreds
        }

        val total = ingredients.size
        if (matched < 2 || matched.toDouble() / total < MIN_COVERAGE) return null

        val servings = servingCount?.takeIf { it > 0 }
        val divisor = servings ?: 1
        return Macros(
            kcal = kcal / divisor,
            proteinG = protein / divisor,
            fatG = fat / divisor,
            carbsG = carbs / divisor,
            matched = matched,
            total = total,
            perServing = servings != null,
            unmatched = unmatched,
        )
    }

    /** Weight in grams of one ingredient line, or null when the amount is unusable. */
    private fun gramsOf(ingredient: IngredientEntity, nutrient: NutritionTable.Nutrient): Double? {
        val amount = ingredient.quantity?.let(Quantities::parse) ?: return null
        val unit = ingredient.unit.orEmpty().lowercase().trim().trimEnd('.', ',')
        GRAMS_PER_UNIT[unit]?.let { return amount * it }
        ML_PER_UNIT[unit]?.let { return amount * it * nutrient.gramsPerMl }
        if (unit in PIECE_UNITS) return nutrient.gramsPerPiece?.times(amount)
        return null
    }
}
