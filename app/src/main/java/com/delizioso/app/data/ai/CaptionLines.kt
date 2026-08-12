package com.delizioso.app.data.ai

/**
 * Turns a caption into numbered lines and resolves the line references the model
 * answers with.
 *
 * AICore caps one answer at [NanoInference.MAX_OUTPUT_TOKENS] (~900 characters),
 * so a model that echoes every ingredient and step back as JSON truncates on any
 * real recipe. Asking instead for *which lines* are ingredients and which are
 * steps keeps the answer tiny no matter how long the recipe is — and the text
 * that lands in the app is the author's own wording, not the model's paraphrase.
 */
object CaptionLines {

    /** Longest caption we number; beyond this the line list itself crowds the prompt. */
    const val MAX_LINES = 60

    /** Non-blank caption lines, trimmed of bullets and numbering noise. */
    fun split(text: String): List<String> = text
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(MAX_LINES)

    /** "1. flour\n2. water" — the numbering the model refers back to. */
    fun numbered(lines: List<String>): String =
        lines.mapIndexed { index, line -> "${index + 1}. $line" }.joinToString("\n")

    /**
     * Resolves the model's references to actual lines.
     *
     * Accepts plain indices (`3`), ranges (`"4-9"`, `"4–9"`) and, when the model
     * ignores the instruction and answers with the text itself, the text verbatim.
     * Out-of-range indices are dropped rather than trusted.
     */
    fun resolve(lines: List<String>, references: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (reference in references) {
            val token = reference.trim()
            if (token.isEmpty()) continue
            val range = RANGE.matchEntire(token)
            when {
                range != null -> {
                    val from = range.groupValues[1].toInt()
                    val to = range.groupValues[2].toInt()
                    for (i in minOf(from, to)..maxOf(from, to)) lines.getOrNull(i - 1)?.let(out::add)
                }
                token.toIntOrNull() != null -> lines.getOrNull(token.toInt() - 1)?.let(out::add)
                // The model answered with content instead of a reference — keep it.
                else -> out.add(token)
            }
        }
        // Captions bullet and number their lines; the app renders its own markers.
        return out.map { stripLeadingMarker(it) }.filter { it.isNotEmpty() }.distinct()
    }

    /** Drops "1. ", "- ", "• " so a resolved line reads as plain content. */
    private fun stripLeadingMarker(line: String): String =
        line.replace(LEADING_MARKER, "").trim()

    private val RANGE = Regex("""(\d+)\s*[-–—]\s*(\d+)""")
    private val LEADING_MARKER = Regex("""^\s*(?:\d+[.)]\s*|[-*•·]\s*)""")
}
