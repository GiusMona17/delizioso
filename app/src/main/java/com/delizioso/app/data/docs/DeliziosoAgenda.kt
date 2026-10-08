package com.delizioso.app.data.docs

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

data class AgendaItem(
    val date: LocalDate,
    /** `meal`, `shopping` or `pantry`. */
    val kind: String,
    val title: String,
    val detail: String?,
    val overdue: Boolean,
    val data: JSONObject,
)

/** What is coming (contract: agenda.json). */
object DeliziosoAgenda {
    const val MEALS_AHEAD = 14L
    const val PANTRY_AHEAD = 14L
    const val PANTRY_BACK = 30L

    fun build(input: DeliziosoDocsInput): List<AgendaItem> =
        (mealItems(input) + shoppingItems(input) + pantryItems(input))
            .sortedWith(compareBy({ it.date }, { it.kind }, { it.title }))

    private fun mealItems(input: DeliziosoDocsInput): List<AgendaItem> =
        input.meals
            .filter { !it.date.isBefore(input.today) && !it.date.isAfter(input.today.plusDays(MEALS_AHEAD)) }
            .map { meal ->
                AgendaItem(
                    date = meal.date,
                    kind = "meal",
                    title = meal.title,
                    detail = "${slotLabel(meal)}, ${portion(meal)}",
                    overdue = false,
                    data = JSONObject()
                        .put("slot", meal.slot)
                        .put("servings", meal.servings)
                        .put("recipeId", meal.recipeId ?: JSONObject.NULL)
                        .put("cooked", meal.cooked)
                        .put("isSide", meal.isSide),
                )
            }

    private fun shoppingItems(input: DeliziosoDocsInput): List<AgendaItem> {
        if (input.shopping.isEmpty()) return emptyList()
        return listOf(
            AgendaItem(
                date = input.today,
                kind = "shopping",
                title = "Shopping list",
                detail = toBuy(input.shopping.size),
                overdue = false,
                data = JSONObject().put("items", JSONArray(input.shopping.map { it.line })),
            ),
        )
    }

    private fun pantryItems(input: DeliziosoDocsInput): List<AgendaItem> =
        input.pantry.mapNotNull { item ->
            val expiry = item.expiresAtEpochDay?.let(LocalDate::ofEpochDay) ?: return@mapNotNull null
            val inWindow = !expiry.isBefore(input.today.minusDays(PANTRY_BACK)) &&
                !expiry.isAfter(input.today.plusDays(PANTRY_AHEAD))
            if (!item.inStock || !inWindow) return@mapNotNull null
            AgendaItem(
                date = expiry,
                kind = "pantry",
                title = item.name,
                detail = item.quantity?.takeIf { it.isNotBlank() },
                overdue = expiry.isBefore(input.today),
                data = JSONObject().put("pantryItemId", item.id).put("category", item.category),
            )
        }

    /** "lunch" or "dinner (side)". */
    fun slotLabel(meal: MealInput): String = meal.slot.lowercase() + if (meal.isSide) " (side)" else ""

    /** "1 serving", "2 servings, 610 kcal". */
    fun portion(meal: MealInput): String =
        "${meal.servings} serving${if (meal.servings == 1) "" else "s"}" + (meal.kcal?.let { ", $it kcal" } ?: "")

    fun toBuy(count: Int): String = "$count item${if (count == 1) "" else "s"} to buy"
}
