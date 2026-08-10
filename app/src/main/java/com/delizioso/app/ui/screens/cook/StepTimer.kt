package com.delizioso.app.ui.screens.cook

/**
 * Finds the duration a cooking step talks about ("simmer for 3 minutes",
 * "30 seconds", "1 hour") so the step can offer a countdown.
 * Returns whole seconds, or null when the step is not timed.
 */
object StepTimer {

    private val PATTERN = Regex(
        """(\d+(?:\.\d+)?)\s*(?:-\s*\d+\s*)?(hours?|hrs?|minutes?|mins?|seconds?|secs?)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun parseSeconds(text: String): Int? {
        val match = PATTERN.find(text) ?: return null
        val amount = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].lowercase()
        val seconds = when {
            unit.startsWith("h") -> amount * 3600
            unit.startsWith("m") -> amount * 60
            else -> amount
        }
        return seconds.toInt().takeIf { it in 1..(6 * 3600) }
    }

    fun format(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        return "%02d:%02d".format(safe / 60, safe % 60)
    }
}
