package com.delizioso.app.data

/** Parsing and formatting of the free-text ingredient amounts we get from imports. */
object Quantities {

    private val FRACTIONS = mapOf(
        '½' to 0.5, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '¼' to 0.25, '¾' to 0.75,
        '⅕' to 0.2, '⅖' to 0.4, '⅗' to 0.6, '⅘' to 0.8, '⅙' to 1.0 / 6,
        '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875,
    )

    private val TOKEN = Regex("""^(\d+)(?:/(\d+))?$""")

    /** "1.5", "1,5" — a decimal, which is not the fraction "1/5". */
    private val DECIMAL = Regex("""^\d+[.,]\d+$""")

    /** "2", "1/2", "1 1/2", "1.5", "1½" → Double; null when not numeric. */
    fun parse(quantity: String): Double? {
        var total = 0.0
        var any = false
        for (c in quantity) {
            FRACTIONS[c]?.let {
                total += it
                any = true
            }
        }
        for (token in quantity.filterNot { it in FRACTIONS }.split(Regex("""\s+"""))) {
            if (token.isEmpty()) continue
            if (DECIMAL.matches(token)) {
                total += token.replace(',', '.').toDouble()
                any = true
                continue
            }
            val m = TOKEN.matchEntire(token) ?: return null
            val whole = m.groupValues[1].toDouble()
            val denom = m.groupValues[2].toDoubleOrNull()
            total += if (denom != null) whole / denom else whole
            any = true
        }
        return if (any) total else null
    }

    /** Two decimals at most, and no trailing ".0". */
    fun format(value: Double): String {
        val rounded = Math.round(value * 100) / 100.0
        return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
    }

    /**
     * Units that scale with the batch. Times and temperatures are deliberately
     * absent: doubling a recipe does not double "bake for 20 minutes" or turn
     * 180 °C into 360 °C, and silently doing so would ruin the dish.
     */
    private val SCALABLE_UNITS = setOf(
        "g", "gr", "grammi", "grammo", "gram", "grams", "kg", "chilo", "chili",
        "ml", "cl", "dl", "l", "litro", "litri", "liter", "liters",
        "oz", "lb", "lbs", "cup", "cups", "tbsp", "tsp",
        "cucchiai", "cucchiaio", "cucchiaini", "cucchiaino",
        "spicchi", "spicchio", "fette", "fetta", "uova", "uovo",
    )

    /** "200 g", "2 cucchiai", "1/2 cup" — an amount followed by one of the above. */
    private val AMOUNT_IN_TEXT = Regex(
        """(\d+\s+\d+/\d+|\d+/\d+|\d+(?:[.,]\d+)?)(\s*)(\p{L}+)\b""",
    )

    /**
     * Scales the amounts written inside a step's text.
     *
     * The servings stepper already scales the ingredient list, which left the
     * method contradicting it — "add 200 g of flour" under an ingredient line
     * reading 400 g. Only quantities carrying a unit of measure are touched, so
     * minutes, degrees and plain counts survive.
     */
    fun scaleInText(text: String, factor: Double): String {
        if (factor == 1.0) return text
        return AMOUNT_IN_TEXT.replace(text) { match ->
            val unit = match.groupValues[3]
            if (unit.lowercase() !in SCALABLE_UNITS) return@replace match.value
            val amount = parse(match.groupValues[1]) ?: return@replace match.value
            format(amount * factor) + match.groupValues[2] + unit
        }
    }

    /**
     * Scales a quantity by [factor], keeping non-numeric amounts ("a pinch") untouched.
     * Returns null when the input was null.
     */
    fun scale(quantity: String?, factor: Double): String? {
        if (quantity.isNullOrBlank()) return quantity
        if (factor == 1.0) return quantity
        val parsed = parse(quantity) ?: return quantity
        return format(parsed * factor)
    }
}
