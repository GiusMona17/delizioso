package com.delizioso.app.data.docs

import com.delizioso.app.data.local.MealSlot
import com.delizioso.app.data.local.PantryItemEntity
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** The three documents, as path to text. Pure and deterministic. */
object DeliziosoDocsBuilder {
    const val FORMAT = "delizioso-docs/1"
    const val AGENDA_FORMAT = "agenda/1"
    private const val NOW_DAYS = 7L

    fun build(input: DeliziosoDocsInput): Map<String, String> {
        val items = DeliziosoAgenda.build(input)
        val generatedAt = input.generatedAt.truncatedTo(ChronoUnit.SECONDS).toString()
        return mapOf(
            "manifest.json" to manifest(input, items.size, generatedAt),
            "now.md" to nowMd(input),
            "agenda.json" to agenda(items, generatedAt),
        ).mapValues { it.value.replace("\r\n", "\n") }
    }

    private fun manifest(input: DeliziosoDocsInput, itemCount: Int, generatedAt: String): String =
        JSONObject()
            .put("format", FORMAT)
            .put("generatedAt", generatedAt)
            .put("timezone", input.timezone)
            .put("counts", JSONObject().put("agendaItems", itemCount))
            .toString(2) + "\n"

    private fun agenda(items: List<AgendaItem>, generatedAt: String): String =
        JSONObject()
            .put("format", AGENDA_FORMAT)
            .put("generatedAt", generatedAt)
            .put(
                "items",
                JSONArray(
                    items.map {
                        JSONObject()
                            .put("date", it.date.toString())
                            .put("kind", it.kind)
                            .put("title", it.title)
                            .put("detail", it.detail ?: JSONObject.NULL)
                            .put("overdue", it.overdue)
                            .put("data", it.data)
                    },
                ),
            )
            .toString(2) + "\n"

    private fun nowMd(input: DeliziosoDocsInput): String = buildString {
        val today = input.today
        appendLine("# Delizioso - $today")
        appendLine()
        appendLine("The owner's meal plan, shopping list and pantry, written for an assistant. Dates are local (${input.timezone}).")

        appendLine()
        appendLine("## Today ($today)")
        appendLine()
        mealBullets(input, today).forEach { appendLine(it) }

        appendLine()
        appendLine("## Tomorrow (${today.plusDays(1)})")
        appendLine()
        mealBullets(input, today.plusDays(1)).forEach { appendLine(it) }

        appendLine()
        appendLine("## Next 7 days")
        appendLine()
        appendLine("| Date | Weekday | Meals |")
        appendLine("|---|---|---|")
        for (offset in 0 until NOW_DAYS) {
            val day = today.plusDays(offset)
            val meals = mealsOn(input, day)
                .joinToString("; ") { "${DeliziosoAgenda.slotLabel(it)}: ${it.title}" }
                .ifEmpty { "nothing planned" }
            appendLine("| $day | ${day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} | ${cell(meals)} |")
        }

        appendLine()
        if (input.shopping.isEmpty()) {
            appendLine("## Shopping list")
            appendLine()
            appendLine("Nothing to buy.")
        } else {
            appendLine("## Shopping list (${DeliziosoAgenda.toBuy(input.shopping.size)})")
            appendLine()
            input.shopping.forEach { appendLine("- ${it.line}") }
        }

        appendLine()
        appendLine("## Pantry expiring within $NOW_DAYS days")
        appendLine()
        val expiring = expiringSoon(input)
        if (expiring.isEmpty()) {
            appendLine("Nothing expiring.")
        } else {
            expiring.forEach { (item, expiry) ->
                val name = item.name + (item.quantity?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
                appendLine("- $name: ${if (expiry.isBefore(input.today)) "expired on" else "expires"} $expiry")
            }
        }
    }

    private fun mealsOn(input: DeliziosoDocsInput, day: LocalDate): List<MealInput> =
        input.meals
            .filter { it.date == day }
            .sortedWith(compareBy({ MealSlot.all.indexOf(it.slot) }, { it.title }))

    private fun mealBullets(input: DeliziosoDocsInput, day: LocalDate): List<String> =
        mealsOn(input, day)
            .map { "- ${DeliziosoAgenda.slotLabel(it)}: ${it.title} (${DeliziosoAgenda.portion(it)})" }
            .ifEmpty { listOf("- nothing planned") }

    private fun expiringSoon(input: DeliziosoDocsInput): List<Pair<PantryItemEntity, LocalDate>> =
        input.pantry
            .mapNotNull { item -> item.expiresAtEpochDay?.let { item to LocalDate.ofEpochDay(it) } }
            .filter { (item, expiry) ->
                item.inStock &&
                    !expiry.isBefore(input.today.minusDays(DeliziosoAgenda.PANTRY_BACK)) &&
                    !expiry.isAfter(input.today.plusDays(NOW_DAYS))
            }
            .sortedWith(compareBy({ it.second }, { it.first.name }))

    /** A table cell: no pipe and no line break may escape it. */
    private fun cell(text: String): String = text.replace("|", "\\|").replace(Regex("\\s*\\R\\s*"), " ")
}
