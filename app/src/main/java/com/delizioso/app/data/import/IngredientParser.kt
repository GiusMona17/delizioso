package com.delizioso.app.data.import

import com.delizioso.app.data.local.IngredientEntity

/**
 * Pragmatic ingredient line splitter: "2 cups flour" → quantity "2", unit "cups",
 * name "flour". Falls back to rawText when the line doesn't fit the shape.
 */
object IngredientParser {

    private val QUANTITY = Regex("""^([\d⅓½⅔¼¾⅕⅙⅛⅜⅝⅞.,\s/+-]+?)(?:\s+|\s*$)(.*)$""")

    private val UNITS = setOf(
        "tbsp", "tablespoon", "tablespoons", "tsp", "teaspoon", "teaspoons",
        "cup", "cups", "oz", "ounce", "ounces", "lb", "lbs", "pound", "pounds",
        "g", "gram", "grams", "kg", "ml", "l", "liter", "liters", "liter",
        "clove", "cloves", "pinch", "pinches", "dash", "splash", "slice", "slices",
        "can", "cans", "bunch", "bunches", "stick", "sticks", "package", "packages",
        "bottle", "bottles", "handful", "handfuls", "drop", "drops", "piece", "pieces",
        "bag", "bags", "jar", "jars", "sheet", "sheets", "fillet", "fillets",
        "stalk", "stalks", "medium", "large", "small", "whole", "fresh", "packed",
    )

    fun split(rawText: String): IngredientEntity {
        val text = rawText.trim()
        if (text.isEmpty()) return IngredientEntity(recipeId = 0, position = 0, name = "", rawText = text)

        val match = QUANTITY.find(text)
        var quantity: String? = null
        var unit: String? = null
        var name = text

        if (match != null) {
            val q = match.groupValues[1].trim()
            val rest = match.groupValues[2].trim()
            if (q.isNotBlank() && rest.isNotBlank()) {
                quantity = q
                // Optional unit token follows the quantity.
                val unitToken = rest.substringBefore(' ')
                if (unitToken.isNotBlank() && UNITS.contains(unitToken.lowercase().trimEnd(',', '.'))) {
                    unit = unitToken.trimEnd(',', '.')
                    name = rest.substringAfter(' ', "").trim()
                } else {
                    name = rest
                }
            }
        }
        if (name.isEmpty()) name = text
        return IngredientEntity(
            recipeId = 0,
            position = 0,
            quantity = quantity,
            unit = unit,
            name = name,
            rawText = text,
        )
    }
}
