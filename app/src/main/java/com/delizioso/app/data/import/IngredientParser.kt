package com.delizioso.app.data.import

import com.delizioso.app.data.local.IngredientEntity

/**
 * Pragmatic ingredient line splitter supporting both:
 * 1. Standard leading quantity: "320 g spaghetti", "2 cups flour", "3 uova", "150g guanciale"
 * 2. Italian trailing quantity: "Spaghetti 320 g", "Guanciale 150g", "Tuorli 6", "Pecorino Romano 50 gr"
 * 3. Qualitative expressions: "Sale fino q.b.", "Pepe a piacere", "Salt to taste"
 */
object IngredientParser {

    private val UNITS = setOf(
        "tbsp", "tablespoon", "tablespoons", "tsp", "teaspoon", "teaspoons",
        "cup", "cups", "oz", "ounce", "ounces", "lb", "lbs", "pound", "pounds",
        "g", "gr", "gram", "grams", "grammo", "grammi", "kg", "chilo", "chili", "etto", "etti", "hg",
        "ml", "l", "liter", "liters", "litro", "litri", "dl", "cl", "cc",
        "clove", "cloves", "sprig", "sprigs", "pinch", "pinches", "dash", "splash", "slice", "slices",
        "can", "cans", "bunch", "bunches", "stick", "sticks", "package", "packages",
        "bottle", "bottles", "handful", "handfuls", "drop", "drops", "piece", "pieces",
        "bag", "bags", "jar", "jars", "sheet", "sheets", "fillet", "fillets",
        "stalk", "stalks", "medium", "large", "small", "whole", "fresh", "packed",
        // Italian kitchen units
        "cucchiaio", "cucchiai", "cucchiaino", "cucchiaini",
        "spicchio", "spicchi", "pizzico", "pizzichi",
        "fetta", "fette", "rametto", "rametti", "foglia", "foglie",
        "bicchiere", "bicchieri", "tazza", "tazze", "bustina", "bustine",
        "barattolo", "barattoli", "lattina", "lattine",
        "vasetto", "vasetti", "manciata", "manciate", "mazzo", "mazzi",
        "filo", "goccia", "gocce", "scatoletta", "scatolette",
    )

    private val LEADING_PREPOSITION = Regex(
        """^(?:(?:di\s+|d['’]|del(?:la|lo|l'|l’)?\s+|degli\s+|dei\s+|delle\s+|of\s+)\s*)+""",
        RegexOption.IGNORE_CASE,
    )

    // Matches "q.b.", "quanto basta", "a piacere", "to taste" at the end of an ingredient line
    private val TRAILING_QB = Regex(
        """^(.*?)(?:\s*[-–—:]\s*|\s+)(?:q\.b\.?|quanto basta|a piacere|to taste|qb)\.?$""",
        RegexOption.IGNORE_CASE
    )

    // Matches leading quantity: "320 g spaghetti", "150g guanciale", "1/2 cup milk", "3 eggs"
    private val LEADING_QUANTITY = Regex(
        """^([\d⅓½⅔¼¾⅕⅙⅛⅜⅝⅞.,/+-]+)(.*)$"""
    )

    // Matches trailing quantity: "Spaghetti 320 g", "Farina 00 200g", "Tuorli 6", "Pecorino 50 gr"
    private val TRAILING_QUANTITY = Regex(
        """^(.*?)\s+([\d⅓½⅔¼¾⅕⅙⅛⅜⅝⅞.,/+-]+)\s*([a-zA-Zàèéìòù]+)?$"""
    )

    fun split(rawText: String): IngredientEntity {
        val text = rawText.trim()
        if (text.isEmpty()) return IngredientEntity(recipeId = 0, position = 0, name = "", rawText = text)

        // 1. Check for "q.b." / "to taste"
        val qbMatch = TRAILING_QB.find(text)
        if (qbMatch != null) {
            val name = cleanName(qbMatch.groupValues[1])
            if (name.isNotEmpty()) {
                return IngredientEntity(
                    recipeId = 0,
                    position = 0,
                    quantity = null,
                    unit = "q.b.",
                    name = name,
                    rawText = text,
                )
            }
        }

        // 2. Check for leading quantity: "320 g spaghetti", "150g guanciale", "2 eggs", "3 uova"
        val leadingMatch = LEADING_QUANTITY.find(text)
        if (leadingMatch != null) {
            val q = leadingMatch.groupValues[1].trim()
            val rest = leadingMatch.groupValues[2].trim()

            if (q.isNotEmpty() && rest.isNotEmpty()) {
                val firstToken = rest.substringBefore(' ').trimEnd(',', '.')
                val remainder = rest.substringAfter(' ', "").trim()

                if (isUnit(firstToken)) {
                    val name = cleanName(remainder)
                    return IngredientEntity(
                        recipeId = 0,
                        position = 0,
                        quantity = q,
                        unit = firstToken,
                        name = if (name.isNotEmpty()) name else rest,
                        rawText = text,
                    )
                } else {
                    // No recognized unit, e.g. "2 eggs", "3 uova", "1 sprig fresh dill"
                    val name = cleanName(rest)
                    return IngredientEntity(
                        recipeId = 0,
                        position = 0,
                        quantity = q,
                        unit = null,
                        name = if (name.isNotEmpty()) name else rest,
                        rawText = text,
                    )
                }
            }
        }

        // 3. Check for trailing quantity: "Spaghetti 320 g", "Guanciale 150g", "Tuorli 6"
        val trailingMatch = TRAILING_QUANTITY.find(text)
        if (trailingMatch != null) {
            val leadName = trailingMatch.groupValues[1].trim()
            val q = trailingMatch.groupValues[2].trim()
            val unitCandidate = trailingMatch.groupValues[3].trim()

            if (leadName.isNotEmpty() && q.isNotEmpty()) {
                val unit = if (unitCandidate.isNotEmpty() && isUnit(unitCandidate)) {
                    unitCandidate
                } else if (unitCandidate.isEmpty()) {
                    null
                } else {
                    null
                }

                if (unitCandidate.isEmpty() || unit != null) {
                    val name = cleanName(leadName)
                    return IngredientEntity(
                        recipeId = 0,
                        position = 0,
                        quantity = q,
                        unit = unit,
                        name = if (name.isNotEmpty()) name else leadName,
                        rawText = text,
                    )
                }
            }
        }

        // Fallback: unparsed line
        return IngredientEntity(
            recipeId = 0,
            position = 0,
            quantity = null,
            unit = null,
            name = cleanName(text).ifEmpty { text },
            rawText = text,
        )
    }

    private fun isUnit(token: String): Boolean =
        UNITS.contains(token.lowercase().trimEnd(',', '.'))

    private fun cleanName(rawName: String): String =
        rawName.replace(LEADING_PREPOSITION, "").trim()
}
