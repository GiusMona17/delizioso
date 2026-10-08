# Delizioso assistant documents (N2c) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task by task. Steps use checkbox (`- [ ]`) syntax. **Read `CLAUDE.md` first: surgical changes, no speculative code, match the existing style.**

**Goal:** After a sync, Delizioso delivers three small documents to the Raspberry Pi as a second snapsync app, `delizioso-docs`: `manifest.json`, `now.md` (today's and tomorrow's meals, the next 7 days, the shopping list, what is expiring in the pantry) and `agenda.json` (what is coming: planned meals, the open shopping list, pantry expiries). Corvo, the owner's assistant on the Pi, reads them to put the menu in his daily and weekly notes.

**Architecture:** A **pure builder** turns the stored data into the three files. A **loader** reads the repository and preferences and works out the open shopping list with the code the app already has (`GroceryAggregator`, `PantryMatcher`). A **pusher**, copied from Daily Flow's, sends the files as content-addressed blobs plus a snapshot. A **DocsSync** decides whether and when (switch on, server set, one minute apart, only when something changed), and the existing `SyncWorker` calls it after a successful sync. A **card** in Profile, under the Pi sync card, holds the switch and a "send now" button.

**Tech Stack:** Kotlin, Room (schema unchanged), manual DI (`AppContainer`, no Hilt), WorkManager, DataStore, `org.json` (already used by the sync code; **no new dependency**), Compose, JUnit4 + Robolectric.

**Specs (read both first):**
- The contract: `D:\Projects\Coding\personal-dashboard\docs\superpowers\specs\2026-10-08-app-docs-contract-design.md` (the files, the `agenda.json` shape). **A change to the contract is made in that repository first.**
- The roadmap: `D:\Projects\Coding\personal-dashboard\docs\superpowers\plans\2026-10-08-notes-system-roadmap.md` (this plan is sub-project **N2c**; the reader on the Pi side is N2).

**Reference implementation (copy from it, do not re-invent):** Daily Flow, `D:\Projects\Coding\daily-flow-tracker-android-native`, commit `7cfad4e`: `data/docs/DocsPusher.kt`, `DocsSync.kt`, `DocsSettingsRepository.kt`, `presentation/settings/docs/`, and the tests `DocsPusherTest.kt`, `DocsSyncTest.kt`. The sibling plan for Machina (`D:\Projects\Coding\machina\docs\superpowers\plans\2026-10-08-assistant-docs.md`) solves the same delivery problem in the other manual-vs-Hilt flavour; read it for the parts that are the same.

**Starting point:** Delizioso `main` at `fc7096d` (working tree clean when this plan was written).

## Global Constraints

- **Output is deterministic:** the same data gives byte-identical files; LF line endings; UTF-8; missing is `null`, never 0.
- **Horizon (contract §4):** planned meals from today to 14 days ahead; the shopping list is one item dated today; pantry items in stock that expire from 30 days ago to 14 days ahead (an expired one is `overdue`). `now.md` shows the next 7 days and pantry items expiring within 7 days.
- **The open shopping list is what the Grocery screen would show for the next 7 days, minus what is ticked and minus what is in the pantry** (the same building blocks as `GroceryViewModel`).
- **Wire constants:** snapsync app `delizioso-docs`; envelope `{"format": "delizioso-docs/1", "schema": 1, "blobs": [...], "data": {"files": {path: sha256}}}`; `agenda.json` has `"format": "agenda/1"`; `kind` is one of `meal`, `shopping`, `pantry`.
- **Stable English** for headings, kinds, slots (`lunch`, `dinner`), weekday names; recipe titles, pantry names, shopping lines exactly as stored.
- **No new dependency, no Room schema change, no new permission.** `org.json` is used in the builder; tests that touch it run under Robolectric with `@Config(application = PlainTestApplication::class)` like the sync tests.
- A failure of the documents must **never** fail or delay the main sync: `DocsSync.run` never throws.
- Documents are **off until the owner switches them on** in Profile; same server and token as the main sync.
- UI strings go in `res/values/strings.xml` **and** `res/values-it/strings.xml` (an Italian string with an apostrophe needs `\'`).
- Commit messages end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Never `git add -A`: add the files you changed. Do not push, merge or install on the phone without the owner's yes.
- Run unit tests with `./gradlew testDebugUnitTest --rerun --no-build-cache` ("UP-TO-DATE" or "FROM-CACHE" is not a run). Compose or instrumented tests that cannot run on the PC must at least compile (`./gradlew assembleDebugAndroidTest`), and the final report says which did not run.

## File structure (paths under `app/src/main/java/com/delizioso/app/` unless noted)

| Area | Files |
|---|---|
| Pure builder | new `data/docs/DeliziosoDocsInput.kt`, `DeliziosoAgenda.kt`, `DeliziosoDocsBuilder.kt` |
| Loader | new `data/docs/DeliziosoDocsLoader.kt` |
| Delivery | new `data/docs/DocsPusher.kt`, `DocsSettingsRepository.kt`, `DocsSync.kt`; modify `DeliziosoApplication.kt` (`AppContainer`), `data/sync/SyncWorker.kt` |
| Card | new `ui/screens/profile/DocsViewModel.kt`, `DocsControls.kt`; modify `ui/screens/profile/ProfileScreen.kt`; `res/values/strings.xml`, `res/values-it/strings.xml` |
| Tests (under `app/src/test/`) | new `java/com/delizioso/app/data/docs/DeliziosoDocsBuilderTest.kt`, `DeliziosoDocsLoaderTest.kt`, `DocsPusherTest.kt`, `DocsSyncTest.kt`, `ui/screens/profile/DocsStatusTest.kt`; new golden files `resources/docs-golden/{manifest.json,now.md,agenda.json}` |
| Docs | `README.md` |

---

### Task 0: Worktree and baseline

- [ ] **Step 1: Work in a sibling worktree**

```bash
cd /d/Projects/Coding/delizioso
git status --short            # must be empty
git worktree add ../delizioso-wt-docs -b feat/assistant-docs main
cd ../delizioso-wt-docs
git rev-parse HEAD            # expect fc7096d...
cp ../delizioso/local.properties .   # git-ignored
```

- [ ] **Step 2: Baseline test count**

Run `./gradlew testDebugUnitTest --rerun --no-build-cache` and note the number of tests passed (sum the `tests=` attributes in `app/build/test-results/testDebugUnitTest/*.xml`). The final report compares against it.

---

### Task 1: The pure builder, with golden documents

**Files:**
- Create: `data/docs/DeliziosoDocsInput.kt`, `DeliziosoAgenda.kt`, `DeliziosoDocsBuilder.kt`
- Create: `app/src/test/resources/docs-golden/manifest.json`, `now.md`, `agenda.json`
- Test: `app/src/test/java/com/delizioso/app/data/docs/DeliziosoDocsBuilderTest.kt`

**Interfaces (produced, used by tasks 2-4):**
- `data class MealInput(date: LocalDate, slot: String, title: String, servings: Int, kcal: Int?, recipeId: Long?, cooked: Boolean, isSide: Boolean)`
- `data class ShoppingLine(line: String, category: String)`
- `data class DeliziosoDocsInput(today: LocalDate, generatedAt: Instant, timezone: String, meals: List<MealInput>, shopping: List<ShoppingLine>, pantry: List<PantryItemEntity>)`
- `object DeliziosoDocsBuilder { fun build(input: DeliziosoDocsInput): Map<String, String> }` returning exactly `manifest.json`, `now.md`, `agenda.json`.

- [ ] **Step 1: Write the golden files (exact; do not generate them from your own output)**

`app/src/test/resources/docs-golden/now.md` (ends with one newline):

```
# Delizioso - 2026-10-08

The owner's meal plan, shopping list and pantry, written for an assistant. Dates are local (Europe/Rome).

## Today (2026-10-08)

- lunch: Piadina crudo, stracchino e rucola (1 serving, 610 kcal)
- dinner: Risotto ai funghi (2 servings)

## Tomorrow (2026-10-09)

- dinner: Pasta al forno (2 servings)

## Next 7 days

| Date | Weekday | Meals |
|---|---|---|
| 2026-10-08 | Thursday | lunch: Piadina crudo, stracchino e rucola; dinner: Risotto ai funghi |
| 2026-10-09 | Friday | dinner: Pasta al forno |
| 2026-10-10 | Saturday | nothing planned |
| 2026-10-11 | Sunday | nothing planned |
| 2026-10-12 | Monday | nothing planned |
| 2026-10-13 | Tuesday | nothing planned |
| 2026-10-14 | Wednesday | nothing planned |

## Shopping list (2 items to buy)

- 500 g pasta
- 6 uova

## Pantry expiring within 7 days

- Yogurt: expired on 2026-10-05
- Latte (1 l): expires 2026-10-10
```

`app/src/test/resources/docs-golden/manifest.json` (compared as parsed JSON, so formatting is free):

```json
{
  "format": "delizioso-docs/1",
  "generatedAt": "2026-10-08T15:23:54Z",
  "timezone": "Europe/Rome",
  "counts": {"agendaItems": 6}
}
```

`app/src/test/resources/docs-golden/agenda.json`:

```json
{
  "format": "agenda/1",
  "generatedAt": "2026-10-08T15:23:54Z",
  "items": [
    {"date": "2026-10-05", "kind": "pantry", "title": "Yogurt", "detail": null, "overdue": true,
     "data": {"pantryItemId": 2, "category": "Pantry"}},
    {"date": "2026-10-08", "kind": "meal", "title": "Piadina crudo, stracchino e rucola", "detail": "lunch, 1 serving, 610 kcal", "overdue": false,
     "data": {"slot": "LUNCH", "servings": 1, "recipeId": null, "cooked": false, "isSide": false}},
    {"date": "2026-10-08", "kind": "meal", "title": "Risotto ai funghi", "detail": "dinner, 2 servings", "overdue": false,
     "data": {"slot": "DINNER", "servings": 2, "recipeId": 75, "cooked": false, "isSide": false}},
    {"date": "2026-10-08", "kind": "shopping", "title": "Shopping list", "detail": "2 items to buy", "overdue": false,
     "data": {"items": ["500 g pasta", "6 uova"]}},
    {"date": "2026-10-09", "kind": "meal", "title": "Pasta al forno", "detail": "dinner, 2 servings", "overdue": false,
     "data": {"slot": "DINNER", "servings": 2, "recipeId": 48, "cooked": false, "isSide": false}},
    {"date": "2026-10-10", "kind": "pantry", "title": "Latte", "detail": "1 l", "overdue": false,
     "data": {"pantryItemId": 1, "category": "Pantry"}}
  ]
}
```

- [ ] **Step 2: Write the failing test**

Create `DeliziosoDocsBuilderTest.kt`:

```kotlin
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
        javaClass.getResource("/docs-golden/$name")!!.readText(Charsets.UTF_8)

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
    fun `a meal beyond 14 days or before today is left out`() {
        val farAhead = pasta.copy(date = today.plusDays(15), title = "Troppo lontano")
        val yesterday = pasta.copy(date = today.minusDays(1), title = "Ieri")
        val edge = pasta.copy(date = today.plusDays(14), title = "Al limite")

        val titles = items(DeliziosoDocsBuilder.build(input(meals = listOf(farAhead, yesterday, edge), shopping = emptyList(), pantry = emptyList())))
            .map { it.getString("title") }

        assertEquals(listOf("Al limite"), titles)
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
```

- [ ] **Step 3: Run it and see it fail**

Run: `./gradlew testDebugUnitTest --tests "*DeliziosoDocsBuilderTest" --rerun --no-build-cache`
Expected: compilation error (`Unresolved reference: DeliziosoDocsBuilder`).

- [ ] **Step 4: Write the input types**

`data/docs/DeliziosoDocsInput.kt`:

```kotlin
package com.delizioso.app.data.docs

import com.delizioso.app.data.local.PantryItemEntity
import java.time.Instant
import java.time.LocalDate

/** A planned meal with its title already resolved (recipe title or the hand-typed name). */
data class MealInput(
    val date: LocalDate,
    /** BREAKFAST | LUNCH | DINNER | SNACK, as stored. */
    val slot: String,
    val title: String,
    val servings: Int,
    val kcal: Int?,
    val recipeId: Long?,
    val cooked: Boolean,
    val isSide: Boolean,
)

/** One line of the open shopping list: not ticked, not in the pantry. */
data class ShoppingLine(
    val line: String,
    val category: String,
)

data class DeliziosoDocsInput(
    val today: LocalDate,
    val generatedAt: Instant,
    /** IANA name, e.g. "Europe/Rome". */
    val timezone: String,
    val meals: List<MealInput>,
    val shopping: List<ShoppingLine>,
    val pantry: List<PantryItemEntity>,
)
```

- [ ] **Step 5: Write the agenda**

`data/docs/DeliziosoAgenda.kt`:

```kotlin
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
```

- [ ] **Step 6: Write the builder**

`data/docs/DeliziosoDocsBuilder.kt`:

```kotlin
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
        )
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
```
Notes for the implementer: `MealSlot.all` is `["BREAKFAST","LUNCH","DINNER","SNACK"]` (`data/local/PlannedMealEntity.kt`); an unknown slot sorts first (`indexOf` = -1), which is harmless. The text "within 7 days" in the heading comes from `NOW_DAYS`; the golden spells it out, which is the test that the two agree.

- [ ] **Step 7: Run the tests**

Run: `./gradlew testDebugUnitTest --tests "*DeliziosoDocsBuilderTest" --rerun --no-build-cache`
Expected: PASS (8 tests). If the golden `now.md` differs by one character, compare by eye and decide whether the code or the golden is wrong; the golden is the contract with the reader on the Pi, never overwrite it with the output without understanding the difference.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/delizioso/app/data/docs app/src/test/java/com/delizioso/app/data/docs app/src/test/resources/docs-golden
git commit -m "feat: the assistant documents of Delizioso, built from the stored data" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 2: The loader

**Files:**
- Create: `data/docs/DeliziosoDocsLoader.kt`
- Test: `app/src/test/java/com/delizioso/app/data/docs/DeliziosoDocsLoaderTest.kt`

**Interfaces:**
- Consumes: `DeliziosoDocsInput`, `MealInput`, `ShoppingLine` (Task 1); `RecipeRepository.mealsBetween(from, to)`, `allWithDetails`, `pantryItems`, `inStockPantryItems`, `save`, `addMeal`, `savePantryItem`; `GroceryAggregator.aggregate`, `GroceryCategories.of`, `PantryMatcher.isIngredientAvailable`; `CustomGroceryLine(line, source)`; test helper `newDeliziosoDb()` (`data/sync/SyncTestSupport.kt`).
- Produces: `fun interface DeliziosoDocsSource { suspend fun load(): DeliziosoDocsInput }` and `class DeliziosoDocsLoader(repository, checked: suspend () -> Set<String>, custom: suspend () -> List<CustomGroceryLine>) : DeliziosoDocsSource`. The grocery preferences come in as two lambdas so tests do not touch the process-wide DataStore.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.delizioso.app.data.docs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.delizioso.app.PlainTestApplication
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.local.CustomGroceryLine
import com.delizioso.app.data.local.IngredientEntity
import com.delizioso.app.data.local.PantryItemEntity
import com.delizioso.app.data.local.PlannedMealEntity
import com.delizioso.app.data.local.RecipeEntity
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.data.sync.newDeliziosoDb
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = PlainTestApplication::class)
class DeliziosoDocsLoaderTest {

    private val db = newDeliziosoDb()
    private val repository = RecipeRepository(db.recipeDao(), db.pantryDao())
    private val now = Instant.parse("2026-10-08T15:23:54Z")
    private val today = LocalDate.of(2026, 10, 8)
    private var checked: Set<String> = emptySet()
    private var custom: List<CustomGroceryLine> = emptyList()

    private fun load() = runBlocking {
        DeliziosoDocsLoader(repository, { checked }, { custom }).load(now, ZoneId.of("Europe/Rome"))
    }

    private fun recipe(title: String, vararg ingredients: String): Long = runBlocking {
        repository.save(
            RecipeWithDetails(
                recipe = RecipeEntity(title = title, caloriesKcal = 480.0),
                ingredients = ingredients.mapIndexed { i, name -> IngredientEntity(recipeId = 0, position = i, name = name) },
            ),
        )
    }

    private fun plan(recipeId: Long?, day: LocalDate, slot: String, name: String? = null, kcal: Double? = null, servings: Int = 2) = runBlocking {
        repository.addMeal(
            PlannedMealEntity(
                recipeId = recipeId, customName = name, caloriesKcal = kcal,
                dateEpochDay = day.toEpochDay(), slot = slot, servings = servings,
            ),
        )
    }

    @Test
    fun `a planned meal gets its recipe title, or its own name, and its calories`() {
        val risotto = recipe("Risotto ai funghi")
        plan(risotto, today, "DINNER")
        plan(null, today, "LUNCH", name = "Piadina", kcal = 610.0, servings = 1)

        val meals = load().meals.sortedBy { it.slot }

        assertEquals(listOf("DINNER", "LUNCH"), meals.map { it.slot })
        assertEquals("Risotto ai funghi", meals[0].title)
        assertEquals(480, meals[0].kcal)       // the recipe's calories when the meal has none
        assertEquals("Piadina", meals[1].title)
        assertEquals(610, meals[1].kcal)
        assertEquals(risotto, meals[0].recipeId)
    }

    @Test
    fun `only the next 14 days are read`() {
        val id = recipe("Pasta")
        plan(id, today.plusDays(14), "DINNER")
        plan(id, today.plusDays(15), "DINNER")

        assertEquals(listOf(today.plusDays(14)), load().meals.map { it.date })
    }

    @Test
    fun `the shopping list is the next 7 days of recipes plus custom items, minus ticked and in-pantry ones`() = runBlocking {
        val id = recipe("Spaghetti al pomodoro", "spaghetti")
        plan(id, today.plusDays(1), "DINNER")
        plan(recipe("Torta", "farina"), today.plusDays(9), "DINNER") // beyond the 7-day shopping range
        custom = listOf(
            CustomGroceryLine("detersivo", null),
            CustomGroceryLine("lievito", null),
            CustomGroceryLine("sale grosso", null),
        )
        checked = setOf("lievito")
        repository.savePantryItem(PantryItemEntity(name = "sale grosso", inStock = true))

        val lines = load().shopping.map { it.line }

        assertTrue(lines.any { "spaghetti" in it })
        assertTrue(lines.none { "farina" in it })
        assertTrue("detersivo" in lines)
        assertFalse("lievito" in lines)      // ticked
        assertFalse("sale grosso" in lines)  // in the pantry
    }

    @Test
    fun `the pantry is read whole and the day and the timezone come from the clock`() = runBlocking {
        repository.savePantryItem(PantryItemEntity(name = "Latte", expiresAtEpochDay = today.plusDays(2).toEpochDay()))
        repository.savePantryItem(PantryItemEntity(name = "Farina", inStock = false))

        val input = load()

        assertEquals(setOf("Latte", "Farina"), input.pantry.map { it.name }.toSet())
        assertEquals(today, input.today)
        assertEquals("Europe/Rome", input.timezone)
        assertEquals(now, input.generatedAt)
    }
}
```
If `RecipeEntity`, `IngredientEntity` or `RecipeWithDetails` need other arguments, copy the construction used in `app/src/test/java/com/delizioso/app/data/backup/LibraryBackupTest.kt`. `CustomGroceryLine` is declared in `data/local/UserPreferences.kt`.

- [ ] **Step 2: Run and see it fail**

Run: `./gradlew testDebugUnitTest --tests "*DeliziosoDocsLoaderTest" --rerun --no-build-cache`
Expected: compilation error (`Unresolved reference: DeliziosoDocsLoader`).

- [ ] **Step 3: Write the loader**

```kotlin
package com.delizioso.app.data.docs

import com.delizioso.app.data.GroceryAggregator
import com.delizioso.app.data.GroceryCategories
import com.delizioso.app.data.GroceryItem
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.local.CustomGroceryLine
import com.delizioso.app.data.pantry.PantryMatcher
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/** Where the documents' input comes from; a fake in tests. */
fun interface DeliziosoDocsSource {
    suspend fun load(): DeliziosoDocsInput
}

/**
 * Reads the plan, the pantry and the grocery state into the builder's input. The open shopping list repeats what
 * GroceryViewModel computes for its default 7 days (planned recipes aggregated, plus the owner's own lines), then drops
 * what is ticked and what the pantry already has.
 */
class DeliziosoDocsLoader(
    private val repository: RecipeRepository,
    private val checked: suspend () -> Set<String>,
    private val custom: suspend () -> List<CustomGroceryLine>,
) : DeliziosoDocsSource {

    override suspend fun load(): DeliziosoDocsInput = load(Instant.now(), ZoneId.systemDefault())

    suspend fun load(now: Instant, zone: ZoneId): DeliziosoDocsInput {
        val today = LocalDate.ofInstant(now, zone)
        val planned = repository.mealsBetween(today.toEpochDay(), today.plusDays(DeliziosoAgenda.MEALS_AHEAD).toEpochDay()).first()

        val meals = planned.map { m ->
            MealInput(
                date = LocalDate.ofEpochDay(m.meal.dateEpochDay),
                slot = m.meal.slot,
                title = m.recipe?.title ?: m.meal.customName ?: "Meal",
                servings = m.meal.servings,
                kcal = (m.meal.caloriesKcal ?: m.recipe?.caloriesKcal)?.roundToInt(),
                recipeId = m.meal.recipeId,
                cooked = m.meal.cooked,
                isSide = m.meal.isSide,
            )
        }
        return DeliziosoDocsInput(
            today = today,
            generatedAt = now,
            timezone = zone.id,
            meals = meals,
            shopping = shoppingList(today),
            pantry = repository.pantryItems.first(),
        )
    }

    private suspend fun shoppingList(today: LocalDate): List<ShoppingLine> {
        val week = repository.mealsBetween(today.toEpochDay(), today.plusDays(SHOPPING_DAYS - 1).toEpochDay()).first()
        val plannedIds = week.map { it.meal.recipeId }.toSet()
        val recipes = repository.allWithDetails.first().filter { it.recipe.id in plannedIds }
        val own = custom().sortedBy { it.line }.map {
            GroceryItem(
                name = it.line,
                line = it.line,
                isMerged = false,
                category = GroceryCategories.of(it.line),
            )
        }
        val inStock = repository.inStockPantryItems.first()
        val ticked = checked()
        return (GroceryAggregator.aggregate(recipes) + own)
            .filter { it.line !in ticked && !PantryMatcher.isIngredientAvailable(it.name, inStock) }
            .map { ShoppingLine(it.line, it.category) }
    }

    private companion object {
        const val SHOPPING_DAYS = 7L
    }
}
```
- [ ] **Step 4: Run, then commit**

Run: `./gradlew testDebugUnitTest --tests "*DeliziosoDocsLoaderTest" --rerun --no-build-cache`
Expected: PASS (4 tests).

```bash
git add app/src/main/java/com/delizioso/app/data/docs/DeliziosoDocsLoader.kt app/src/test/java/com/delizioso/app/data/docs/DeliziosoDocsLoaderTest.kt
git commit -m "feat: read the plan, the pantry and the open shopping list into the assistant documents" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Delivery — the pusher, the settings and `DocsSync`

**Files:**
- Create: `data/docs/DocsPusher.kt`, `data/docs/DocsSettingsRepository.kt`, `data/docs/DocsSync.kt`
- Test: `app/src/test/java/com/delizioso/app/data/docs/DocsPusherTest.kt`, `DocsSyncTest.kt`

**Interfaces:**
- Consumes: `SnapshotStore`, `PushResult`, `SnapsyncException`, `sha256Of(ByteArray)` (all in `data/sync`), `SyncSettingsRepository.current()` and `SyncSettings.isConfigured`, `FakeSnapshotStore` (in `SyncEngineTest.kt`), `newPrefs(file)` (in `SyncTestSupport.kt`), `DeliziosoDocsSource`, `DeliziosoDocsBuilder`.
- Produces: `DocsPusher(store, workDir).push(files): Int`, `DocsPusher.APP = "delizioso-docs"`, `DocsPusher.contentHash(files)`; `DocsSettings`, `DocsSettingsRepository(dataStore)`; `sealed interface DocsOutcome`; `DocsSync(source, connection, settings, storeFor, workDir, clock).run(force = false): DocsOutcome`.

- [ ] **Step 1: Write the failing `DocsPusherTest`**

```kotlin
package com.delizioso.app.data.docs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.delizioso.app.PlainTestApplication
import com.delizioso.app.data.sync.FakeSnapshotStore
import com.delizioso.app.data.sync.PushResult
import com.delizioso.app.data.sync.SnapshotStore
import com.delizioso.app.data.sync.SnapsyncException
import com.delizioso.app.data.sync.sha256Of
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = PlainTestApplication::class)
class DocsPusherTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val pi = FakeSnapshotStore()
    private val workDir get() = File(tmp.root, "docs-sync")
    private val files = mapOf("manifest.json" to "{}\n", "now.md" to "# Now\n", "agenda.json" to "{\"items\":[]}\n")

    private fun push(files: Map<String, String>) = runBlocking { DocsPusher(pi, workDir).push(files) }
    private fun sha(text: String) = sha256Of(text.toByteArray(Charsets.UTF_8))
    private fun delivered() = JSONObject(pi.body!!.decodeToString())

    @Test
    fun `every file is uploaded once, by its hash, and the snapshot maps each path to it`() {
        val uploaded = push(files)

        assertEquals(3, uploaded)
        assertEquals(files.values.map(::sha).toSet(), pi.blobs.keys)
        val mapped = delivered().getJSONObject("data").getJSONObject("files")
        assertEquals(files.mapValues { sha(it.value) }, mapped.keys().asSequence().associateWith { mapped.getString(it) })
    }

    @Test
    fun `the snapshot says what it is, in the shape the server reads`() {
        push(files)

        assertEquals("delizioso-docs/1", delivered().getString("format"))
        assertEquals(1, delivered().getInt("schema"))
        assertEquals(
            files.values.map(::sha).sorted(),
            delivered().getJSONArray("blobs").let { a -> (0 until a.length()).map { a.getString(it) } },
        )
    }

    @Test
    fun `a file the Pi already has is not uploaded again`() {
        push(files)

        assertEquals(1, push(files + ("now.md" to "# Changed\n")))
    }

    @Test
    fun `the same delivery twice uploads nothing the second time`() {
        push(files)

        assertEquals(0, push(files))
    }

    @Test
    fun `a stale base is retried with the Pi's new head`() {
        val flaky = object : SnapshotStore by pi {
            var first = true
            override suspend fun push(body: ByteArray, baseEtag: String?): PushResult {
                if (first) { first = false; return PushResult.Stale }
                return pi.push(body, baseEtag)
            }
        }

        runBlocking { DocsPusher(flaky, workDir).push(files) }

        assertTrue(pi.body != null)
    }

    @Test
    fun `a Pi that never accepts the snapshot is reported`() {
        val refusing = object : SnapshotStore by pi {
            override suspend fun push(body: ByteArray, baseEtag: String?) = PushResult.Stale
        }

        assertThrows(SnapsyncException::class.java) { runBlocking { DocsPusher(refusing, workDir).push(files) } }
    }

    @Test
    fun `no temporary file is left behind`() {
        push(files)

        assertEquals(emptyList<String>(), workDir.listFiles()?.map { it.name }.orEmpty())
    }

    @Test
    fun `the content hash ignores the manifest and the order of the files`() {
        val a = DocsPusher.contentHash(files + ("manifest.json" to "{\"generatedAt\":\"1\"}"))
        val b = DocsPusher.contentHash(files.entries.reversed().associate { it.key to it.value } + ("manifest.json" to "{\"generatedAt\":\"2\"}"))
        val c = DocsPusher.contentHash(files + ("now.md" to "# Different\n"))

        assertEquals(a, b)
        assertTrue(a != c)
    }
}
```
Run: `./gradlew testDebugUnitTest --tests "*DocsPusherTest" --rerun --no-build-cache`. Expected: compilation error (`Unresolved reference: DocsPusher`).

- [ ] **Step 2: Write `DocsPusher`**

```kotlin
package com.delizioso.app.data.docs

import com.delizioso.app.data.sync.PushResult
import com.delizioso.app.data.sync.SnapshotStore
import com.delizioso.app.data.sync.SnapsyncException
import com.delizioso.app.data.sync.sha256Of
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sends the documents to the Pi as a snapshot of a second snapsync app: every file is a blob named by its hash (a file
 * the Pi already has is not sent again) and the snapshot maps each path to its hash. The server needs nothing new.
 */
class DocsPusher(
    private val store: SnapshotStore,
    private val workDir: File,
) {
    /** Number of blobs that had to be uploaded. */
    suspend fun push(files: Map<String, String>): Int {
        val bytes = files.mapValues { it.value.toByteArray(Charsets.UTF_8) }
        val shaByPath = bytes.mapValues { sha256Of(it.value) }
        val byBlob = bytes.entries.associate { shaByPath.getValue(it.key) to it.value }
        val body = envelope(shaByPath).toByteArray(Charsets.UTF_8)

        var uploaded = 0
        repeat(MAX_ATTEMPTS) {
            for (sha in store.missing(byBlob.keys)) {
                val file = File(workDir, sha).also { it.parentFile?.mkdirs() }
                try {
                    file.writeBytes(byBlob.getValue(sha))
                    store.uploadBlob(sha, file)
                    uploaded++
                } finally {
                    file.delete()
                }
            }
            when (store.push(body, store.head())) {
                is PushResult.Created -> return uploaded
                PushResult.Stale, PushResult.MissingBlobs -> Unit // the Pi moved on or lost a blob: look again
            }
        }
        throw SnapsyncException("il Pi non ha accettato i documenti, riprova")
    }

    companion object {
        const val APP = "delizioso-docs"
        private const val MAX_ATTEMPTS = 3

        /** The snapshot body, in the same shape as the main snapshot: the server reads only the `blobs` list. */
        fun envelope(shaByPath: Map<String, String>): String {
            val paths = JSONObject()
            shaByPath.toSortedMap().forEach { (path, sha) -> paths.put(path, sha) }
            return JSONObject()
                .put("format", "delizioso-docs/1")
                .put("schema", 1)
                .put("blobs", JSONArray(shaByPath.values.toSortedSet().toList()))
                .put("data", JSONObject().put("files", paths))
                .toString()
        }

        /**
         * What identifies the content of a delivery, to skip one that would change nothing. The manifest is left out
         * because it carries the time of generation.
         */
        fun contentHash(files: Map<String, String>): String =
            sha256Of(
                files.filterKeys { it != "manifest.json" }.toSortedMap().entries
                    .joinToString("\n") { "${it.key}=${sha256Of(it.value.toByteArray(Charsets.UTF_8))}" }
                    .toByteArray(Charsets.UTF_8),
            )
    }
}
```

- [ ] **Step 3: Write `DocsSettingsRepository`**

`data/docs/DocsSettingsRepository.kt`. No `@Inject`: this app has no Hilt, the constructor is called from `AppContainer` like `SyncSettingsRepository(preferences.dataStore)`. Keys: `docs_enabled`, `docs_last_hash`, `docs_last_pushed_at`, `docs_last_error`. None is in `DeliziosoSyncConfig.prefKeys`, so none is synced (a test in Step 4 pins it).

```kotlin
package com.delizioso.app.data.docs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class DocsSettings(
    /** The switch in Profile: off until the owner turns it on. */
    val enabled: Boolean = false,
    /** What the last delivery contained (see [DocsPusher.contentHash]); the same content is not sent again. */
    val lastHash: String? = null,
    val lastPushedAt: Long? = null,
    val lastError: String? = null,
)

/** Device only, like the rest of the sync state: none of these keys is part of a snapshot. */
class DocsSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<DocsSettings> = dataStore.data.map { p ->
        DocsSettings(
            enabled = p[ENABLED] ?: false,
            lastHash = p[LAST_HASH],
            lastPushedAt = p[LAST_PUSHED_AT],
            lastError = p[LAST_ERROR],
        )
    }

    suspend fun current(): DocsSettings = settings.first()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED] = enabled }
    }

    suspend fun recordPushed(hash: String, at: Long) {
        dataStore.edit {
            it[LAST_HASH] = hash
            it[LAST_PUSHED_AT] = at
            it.remove(LAST_ERROR)
        }
    }

    suspend fun recordError(message: String) {
        dataStore.edit { it[LAST_ERROR] = message }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("docs_enabled")
        val LAST_HASH = stringPreferencesKey("docs_last_hash")
        val LAST_PUSHED_AT = longPreferencesKey("docs_last_pushed_at")
        val LAST_ERROR = stringPreferencesKey("docs_last_error")
    }
}
```

- [ ] **Step 4: Write `DocsSyncTest`, see it fail, then write `DocsSync`**

```kotlin
package com.delizioso.app.data.docs

import com.delizioso.app.PlainTestApplication
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.delizioso.app.data.sync.DeliziosoSyncConfig
import com.delizioso.app.data.sync.FakeSnapshotStore
import com.delizioso.app.data.sync.PushResult
import com.delizioso.app.data.sync.SnapshotStore
import com.delizioso.app.data.sync.SnapsyncException
import com.delizioso.app.data.sync.SyncSettingsRepository
import com.delizioso.app.data.sync.newPrefs
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = PlainTestApplication::class)
class DocsSyncTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val pi = FakeSnapshotStore()
    private var input = DeliziosoDocsInput(
        LocalDate.of(2026, 10, 8), Instant.parse("2026-10-08T10:00:00Z"), "Europe/Rome",
        emptyList(), emptyList(), emptyList(),
    )
    private var loads = 0
    private var now = 1_000_000L
    private lateinit var connection: SyncSettingsRepository
    private lateinit var settings: DocsSettingsRepository

    @Before
    fun setUp() {
        connection = SyncSettingsRepository(newPrefs(File(tmp.root, "sync.preferences_pb")))
        settings = DocsSettingsRepository(newPrefs(File(tmp.root, "docs.preferences_pb")))
        runBlocking { connection.setServer("https://pi", "token") }
    }

    private fun docs(store: SnapshotStore = pi) = DocsSync(
        source = { loads++; input },
        connection = connection,
        settings = settings,
        storeFor = { store },
        workDir = File(tmp.root, "docs-sync"),
        clock = { now },
    )

    private fun enable() = runBlocking { settings.setEnabled(true) }

    @Test
    fun `nothing happens while the switch is off`() = runBlocking {
        assertEquals(DocsOutcome.Disabled, docs().run())
        assertEquals(0, loads)
        assertNull(pi.body)
    }

    @Test
    fun `nothing happens without a server and a token`() = runBlocking {
        enable()
        connection.setServer("", "")

        assertEquals(DocsOutcome.NotConfigured, docs().run())
    }

    @Test
    fun `the first run delivers the three documents`() = runBlocking {
        enable()

        val outcome = docs().run()

        assertTrue(outcome is DocsOutcome.Pushed)
        assertEquals(3, (outcome as DocsOutcome.Pushed).files)
        assertNotNull(pi.body)
        assertNull(settings.current().lastError)
    }

    @Test
    fun `a second run with the same content sends nothing, even with a new generation time`() = runBlocking {
        enable()
        val sync = docs()
        sync.run()
        now += DocsSync.MIN_INTERVAL_MS + 1
        input = input.copy(generatedAt = Instant.parse("2026-10-08T11:00:00Z"))

        assertEquals(DocsOutcome.UpToDate, sync.run())
    }

    @Test
    fun `a changed day is sent again`() = runBlocking {
        enable()
        val sync = docs()
        sync.run()
        now += DocsSync.MIN_INTERVAL_MS + 1
        input = input.copy(today = LocalDate.of(2026, 10, 9))

        assertTrue(sync.run() is DocsOutcome.Pushed)
    }

    @Test
    fun `two runs less than a minute apart are throttled, force skips the pause`() = runBlocking {
        enable()
        val sync = docs()
        sync.run()
        now += 1_000

        assertEquals(DocsOutcome.Throttled, sync.run())
        assertTrue(sync.run(force = true) is DocsOutcome.Pushed)
    }

    @Test
    fun `a failure is recorded and returned, never thrown`() = runBlocking {
        enable()
        val broken = object : SnapshotStore by pi {
            override suspend fun push(body: ByteArray, baseEtag: String?): PushResult = throw SnapsyncException("pi spento")
        }

        val outcome = docs(broken).run()

        assertEquals(DocsOutcome.Failed("pi spento"), outcome)
        assertEquals("pi spento", settings.current().lastError)
    }

    @Test
    fun `the documents settings are not part of the synced preferences`() {
        assertTrue(DeliziosoSyncConfig.prefKeys.none { it.startsWith("docs_") })
    }
}
```
Run it and expect a compilation error (`Unresolved reference: DocsSync`). Then write `data/docs/DocsSync.kt`:

```kotlin
package com.delizioso.app.data.docs

import com.delizioso.app.data.sync.SnapshotStore
import com.delizioso.app.data.sync.SyncSettings
import com.delizioso.app.data.sync.SyncSettingsRepository
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface DocsOutcome {
    /** The switch in Profile is off. */
    data object Disabled : DocsOutcome

    /** No server address or token yet: the documents use the same connection as the main sync. */
    data object NotConfigured : DocsOutcome

    /** A delivery was attempted less than a minute ago. */
    data object Throttled : DocsOutcome

    /** Nothing changed since the last delivery. */
    data object UpToDate : DocsOutcome
    data class Pushed(val files: Int, val uploaded: Int) : DocsOutcome
    data class Failed(val message: String) : DocsOutcome
}

/**
 * Builds the documents and delivers them to the Pi after a sync, when the owner switched them on. One way: nothing is
 * ever read back. It never throws, so a problem with the documents cannot fail the main sync that calls it.
 */
class DocsSync(
    private val source: DeliziosoDocsSource,
    private val connection: SyncSettingsRepository,
    private val settings: DocsSettingsRepository,
    private val storeFor: (SyncSettings) -> SnapshotStore,
    private val workDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var lastAttemptMillis = Long.MIN_VALUE / 2

    /** [force] skips the one-minute pause and the "nothing changed" check (the "send now" button). */
    suspend fun run(force: Boolean = false): DocsOutcome = mutex.withLock {
        val docs = settings.current()
        if (!docs.enabled) return@withLock DocsOutcome.Disabled
        val server = connection.current()
        if (!server.isConfigured) return@withLock DocsOutcome.NotConfigured
        val now = clock()
        if (!force && now - lastAttemptMillis < MIN_INTERVAL_MS) return@withLock DocsOutcome.Throttled
        lastAttemptMillis = now
        try {
            val input = source.load()
            val files = withContext(Dispatchers.Default) { DeliziosoDocsBuilder.build(input) }
            // agenda.json carries the generation time too, so the hash is taken from a build with a fixed one.
            val hash = DocsPusher.contentHash(DeliziosoDocsBuilder.build(input.copy(generatedAt = Instant.EPOCH)))
            if (!force && hash == docs.lastHash) return@withLock DocsOutcome.UpToDate
            val uploaded = DocsPusher(storeFor(server), workDir).push(files)
            settings.recordPushed(hash, clock())
            DocsOutcome.Pushed(files.size, uploaded)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            settings.recordError(message)
            DocsOutcome.Failed(message)
        }
    }

    companion object {
        const val MIN_INTERVAL_MS = 60_000L
    }
}
```
Run `*DocsPusherTest` and `*DocsSyncTest`: PASS (8 + 8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/delizioso/app/data/docs app/src/test/java/com/delizioso/app/data/docs
git commit -m "feat: deliver the assistant documents to the Pi as a second snapsync app" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Wiring, and the card in Profile

**Files:**
- Modify: `DeliziosoApplication.kt` (`AppContainer`), `data/sync/SyncWorker.kt`, `ui/screens/profile/ProfileScreen.kt`, `res/values/strings.xml`, `res/values-it/strings.xml`
- Create: `ui/screens/profile/DocsViewModel.kt`, `ui/screens/profile/DocsControls.kt`
- Test: `app/src/test/java/com/delizioso/app/ui/screens/profile/DocsStatusTest.kt`

**Interfaces:**
- Consumes: `DocsSync`, `DocsSettingsRepository`, `DeliziosoDocsLoader` (Tasks 2-3); `SyncSettingsRepository`; `AppContainer.syncHttp`, `preferences`; `ClayButton`, `SettingsCard`.
- Produces: `sealed interface DocsStatus` and `fun docsStatus(enabled: Boolean, configured: Boolean, lastPushedAt: Long?, lastError: String?): DocsStatus` (pure, tested); `AppContainer.docsSync`, `AppContainer.docsSettings`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.delizioso.app.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class DocsStatusTest {

    @Test
    fun `off is said first`() {
        assertEquals(DocsStatus.Off, docsStatus(enabled = false, configured = true, lastPushedAt = 5, lastError = "x"))
    }

    @Test
    fun `without a server the owner is sent to set it`() {
        assertEquals(DocsStatus.NeedsServer, docsStatus(enabled = true, configured = false, lastPushedAt = null, lastError = null))
    }

    @Test
    fun `an error wins over a past delivery`() {
        assertEquals(DocsStatus.Failed("pi spento"), docsStatus(true, true, 5, "pi spento"))
    }

    @Test
    fun `a delivery is reported with its time`() {
        assertEquals(DocsStatus.Sent(5), docsStatus(true, true, 5, null))
    }

    @Test
    fun `nothing sent yet`() {
        assertEquals(DocsStatus.Never, docsStatus(true, true, null, null))
    }
}
```
Run `./gradlew testDebugUnitTest --tests "*DocsStatusTest" --rerun --no-build-cache`; expect a compilation error.

- [ ] **Step 2: Write the view model and the controls**

`ui/screens/profile/DocsViewModel.kt`:

```kotlin
package com.delizioso.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.delizioso.app.DeliziosoApplication
import com.delizioso.app.data.docs.DocsSettings
import com.delizioso.app.data.docs.DocsSettingsRepository
import com.delizioso.app.data.docs.DocsSync
import com.delizioso.app.data.sync.SyncSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DocsStatus {
    data object Off : DocsStatus
    data object NeedsServer : DocsStatus
    data class Failed(val message: String) : DocsStatus
    data class Sent(val at: Long) : DocsStatus
    data object Never : DocsStatus
}

fun docsStatus(enabled: Boolean, configured: Boolean, lastPushedAt: Long?, lastError: String?): DocsStatus = when {
    !enabled -> DocsStatus.Off
    !configured -> DocsStatus.NeedsServer
    lastError != null -> DocsStatus.Failed(lastError)
    lastPushedAt != null -> DocsStatus.Sent(lastPushedAt)
    else -> DocsStatus.Never
}

data class DocsUiState(
    val docs: DocsSettings = DocsSettings(),
    /** A server address and token are set: the documents travel with the same connection as the main sync. */
    val configured: Boolean = false,
)

class DocsViewModel(
    private val sync: DocsSync,
    private val settings: DocsSettingsRepository,
    connection: SyncSettingsRepository,
) : ViewModel() {

    val state: StateFlow<DocsUiState> = combine(settings.settings, connection.settings) { d, c ->
        DocsUiState(d, c.isConfigured)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocsUiState())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Turning it on sends the documents at once. */
    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setEnabled(enabled)
            if (enabled) send()
        }
    }

    fun sendNow() {
        viewModelScope.launch { send() }
    }

    private suspend fun send() {
        _busy.value = true
        try {
            sync.run(force = true)
        } finally {
            _busy.value = false
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                DocsViewModel(app.container.docsSync, app.container.docsSettings, app.container.syncSettings)
            }
        }
    }
}
```

`ui/screens/profile/DocsControls.kt`:

```kotlin
package com.delizioso.app.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.delizioso.app.R
import com.delizioso.app.ui.components.ClayButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The documents for the assistant: a switch, the status and "send now". Off until the owner turns it on. */
@Composable
fun DocsControls(viewModel: DocsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val status = docsStatus(state.docs.enabled, state.configured, state.docs.lastPushedAt, state.docs.lastError)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.docs_desc), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.docs_switch), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = state.docs.enabled,
                onCheckedChange = viewModel::setEnabled,
                enabled = !busy && state.configured,
            )
        }
        Text(
            statusText(status),
            style = MaterialTheme.typography.labelMedium,
            color = if (status is DocsStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ClayButton(
            text = stringResource(R.string.docs_send_now),
            enabled = !busy && state.configured && state.docs.enabled,
            onClick = viewModel::sendNow,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun statusText(status: DocsStatus): String = when (status) {
    DocsStatus.Off -> stringResource(R.string.docs_status_off)
    DocsStatus.NeedsServer -> stringResource(R.string.docs_status_needs_server)
    DocsStatus.Never -> stringResource(R.string.docs_status_never)
    is DocsStatus.Failed -> stringResource(R.string.docs_status_error, status.message)
    is DocsStatus.Sent -> stringResource(
        R.string.docs_status_last,
        Instant.ofEpochMilli(status.at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
    )
}
```

- [ ] **Step 3: Strings**

Add to `res/values/strings.xml`, after the `sync_outcome_failed` line:

```xml
    <string name="docs_section">Documents for the assistant</string>
    <string name="docs_desc">After each sync, sends your meal plan, shopping list and pantry to the Pi in a form Corvo can read. Off by default.</string>
    <string name="docs_switch">Send documents to the Pi</string>
    <string name="docs_send_now">Send now</string>
    <string name="docs_status_off">Off</string>
    <string name="docs_status_needs_server">Set the Pi server above first</string>
    <string name="docs_status_never">Nothing sent yet</string>
    <string name="docs_status_last">Last sent: %1$s</string>
    <string name="docs_status_error">Error: %1$s</string>
```
and the same keys to `res/values-it/strings.xml`:

```xml
    <string name="docs_section">Documenti per l\'assistente</string>
    <string name="docs_desc">Dopo ogni sincronizzazione invia al Pi piano dei pasti, lista della spesa e dispensa in un formato leggibile da Corvo. Disattivato di default.</string>
    <string name="docs_switch">Invia i documenti al Pi</string>
    <string name="docs_send_now">Invia ora</string>
    <string name="docs_status_off">Disattivato</string>
    <string name="docs_status_needs_server">Imposta prima il server del Pi qui sopra</string>
    <string name="docs_status_never">Ancora nessun invio</string>
    <string name="docs_status_last">Ultimo invio: %1$s</string>
    <string name="docs_status_error">Errore: %1$s</string>
```

- [ ] **Step 4: Wire the container, the worker and Profile**

`DeliziosoApplication.kt`, in `AppContainer`, after `val syncNotifier ...` add (and import `DeliziosoDocsLoader`, `DocsPusher`, `DocsSettingsRepository`, `DocsSync`):

```kotlin
    val docsSettings: DocsSettingsRepository = DocsSettingsRepository(preferences.dataStore)

    /** The documents for the assistant: delivered to a second snapsync app next to the main one, same server and token. */
    val docsSync: DocsSync by lazy {
        DocsSync(
            DeliziosoDocsLoader(
                recipeRepository,
                { preferences.groceryChecked.first() },
                { preferences.groceryCustomItems.first() },
            ),
            syncSettings,
            docsSettings,
            { s -> SnapsyncClient(syncHttp, s.serverUrl, s.token, DocsPusher.APP) },
            File(context.filesDir, "docs-sync"),
        )
    }
```
(`first` is already imported in this file for `nanoStructurer`.)

`data/sync/SyncWorker.kt`: replace the final `else -> Result.success()` with:

```kotlin
            else -> {
                // The documents for the assistant follow a successful sync; they never decide its result.
                container.docsSync.run()
                Result.success()
            }
```

`ui/screens/profile/ProfileScreen.kt`: add a parameter `docsViewModel: DocsViewModel = viewModel(factory = DocsViewModel.Factory),` after `syncViewModel`, and a new item right after the sync card's `item { ... }`:

```kotlin
            item {
                SettingsCard(title = stringResource(R.string.docs_section)) {
                    DocsControls(docsViewModel)
                }
            }
```

- [ ] **Step 5: Verify**

Run: `./gradlew testDebugUnitTest --rerun --no-build-cache` (the whole unit suite) and `./gradlew assembleDebug assembleDebugAndroidTest`.
Expected: all green; the count is the baseline + 8 + 4 + 8 + 8 + 5. If a resource test checks that `values` and `values-it` have the same keys, it passes because both got the nine new ones.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/delizioso/app/DeliziosoApplication.kt app/src/main/java/com/delizioso/app/data/sync/SyncWorker.kt app/src/main/java/com/delizioso/app/ui/screens/profile app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml app/src/test/java/com/delizioso/app/ui
git commit -m "feat: the documents follow a sync, with a switch and a send-now button in Profile" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Documentation and verification (owner-attended where marked)

- [ ] **Step 1: README**

Add a section to `README.md` next to the "Pi sync" one: what the three files are, that it is off by default and enabled from Profile, that it uses the same server and token as the Pi sync, which data it carries (planned meals for 14 days, the open shopping list, pantry expiries), and a link to the contract in `personal-dashboard` (`docs/superpowers/specs/2026-10-08-app-docs-contract-design.md`).

- [ ] **Step 2: Check the output against the contract by hand**

Run `DeliziosoDocsBuilder.build` on the fixture (a throwaway test, not committed) and check each point of contract §3-§5: the three required files exist; `manifest.json` has `format`, `generatedAt` (ISO UTC) and `timezone`; `agenda.json` has `"format": "agenda/1"`, items sorted by date, kind, title; `kind` is `meal`, `shopping` or `pantry`; an empty week yields no `meal` items and that is not an error.

- [ ] **Step 3: On the phone (owner's yes; the owner installs)**

Build the debug APK and install it; in Profile switch "Documents for the assistant" on. Expected: status "Last sent: ...". On the Pi (`ssh -i ~/.ssh/id_ed25519_pi funkyjack17@raspberrypi4.local`):

```bash
ls ~/snapsync/data/delizioso-docs/snapshots
python3 - <<'EOF'
import json, pathlib
root = pathlib.Path.home() / "snapsync" / "data" / "delizioso-docs"
latest = max((root / "snapshots").iterdir(), key=lambda p: int(p.name.split("-")[0]))
files = json.loads(latest.read_text())["data"]["files"]
for name in ("now.md", "agenda.json"):
    print("=====", name)
    print((root / "blobs" / files[name]).read_text())
EOF
```
Expected: the real `now.md` and `agenda.json`. Compare with the Planner and Grocery screens by eye (the owner confirms). The planner may well be empty for the current week (it was on 2026-10-08): then `now.md` says "nothing planned" and `agenda.json` has no `meal` items. Plan one meal in the app, send again, and check it appears.

- [ ] **Step 4: Hand the real files to the Corvo side**

Copy the two real files to `D:\Projects\Coding\personal-dashboard\backend\tests\fixtures\app_docs\delizioso\` for sub-project N2's tests, only if that directory exists or the owner asks.

- [ ] **Step 5: Finish**

Use `superpowers:finishing-a-development-branch`; the owner chooses merge, pull request or keep. Remove the worktree (`git worktree remove ../delizioso-wt-docs`) only after the merge.

## Self-review against the contract

| Contract point | Where |
|---|---|
| Transport: `delizioso-docs`, envelope, blobs by hash, one way, same token | Task 3 (`DocsPusher`), Task 4 (container) |
| Three files; `manifest.json` fields; deterministic; LF; `null` not 0 | Task 1 (golden, determinism and LF tests) |
| `agenda.json`: sorted, `meal`/`shopping`/`pantry`, `overdue`, horizon, `detail`, `data` | Task 1 (`DeliziosoAgenda`, golden) |
| An empty week yields no `meal` items | Task 1 (`an empty state...`), Task 5 Step 3 |
| A failure of the documents never fails the main sync | Task 3 (`DocsSync` never throws), Task 4 (worker hook) |
| Freshness is Corvo's job (`generatedAt`) | both JSON files carry it; nothing to do here |
