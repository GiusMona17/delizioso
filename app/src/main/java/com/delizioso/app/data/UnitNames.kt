package com.delizioso.app.data

/**
 * Countable kitchen units, translated from a table rather than by a model.
 *
 * A unit arrives at the translator as a bare word with no sentence around it, and
 * NMT guesses badly at that: "2 cloves" of garlic came back as "2 chiodi di
 * garofano" — the spice. The set of units a recipe uses is small and closed, so a
 * lookup is both correct and free, which is the same reason
 * [UnitConverter] does the arithmetic instead of the model.
 *
 * Only Italian is filled in: it is the language this app is used in, and inventing
 * half-checked tables for languages nobody here reads would be worse than leaving
 * the word alone.
 */
object UnitNames {

    private val EN_TO_IT = mapOf(
        "clove" to "spicchio",
        "cloves" to "spicchi",
        "slice" to "fetta",
        "slices" to "fette",
        "piece" to "pezzo",
        "pieces" to "pezzi",
        "can" to "lattina",
        "cans" to "lattine",
        "jar" to "barattolo",
        "jars" to "barattoli",
        "bunch" to "mazzo",
        "bunches" to "mazzi",
        "handful" to "manciata",
        "handfuls" to "manciate",
        "pinch" to "pizzico",
        "pinches" to "pizzichi",
        "dash" to "spruzzata",
        "sprig" to "rametto",
        "sprigs" to "rametti",
        "stalk" to "gambo",
        "stalks" to "gambi",
        "stick" to "stecca",
        "sticks" to "stecche",
        "fillet" to "filetto",
        "fillets" to "filetti",
        "sheet" to "foglio",
        "sheets" to "fogli",
        "bag" to "busta",
        "bags" to "buste",
        "bottle" to "bottiglia",
        "bottles" to "bottiglie",
        "package" to "confezione",
        "packages" to "confezioni",
        "drop" to "goccia",
        "drops" to "gocce",
        "whole" to "intero",
        "large" to "grande",
        "medium" to "medio",
        "small" to "piccolo",
    )

    /**
     * The unit written in [targetLanguage], or unchanged when there is nothing to
     * say — a metric symbol, an unknown word, or a language without a table.
     */
    fun localize(unit: String?, targetLanguage: String): String? {
        if (unit.isNullOrBlank() || targetLanguage != "it") return unit
        val translated = EN_TO_IT[unit.lowercase().trim()] ?: return unit
        // "Cloves" at the start of a line should stay capitalised.
        return if (unit.first().isUpperCase()) translated.replaceFirstChar { it.uppercase() } else translated
    }
}
