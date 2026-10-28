package com.delizioso.app.data.docs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.delizioso.app.PlainTestApplication
import com.delizioso.app.data.local.PantryItemEntity
import java.time.Instant
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = PlainTestApplication::class)
class DeliziosoDocsBuilderTest {

    private val today = LocalDate.of(2026, 10, 8)
    private val generatedAt = Instant.parse("2026-10-08T15:23:54Z")

    private val piadina = MealInput(today, "LUNCH", "Piadina crudo, stracchino e rucola", 1, 610, null, cooked = false, isSide = false)
    private val risotto = MealInput(today, "DINNER", "Risotto ai funghi", 2, null, 75, cooked = false, isSide = false)
    private val pasta = MealInput(today.plusDays(1), "DINNER", "Pasta al forno", 2, null, 48, cooked = false, isSide = false)

    private val shopping = listOf(ShoppingLine("500 g pasta", "Pasta & rice"), ShoppingLine("6 uova", "Dairy & eggs"))

    private fun pantryItem(id: Long, name: String, expires: String?, quantity: String? = null, inStock: Boolean = true) =
        PantryItemEntity(
            id = id, name = name, quantity = quantity,
            expiresAtEpochDay = expires?.let { LocalDate.parse(it).toEpochDay() }, inStock = inStock,
        )

    private val pantry = listOf(
        pantryItem(1, "Latte", "2026-10-10", "1 l"),
        pantryItem(2, "Yogurt", "2026-10-05"),
        pantryItem(3, "Riso", "2027-01-01"),
        pantryItem(4, "Farina", "2026-10-09", inStock = false),
    )

    private fun input(
        meals: List<MealInput> = listOf(piadina, risotto, pasta),
        shopping: List<ShoppingLine> = this.shopping,
        pantry: List<PantryItemEntity> = this.pantry,
    ) = DeliziosoDocsInput(today, generatedAt, "Europe/Rome", meals, shopping, pantry)

    private fun golden(name: String): String =
        javaClass.getResource("/docs-golden/$name")!!.readText(Charsets.UTF_8).replace("\r\n", "\n")

    private fun plain(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { plain(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { plain(value.get(it)) }
        JSONObject.NULL -> null
        is Number -> value.toDouble()
        else -> value
    }

    private fun parsed(text: String) = plain(JSONObject(text))

    private fun items(files: Map<String, String>): List<JSONObject> {
        val array = JSONObject(files.getValue("agenda.json")).getJSONArray("items")
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    @Test
    fun `the fixture produces exactly the golden documents`() {
        val files = DeliziosoDocsBuilder.build(input())

        assertEquals(setOf("manifest.json", "now.md", "agenda.json"), files.keys)
        assertEquals(golden("now.md"), files.getValue("now.md"))
        assertEquals(parsed(golden("manifest.json")), parsed(files.getValue("manifest.json")))
        assertEquals(parsed(golden("agenda.json")), parsed(files.getValue("agenda.json")))
    }

    @Test
    fun `two runs are byte identical and use LF`() {
        val a = DeliziosoDocsBuilder.build(input())
        val b = DeliziosoDocsBuilder.build(input())

        assertEquals(a, b)
        assertFalse(a.values.any { it.contains('\r') })
        assertTrue(a.values.all { it.endsWith("\n") })
    }

    @Test
    fun `an empty state still yields valid files and says so`() {
        val files = DeliziosoDocsBuilder.build(input(meals = emptyList(), shopping = emptyList(), pantry = emptyList()))

        assertEquals(0, items(files).size)
        val now = files.getValue("now.md")
        assertTrue(now.contains("## Today (2026-10-08)\n\n- nothing planned\n"))
        assertTrue(now.contains("## Shopping list\n\nNothing to buy.\n"))
        assertTrue(now.contains("## Pantry expiring within 7 days\n\nNothing expiring.\n"))
    }

    @Test
    fun `a meal beyond 14 days or before this week's Monday is left out`() {
        val farAhead = pasta.copy(date = today.plusDays(15), title = "Troppo lontano")
        val lastSunday = pasta.copy(date = LocalDate.of(2026, 10, 4), title = "Domenica scorsa")
        val monday = pasta.copy(date = LocalDate.of(2026, 10, 5), title = "Lunedi")
        val yesterday = pasta.copy(date = today.minusDays(1), title = "Ieri")
        val edge = pasta.copy(date = today.plusDays(14), title = "Al limite")

        val titles = items(DeliziosoDocsBuilder.build(input(meals = listOf(farAhead, lastSunday, monday, yesterday, edge), shopping = emptyList(), pantry = emptyList())))
            .map { it.getString("title") }

        assertEquals(listOf("Lunedi", "Ieri", "Al limite"), titles)
    }

    @Test
    fun `on a Monday nothing before today is kept`() {
        val monday = LocalDate.of(2026, 10, 5)
        val lastSunday = pasta.copy(date = monday.minusDays(1), title = "Domenica")
        val mondayMeal = pasta.copy(date = monday, title = "Oggi")
        val mondayInput = DeliziosoDocsInput(monday, generatedAt, "Europe/Rome", listOf(lastSunday, mondayMeal), emptyList(), emptyList())

        val titles = items(DeliziosoDocsBuilder.build(mondayInput)).map { it.getString("title") }

        assertEquals(listOf("Oggi"), titles)
    }

    @Test
    fun `now md ignores this week's past meals`() {
        val pastMeal = piadina.copy(date = today.minusDays(2), title = "Passato")

        val withPast = DeliziosoDocsBuilder.build(input(meals = listOf(piadina, risotto, pasta, pastMeal)))
        val without = DeliziosoDocsBuilder.build(input())

        assertEquals(without.getValue("now.md"), withPast.getValue("now.md"))
    }

    @Test
    fun `a side dish is marked, one serving is singular and calories are optional`() {
        val side = risotto.copy(title = "Pane", servings = 1, isSide = true, kcal = null)

        val item = items(DeliziosoDocsBuilder.build(input(meals = listOf(side), shopping = emptyList(), pantry = emptyList()))).single()

        assertEquals("dinner (side), 1 serving", item.getString("detail"))
        assertTrue(item.getJSONObject("data").getBoolean("isSide"))
    }

    @Test
    fun `pantry only counts what is in stock, expires within 14 days and is not older than 30`() {
        val tooOld = pantryItem(5, "Vecchio", "2026-09-01")
        val noDate = pantryItem(6, "Sale", null)
        val boundary = pantryItem(7, "Limite", "2026-10-22")
        val beyond = pantryItem(8, "Oltre", "2026-10-23")

        val titles = items(DeliziosoDocsBuilder.build(input(meals = emptyList(), shopping = emptyList(), pantry = listOf(tooOld, noDate, boundary, beyond, pantry[3]))))
            .map { it.getString("title") }

        assertEquals(listOf("Limite"), titles)
    }

    @Test
    fun `no shopping item is written when there is nothing to buy`() {
        val kinds = items(DeliziosoDocsBuilder.build(input(shopping = emptyList()))).map { it.getString("kind") }

        assertFalse(kinds.contains("shopping"))
    }

    @Test
    fun `a pipe in a title cannot break a table`() {
        val odd = piadina.copy(title = "Pane | burro")

        val now = DeliziosoDocsBuilder.build(input(meals = listOf(odd))).getValue("now.md")

        assertTrue(now.contains("Pane \\| burro"))
    }
}
