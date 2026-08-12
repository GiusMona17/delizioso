package com.delizioso.app.data.import

/**
 * Turns a block of pasted recipe text into sections, with no model call.
 *
 * [CaptionRecipeParser] handles the easy, common shape — text that declares its
 * own INGREDIENTI/PROCEDIMENTO headings — and is tried first because headings are
 * something a regex gets right every time. What is left is text with no structure
 * to lean on, so this falls back to the one signal that survives: an ingredient
 * line starts with an amount, an instruction is a sentence.
 *
 * It is deliberately allowed to be imperfect. The result lands in the editable
 * preview, where moving one line is trivial — far cheaper than an LLM pass that
 * paraphrases the whole thing to fix a single misplaced row.
 */
object PastedRecipeParser {

    /** "1. Boil the water", "2) Add salt" — a numbered instruction, not an amount. */
    private val STEP_MARKER = Regex("""^\s*\d{1,2}\s*[.)]\s+(.+)$""")

    /** "200 g", "2 cups", "½ tsp", "- 1 onion" — the line opens with an amount. */
    private val STARTS_WITH_AMOUNT = Regex("""^\s*[-*•·▪]?\s*(\d|[½⅓⅔¼¾⅕⅙⅛⅜⅝⅞])""")

    /** Hashtags, handles and "link in bio" noise that a paste often drags along. */
    private val NOISE = Regex(
        """^\s*[#@]|link in bio|segui(mi)?|follow (me|for)|iscriviti|subscribe""",
        RegexOption.IGNORE_CASE,
    )

    /** Below this many words a line is a shopping-list item, not an instruction. */
    private const val MIN_STEP_WORDS = 3

    fun parse(text: String): StructuredRecipe {
        CaptionRecipeParser.parse(text)?.let { return it }

        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !NOISE.containsMatchIn(it) && it.any(Char::isLetterOrDigit) }
        if (lines.isEmpty()) return StructuredRecipe()

        // The title is the first line only when it isn't already an amount or a step.
        val hasTitle = lines[0].let { !STARTS_WITH_AMOUNT.containsMatchIn(it) && STEP_MARKER.find(it) == null }
        val title = if (hasTitle) lines[0].take(120) else null

        val ingredients = mutableListOf<String>()
        val steps = mutableListOf<String>()
        for (line in lines.drop(if (hasTitle) 1 else 0)) {
            val numbered = STEP_MARKER.find(line)?.groupValues?.get(1)
            when {
                // A numbered line is an instruction even though it opens with a digit.
                numbered != null && numbered.wordCount() >= MIN_STEP_WORDS -> steps += numbered
                STARTS_WITH_AMOUNT.containsMatchIn(line) -> ingredients += line.stripBullet()
                line.wordCount() < MIN_STEP_WORDS -> ingredients += line.stripBullet()
                else -> steps += line.stripBullet()
            }
        }

        return StructuredRecipe(
            title = title,
            ingredients = ingredients.mapIndexed { i, line -> IngredientParser.split(line).copy(position = i) },
            steps = steps,
        )
    }

    private fun String.wordCount(): Int = split(Regex("""\s+""")).count { it.isNotBlank() }

    private fun String.stripBullet(): String = replace(Regex("""^\s*[-*•·▪]\s*"""), "").trim()
}
