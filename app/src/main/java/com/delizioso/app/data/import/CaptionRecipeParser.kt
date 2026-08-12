package com.delizioso.app.data.import

/**
 * Reads a recipe out of a caption by its own section headings, with no model call.
 *
 * Social recipe captions are overwhelmingly written as
 * `INGREDIENTS: … / INSTRUCTIONS: …`, and headings are something a regex gets
 * right every time. Gemini Nano, by contrast, has to *count lines* to point at
 * them and a small model miscounts — which lands macros and ingredients in the
 * method section. So this runs first and the model is the fallback for captions
 * that have no headings at all.
 */
object CaptionRecipeParser {

    private val INGREDIENT_HEADING = Regex(
        """^\s*[^\p{L}\d]{0,3}\s*(ingredient(s|i)?|ingredienti|occorrente|serve|servono)\b.*$""",
        RegexOption.IGNORE_CASE,
    )

    private val STEP_HEADING = Regex(
        """^\s*[^\p{L}\d]{0,3}\s*(instruction(s)?|direction(s)?|method|steps?|preparation|preparazione|procedimento|esecuzione|how to make)\b.*$""",
        RegexOption.IGNORE_CASE,
    )

    /** Nutrition lines that sit between the title and the ingredients. */
    private val MACRO_LINE = Regex(
        """^\s*(macros?|protein|proteine|carb(s|ohydrates)?|carboidrati|fat|grassi|calor(ie|ies|ien)|kcal)\b\s*[:\-]?.*$""",
        RegexOption.IGNORE_CASE,
    )

    /** "Follow for more", "full recipe below", "link in bio" — never recipe content. */
    private val CALL_TO_ACTION = Regex(
        """(full recipe|follow|link in bio|comment|save this|share this|ricetta completa|seguimi|salva)""",
        RegexOption.IGNORE_CASE,
    )

    /** A short line ending in ':' is a sub-heading ("Toppings:", "Sauce:"). */
    private val SUB_HEADING = Regex("""^.{0,28}:\s*$""")

    private val SERVINGS = Regex(
        """(?:per|for|serves|servings?|porzioni|persone)\s*:?\s*(\d+)|(\d+)\s*(?:servings?|porzioni|persone)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Returns the recipe when the caption declares both an ingredients and a
     * method section, otherwise null so the caller can fall back to the model.
     */
    fun parse(text: String): StructuredRecipe? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val ingredientsAt = lines.indexOfFirst { INGREDIENT_HEADING.matches(it) }
        val stepsAt = lines.indexOfFirst { STEP_HEADING.matches(it) }
        if (ingredientsAt == -1 || stepsAt == -1 || stepsAt <= ingredientsAt) return null

        val ingredients = lines
            .subList(ingredientsAt + 1, stepsAt)
            .filter { it.isContent() }
        val steps = lines
            .drop(stepsAt + 1)
            .filter { it.isContent() }
            .map { it.stripMarker() }
        if (ingredients.isEmpty() || steps.isEmpty()) return null

        return StructuredRecipe(
            title = title(lines, ingredientsAt),
            servings = SERVINGS.find(lines[ingredientsAt])?.let { m ->
                (m.groupValues[1].ifBlank { m.groupValues[2] }).toIntOrNull()
            },
            ingredients = ingredients.mapIndexed { i, line ->
                IngredientParser.split(line.stripMarker()).copy(position = i)
            },
            steps = steps,
        )
    }

    /** The first line above the ingredients that reads like a dish name. */
    private fun title(lines: List<String>, ingredientsAt: Int): String? =
        lines.take(ingredientsAt)
            .firstOrNull { it.isContent() && it.length in 3..80 && !it.startsWith("@") }
            ?.stripMarker()

    private fun String.isContent(): Boolean =
        isNotBlank() &&
            !MACRO_LINE.matches(this) &&
            !CALL_TO_ACTION.containsMatchIn(this) &&
            !SUB_HEADING.matches(this) &&
            !startsWith("#") &&
            // A line that is nothing but hashtags/emoji carries no recipe content.
            any { it.isLetterOrDigit() }

    private fun String.stripMarker(): String =
        replace(Regex("""^\s*(?:\d+[.)]\s*|[-*•·▪]\s*)"""), "").trim()
}
