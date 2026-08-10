package com.delizioso.app.data

/** Parsing and formatting of the free-text ingredient amounts we get from imports. */
object Quantities {

    private val FRACTIONS = mapOf(
        '½' to 0.5, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '¼' to 0.25, '¾' to 0.75,
        '⅕' to 0.2, '⅖' to 0.4, '⅗' to 0.6, '⅘' to 0.8, '⅙' to 1.0 / 6,
        '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875,
    )

    private val TOKEN = Regex("""^(\d+)(?:[./](\d+))?$""")

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
