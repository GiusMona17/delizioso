# Online Recipe Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user search TheMealDB by dish name or by ingredient and import a result into the library through the preview screen the app already has.

**Architecture:** A concrete `TheMealDbClient` (no provider interface — one implementation does not justify one) parses TheMealDB's denormalised JSON into the app's existing `StructuredRecipe`. Search results hand off to `ImportPreviewScreen` by setting `ImportUiState.Ready` on the shared `ImportViewModel`, so photo caching, translation, categories and macros all come for free. A small `TheMealDbImporter` registered in the importer registry makes the existing Refresh button work on these recipes too.

**Tech Stack:** Kotlin, Jetpack Compose, Room, kotlinx.serialization, OkHttp, Coil 2.7, JUnit 4 + okhttp-mockwebserver.

**Spec:** `docs/superpowers/specs/2026-08-19-online-recipe-search-design.md`

## Global Constraints

- **No new dependencies.** OkHttp, kotlinx.serialization, Coil and MockWebServer are already present. Do not add Retrofit.
- **API base URL:** `https://www.themealdb.com/api/json/v1/1/` — the trailing slash is part of it. The `1` is the public test key.
- **Every client class takes its base URL as a constructor parameter** with a default, so `MockWebServer` can replace it in tests. This is the existing convention (`TikTokImporter(oEmbedBase = …)`).
- **Every new user-facing string goes in BOTH** `app/src/main/res/values/strings.xml` and `app/src/main/res/values-it/strings.xml`. Apostrophes in Italian strings MUST be escaped as `\'` or the resource compiler fails with "Invalid unicode escape sequence".
- **Code comments in English**, matching the codebase.
- **`servings`, `prepTimeMinutes` and `cookTimeMinutes` stay `null`** for TheMealDB recipes. The API has no such fields. Do not infer them.
- **No `@Serializable` DTO for the full meal object** (54 fields). Read it as a `JsonObject`. The list and filter responses may use DTOs but do not need to.
- Run the full suite with `./gradlew :app:testDebugUnitTest` and build with `./gradlew :app:assembleDebug`.

---

### Task 1: Map a TheMealDB meal to a StructuredRecipe

The pure part, with no network. Everything else depends on this.

**Files:**
- Create: `app/src/main/java/com/delizioso/app/data/search/MealDbMapper.kt`
- Test: `app/src/test/java/com/delizioso/app/data/search/MealDbMapperTest.kt`

**Interfaces:**
- Consumes: `StructuredRecipe`, `IngredientParser.split`, `Categories.canonicalise` (all existing).
- Produces: `MealDbMapper.toRecipe(meal: JsonObject): StructuredRecipe` and `MealDbMapper.mealId(meal: JsonObject): String?`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/delizioso/app/data/search/MealDbMapperTest.kt`:

```kotlin
package com.delizioso.app.data.search

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealDbMapperTest {

    private fun meal(body: String): JsonObject =
        Json.parseToJsonElement(body) as JsonObject

    /** Shape captured live from search.php?s=arrabiata on 2026-08-19. */
    private val arrabiata = meal(
        """
        {
          "idMeal": "52771",
          "strMeal": "Spicy Arrabiata Penne",
          "strCategory": "Vegetarian",
          "strArea": "Italian",
          "strInstructions": "Bring a large pot of water to a boil.\r\nAdd the penne.\r\n\r\n3. Serve immediately.",
          "strMealThumb": "https://www.themealdb.com/images/media/meals/x.jpg",
          "strSource": null,
          "strIngredient1": "penne rigate", "strMeasure1": "1 pound",
          "strIngredient2": "olive oil",    "strMeasure2": "1/4 cup",
          "strIngredient3": "",             "strMeasure3": "",
          "strIngredient4": null,           "strMeasure4": null,
          "strIngredient5": "parsley",      "strMeasure5": " ",
          "strIngredient20": "",            "strMeasure20": ""
        }
        """.trimIndent()
    )

    @Test
    fun `maps the fields the API actually has`() {
        val recipe = MealDbMapper.toRecipe(arrabiata)
        assertEquals("Spicy Arrabiata Penne", recipe.title)
        assertEquals("https://www.themealdb.com/images/media/meals/x.jpg", recipe.imageUrl)
        assertEquals("52771", MealDbMapper.mealId(arrabiata))
    }

    /** Empty slots arrive as "", " " or null anywhere in the 1..20 range. */
    @Test
    fun `skips empty ingredient slots without leaving blanks`() {
        val recipe = MealDbMapper.toRecipe(arrabiata)
        assertEquals(
            listOf("1 pound penne rigate", "1/4 cup olive oil", "parsley"),
            recipe.ingredients.map { it.rawText },
        )
        assertEquals(listOf(0, 1, 2), recipe.ingredients.map { it.position })
    }

    @Test
    fun `splits instructions into steps and drops their numbering`() {
        val recipe = MealDbMapper.toRecipe(arrabiata)
        assertEquals(
            listOf("Bring a large pot of water to a boil.", "Add the penne.", "Serve immediately."),
            recipe.steps,
        )
    }

    /** strCategory and strArea go through the fixed vocabulary; unknowns drop. */
    @Test
    fun `categories are canonicalised and Italian is not one of ours`() {
        assertEquals(listOf("Vegetarian"), MealDbMapper.toRecipe(arrabiata).categories)
    }

    /** The API has no servings or timings. Inventing them would be worse than null. */
    @Test
    fun `servings and times stay null`() {
        val recipe = MealDbMapper.toRecipe(arrabiata)
        assertNull(recipe.servings)
        assertNull(recipe.prepTimeMinutes)
        assertNull(recipe.cookTimeMinutes)
    }

    @Test
    fun `a meal with nothing usable maps to an empty recipe rather than throwing`() {
        val recipe = MealDbMapper.toRecipe(meal("""{"idMeal":"1","strMeal":"X"}"""))
        assertEquals("X", recipe.title)
        assertTrue(recipe.ingredients.isEmpty())
        assertTrue(recipe.steps.isEmpty())
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*MealDbMapperTest*'`
Expected: FAIL — `Unresolved reference 'MealDbMapper'`.

- [ ] **Step 3: Write the mapper**

Create `app/src/main/java/com/delizioso/app/data/search/MealDbMapper.kt`:

```kotlin
package com.delizioso.app.data.search

import com.delizioso.app.data.Categories
import com.delizioso.app.data.import.IngredientParser
import com.delizioso.app.data.import.StructuredRecipe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Turns TheMealDB's denormalised meal object into the app's recipe.
 *
 * The API spreads ingredients across `strIngredient1..20` with a parallel
 * `strMeasure1..20`, leaving unused slots as "", " " or null — anywhere in the
 * range, not only at the end. It has no servings and no timings at all, so those
 * stay null rather than being guessed.
 */
object MealDbMapper {

    /** The API's fixed number of ingredient slots. */
    private const val INGREDIENT_SLOTS = 20

    /** "1. ", "2) ", "STEP 3 - " at the start of an instruction line. */
    private val LEADING_NUMBER = Regex("""^(?:step\s*)?\d{1,2}\s*[.)\-:]\s*""", RegexOption.IGNORE_CASE)

    fun mealId(meal: JsonObject): String? = meal.str("idMeal")

    fun toRecipe(meal: JsonObject): StructuredRecipe = StructuredRecipe(
        title = meal.str("strMeal"),
        imageUrl = meal.str("strMealThumb"),
        ingredients = ingredients(meal),
        steps = steps(meal.str("strInstructions")),
        // The site already classified it; map its words onto our vocabulary.
        categories = Categories.canonicalise(
            listOfNotNull(meal.str("strCategory"), meal.str("strArea"))
        ),
    )

    private fun ingredients(meal: JsonObject) = (1..INGREDIENT_SLOTS)
        .mapNotNull { slot ->
            val name = meal.str("strIngredient$slot") ?: return@mapNotNull null
            listOfNotNull(meal.str("strMeasure$slot"), name).joinToString(" ")
        }
        .mapIndexed { index, line -> IngredientParser.split(line).copy(position = index) }

    private fun steps(instructions: String?): List<String> = instructions.orEmpty()
        .split(Regex("""\r?\n"""))
        .map { it.trim().replace(LEADING_NUMBER, "") }
        .filter { it.isNotEmpty() }

    /** Blank, whitespace-only and the literal string "null" all mean absent. */
    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*MealDbMapperTest*'`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/delizioso/app/data/search/MealDbMapper.kt app/src/test/java/com/delizioso/app/data/search/MealDbMapperTest.kt
git commit -m "feat: map a TheMealDB meal onto the app's recipe model"
```

---

### Task 2: Client — search by name and look up by id

**Files:**
- Create: `app/src/main/java/com/delizioso/app/data/search/TheMealDbClient.kt`
- Test: `app/src/test/java/com/delizioso/app/data/search/TheMealDbClientTest.kt`

**Interfaces:**
- Consumes: `ImportHttp.client`, `newCallSuspend` (both `internal` in `com.delizioso.app.data.import`, same module, so importable), `MealDbMapper` from Task 1.
- Produces:
  - `class TheMealDbClient(client: OkHttpClient = ImportHttp.client, json: Json = …, baseUrl: String = TheMealDbClient.DEFAULT_BASE_URL)`
  - `data class TheMealDbClient.SearchResult(val id: String, val title: String, val thumbnailUrl: String?)`
  - `suspend fun searchByName(query: String): List<JsonObject>`
  - `suspend fun lookup(id: String): JsonObject?`
  - `companion object { const val DEFAULT_BASE_URL }`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/delizioso/app/data/search/TheMealDbClientTest.kt`:

```kotlin
package com.delizioso.app.data.search

import com.delizioso.app.data.import.ImportException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TheMealDbClientTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun client() = TheMealDbClient(baseUrl = server.url("/").toString())

    private fun respond(body: String) = server.enqueue(
        MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    )

    @Test
    fun `searching by name returns the meals and asks the right endpoint`() = runTest {
        respond("""{"meals":[{"idMeal":"1","strMeal":"Penne"}]}""")
        val meals = client().searchByName("penne arrabiata")
        assertEquals(1, meals.size)
        assertEquals("1", MealDbMapper.mealId(meals.first()))
        val request = server.takeRequest()
        assertEquals("/search.php", request.path?.substringBefore("?"))
        assertTrue(request.path!!.contains("s=penne%20arrabiata") || request.path!!.contains("s=penne+arrabiata"))
    }

    /** TheMealDB answers a miss with {"meals":null}, not an empty array. */
    @Test
    fun `no results is an empty list, not an error`() = runTest {
        respond("""{"meals":null}""")
        assertTrue(client().searchByName("nothing").isEmpty())
    }

    @Test
    fun `lookup returns the single meal`() = runTest {
        respond("""{"meals":[{"idMeal":"52771","strMeal":"Penne"}]}""")
        assertEquals("52771", MealDbMapper.mealId(client().lookup("52771")!!))
    }

    @Test
    fun `lookup of an unknown id is null`() = runTest {
        respond("""{"meals":null}""")
        assertNull(client().lookup("999999"))
    }

    @Test
    fun `an http error becomes an ImportException the UI can show`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        assertThrows(ImportException::class.java) { runTest { client().searchByName("x") } }
    }

    @Test
    fun `malformed json becomes an ImportException rather than a crash`() = runTest {
        respond("not json at all")
        assertThrows(ImportException::class.java) { runTest { client().searchByName("x") } }
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*TheMealDbClientTest*'`
Expected: FAIL — `Unresolved reference 'TheMealDbClient'`.

- [ ] **Step 3: Write the client**

Create `app/src/main/java/com/delizioso/app/data/search/TheMealDbClient.kt`:

```kotlin
package com.delizioso.app.data.search

import com.delizioso.app.data.import.ImportException
import com.delizioso.app.data.import.ImportHttp
import com.delizioso.app.data.import.newCallSuspend
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * TheMealDB, the app's one online recipe source.
 *
 * Free, no signup, and the `1` in the path is its public test key. Chosen over
 * Spoonacular and Edamam because it is the only one with no daily quota and no
 * terms limiting how long a recipe may be kept — which matters for a library the
 * user keeps forever. See BACKLOG.md for the full comparison.
 *
 * [baseUrl] is a parameter so MockWebServer can stand in for it in tests.
 */
class TheMealDbClient(
    private val client: OkHttpClient = ImportHttp.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    /** What an ingredient filter gives back: enough for a tile, not for a recipe. */
    data class SearchResult(val id: String, val title: String, val thumbnailUrl: String?)

    /** Full meals — `search.php` returns every field, so no second call is needed. */
    suspend fun searchByName(query: String): List<JsonObject> =
        meals("search.php?s=${encode(query.trim())}")

    /** One full meal, for a result that arrived from an ingredient filter. */
    suspend fun lookup(id: String): JsonObject? = meals("lookup.php?i=${encode(id)}").firstOrNull()

    private suspend fun meals(path: String): List<JsonObject> {
        val request = Request.Builder().url(baseUrl.trimEnd('/') + "/" + path).get().build()
        val response = client.newCallSuspend(request)
        if (!response.isSuccessful) {
            throw ImportException("TheMealDB returned HTTP ${response.code}", retryable = true)
        }
        val body = response.body?.string().orEmpty()
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: throw ImportException("TheMealDB sent something this app could not read", retryable = true)
        // A miss is {"meals":null}, which is not an error.
        val array = root["meals"] as? JsonArray ?: return emptyList()
        return array.filterIsInstance<JsonObject>()
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val DEFAULT_BASE_URL = "https://www.themealdb.com/api/json/v1/1/"
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*TheMealDbClientTest*'`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/delizioso/app/data/search/TheMealDbClient.kt app/src/test/java/com/delizioso/app/data/search/TheMealDbClientTest.kt
git commit -m "feat: TheMealDB client for search by name and lookup by id"
```

---

### Task 3: Client — ingredient list and multi-ingredient intersection

TheMealDB's multi-ingredient filter is behind a paid tier. One call per ingredient, intersected in the app, gets the same answer for free.

**Files:**
- Modify: `app/src/main/java/com/delizioso/app/data/search/TheMealDbClient.kt`
- Test: `app/src/test/java/com/delizioso/app/data/search/TheMealDbClientTest.kt` (append)

**Interfaces:**
- Produces, added to `TheMealDbClient`:
  - `suspend fun ingredientNames(): List<String>`
  - `suspend fun mealsWithIngredient(ingredient: String): List<SearchResult>`
  - `companion object { fun intersect(perIngredient: List<List<SearchResult>>): List<SearchResult> }`

- [ ] **Step 1: Write the failing test**

Append to `TheMealDbClientTest.kt`, inside the class:

```kotlin
    @Test
    fun `ingredient names come back sorted and without blanks`() = runTest {
        respond("""{"meals":[{"strIngredient":"Salmon"},{"strIngredient":" "},{"strIngredient":"Beef"}]}""")
        assertEquals(listOf("Beef", "Salmon"), client().ingredientNames())
    }

    /** TheMealDB wants underscores where the ingredient name has spaces. */
    @Test
    fun `filtering by ingredient sends underscores and maps the tile fields`() = runTest {
        respond("""{"meals":[{"idMeal":"7","strMeal":"Chicken Pie","strMealThumb":"https://x/y.jpg"}]}""")
        val results = client().mealsWithIngredient("chicken breast")
        assertEquals(listOf(TheMealDbClient.SearchResult("7", "Chicken Pie", "https://x/y.jpg")), results)
        val path = server.takeRequest().path!!
        assertEquals("/filter.php", path.substringBefore("?"))
        assertTrue(path.contains("i=chicken_breast"))
    }

    @Test
    fun `intersecting keeps only meals present for every ingredient`() {
        val a = TheMealDbClient.SearchResult("1", "A", null)
        val b = TheMealDbClient.SearchResult("2", "B", null)
        val c = TheMealDbClient.SearchResult("3", "C", null)
        assertEquals(
            listOf(b),
            TheMealDbClient.intersect(listOf(listOf(a, b), listOf(b, c))),
        )
    }

    @Test
    fun `intersecting one list returns it unchanged, and no lists returns nothing`() {
        val a = TheMealDbClient.SearchResult("1", "A", null)
        assertEquals(listOf(a), TheMealDbClient.intersect(listOf(listOf(a))))
        assertTrue(TheMealDbClient.intersect(emptyList()).isEmpty())
    }

    /** Three ingredients often match nothing in a ~300 recipe catalogue. */
    @Test
    fun `an empty intersection is empty, not the first list`() {
        val a = TheMealDbClient.SearchResult("1", "A", null)
        val b = TheMealDbClient.SearchResult("2", "B", null)
        assertTrue(TheMealDbClient.intersect(listOf(listOf(a), listOf(b))).isEmpty())
    }
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*TheMealDbClientTest*'`
Expected: FAIL — `Unresolved reference 'ingredientNames'`.

- [ ] **Step 3: Add the three members**

In `TheMealDbClient.kt`, add these two methods after `lookup`:

```kotlin
    /**
     * Every ingredient the catalogue knows — 992 of them.
     *
     * Fetched so the search screen can offer them as suggestions: the catalogue is
     * English-only, and choosing from a list beats guessing that "cipollotto" is
     * "spring onions". Never cached to disk, because ingredient search needs the
     * network anyway.
     */
    suspend fun ingredientNames(): List<String> = meals("list.php?i=list")
        .mapNotNull { (it["strIngredient"] as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }
        .sorted()

    /** Meals containing one ingredient. Returns tiles only — no ingredients, no steps. */
    suspend fun mealsWithIngredient(ingredient: String): List<SearchResult> {
        val slug = ingredient.trim().replace(' ', '_')
        return meals("filter.php?i=${encode(slug)}").mapNotNull { meal ->
            val id = MealDbMapper.mealId(meal) ?: return@mapNotNull null
            val title = (meal["strMeal"] as? JsonPrimitive)?.content?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            SearchResult(
                id = id,
                title = title,
                thumbnailUrl = (meal["strMealThumb"] as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty),
            )
        }
    }
```

Add the import `kotlinx.serialization.json.JsonPrimitive`, and extend the companion object:

```kotlin
    companion object {
        const val DEFAULT_BASE_URL = "https://www.themealdb.com/api/json/v1/1/"

        /**
         * Meals present in every list.
         *
         * TheMealDB's own multi-ingredient filter is behind a paid tier, so one
         * call per ingredient and an intersection here buys the same answer.
         */
        fun intersect(perIngredient: List<List<SearchResult>>): List<SearchResult> {
            if (perIngredient.isEmpty()) return emptyList()
            val shared = perIngredient
                .map { list -> list.mapTo(mutableSetOf()) { it.id } }
                .reduce { acc, ids -> acc intersect ids }
            return perIngredient.first().filter { it.id in shared }
        }
    }
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*TheMealDbClientTest*'`
Expected: PASS, 11 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/delizioso/app/data/search/TheMealDbClient.kt app/src/test/java/com/delizioso/app/data/search/TheMealDbClientTest.kt
git commit -m "feat: ingredient list and client-side multi-ingredient intersection"
```

---

### Task 4: Make Refresh work on TheMealDB recipes

Without this, these would be the only recipes in the library whose "Refresh" button cannot work, because `strSource` is often null and there would be no source URL.

**Files:**
- Modify: `app/src/main/java/com/delizioso/app/data/import/PlatformDetector.kt` (enum + detector)
- Modify: `app/src/main/java/com/delizioso/app/data/local/Entities.kt` (the `Platform` **object** of string constants — a different type with the same name)
- Create: `app/src/main/java/com/delizioso/app/data/import/TheMealDbImporter.kt`
- Modify: `app/src/main/java/com/delizioso/app/DeliziosoApplication.kt` (register it)
- Test: `app/src/test/java/com/delizioso/app/data/import/TheMealDbImporterTest.kt`

**Careful:** there are two things called `Platform`. `com.delizioso.app.data.import.Platform` is an **enum** used by importers; `com.delizioso.app.data.local.Platform` is an **object of String constants** stored in `SourceEntity.platform`. Both need a MEALDB entry.

**Interfaces:**
- Consumes: `TheMealDbClient` (Tasks 2–3), `MealDbMapper` (Task 1), `RecipeImporter`, `RawImport`, `ImportContent`.
- Produces:
  - `Platform.MEALDB` in both types
  - `PlatformDetector.mealDbId(rawUrl: String): String?`
  - `class TheMealDbImporter(client: TheMealDbClient = TheMealDbClient()) : RecipeImporter`
  - `TheMealDbImporter.webUrl(id: String): String` — the canonical source URL to store

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/delizioso/app/data/import/TheMealDbImporterTest.kt`:

```kotlin
package com.delizioso.app.data.import

import com.delizioso.app.data.search.TheMealDbClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class TheMealDbImporterTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun `detects a themealdb recipe url and its id`() {
        assertEquals(Platform.MEALDB, PlatformDetector.detect("https://www.themealdb.com/meal/52771"))
        assertEquals("52771", PlatformDetector.mealDbId("https://www.themealdb.com/meal/52771"))
        assertEquals("52771", PlatformDetector.mealDbId("themealdb.com/meal/52771/spicy-penne"))
        assertNull(PlatformDetector.mealDbId("https://example.com/meal/52771"))
    }

    /** A blog URL must keep going to the blog importer. */
    @Test
    fun `an ordinary link is still a blog`() {
        assertEquals(Platform.BLOG, PlatformDetector.detect("https://ricette.giallozafferano.it/x.html"))
    }

    @Test
    fun `fetching returns a structured recipe, not raw text`() = runTest {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"meals":[{"idMeal":"52771","strMeal":"Penne","strInstructions":"Boil.",
                   "strIngredient1":"penne","strMeasure1":"1 pound"}]}"""
            )
        )
        val importer = TheMealDbImporter(TheMealDbClient(baseUrl = server.url("/").toString()))
        val raw = importer.fetch("https://www.themealdb.com/meal/52771")

        assertEquals(Platform.MEALDB, importer.platform)
        assertEquals("https://www.themealdb.com/meal/52771", raw.url)
        assertEquals("TheMealDB", raw.author)
        val recipe = (raw.content as ImportContent.Structured).recipe
        assertEquals("Penne", recipe.title)
        assertEquals(listOf("1 pound penne"), recipe.ingredients.map { it.rawText })
        assertEquals(listOf("Boil."), recipe.steps)
    }

    @Test
    fun `an unknown id fails with a message the screen can show`() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"meals":null}"""))
        val importer = TheMealDbImporter(TheMealDbClient(baseUrl = server.url("/").toString()))
        assertThrows(ImportException::class.java) {
            runTest { importer.fetch("https://www.themealdb.com/meal/999999") }
        }
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*TheMealDbImporterTest*'`
Expected: FAIL — `Unresolved reference 'MEALDB'`.

- [ ] **Step 3: Add the enum value, the detector, and the string constant**

In `PlatformDetector.kt`, add to the enum:

```kotlin
    BLOG("BLOG"),
    MEALDB("MEALDB"),
```

Add the pattern beside the others:

```kotlin
    private val mealDb = Regex(
        """^(?:https?://)?(?:www\.)?themealdb\.com/meal/(\d+)"""
    )
```

Add the branch to `detect`, **above** the `startsWith("http")` blog fallback, or every TheMealDB link becomes a blog:

```kotlin
            mealDb.find(url) != null -> Platform.MEALDB
```

Add the accessor beside `youtubeId`:

```kotlin
    fun mealDbId(rawUrl: String): String? = mealDb.find(rawUrl.trim())?.groupValues?.get(1)
```

In `data/local/Entities.kt`, add to the `Platform` **object**:

```kotlin
    const val MEALDB = "MEALDB"
```

and extend the KDoc line above it to read:

```kotlin
    /** MANUAL | INSTAGRAM | FACEBOOK | TIKTOK | YOUTUBE | BLOG | OCR | MEALDB. */
```

- [ ] **Step 4: Write the importer**

Create `app/src/main/java/com/delizioso/app/data/import/TheMealDbImporter.kt`:

```kotlin
package com.delizioso.app.data.import

import com.delizioso.app.data.search.MealDbMapper
import com.delizioso.app.data.search.TheMealDbClient

/**
 * Imports a recipe from a `themealdb.com/meal/<id>` link.
 *
 * It exists so that recipes found through online search can be refreshed like any
 * other: their stored source URL is this one, and [SourceRefresher] resolves it
 * through the same registry as every other link. It also makes those URLs
 * pasteable in the import field.
 */
class TheMealDbImporter(
    private val client: TheMealDbClient = TheMealDbClient(),
) : RecipeImporter {

    override val platform: Platform = Platform.MEALDB

    override suspend fun fetch(rawUrl: String): RawImport {
        val id = PlatformDetector.mealDbId(rawUrl)
            ?: throw ImportException("Not a valid TheMealDB link")
        val meal = client.lookup(id)
            ?: throw ImportException("TheMealDB has no recipe with id $id", retryable = true)
        val recipe = MealDbMapper.toRecipe(meal)
        return RawImport(
            platform = platform.key,
            url = webUrl(id),
            author = AUTHOR,
            content = ImportContent.Structured(recipe),
            thumbnailUrl = recipe.imageUrl,
        )
    }

    companion object {
        const val AUTHOR = "TheMealDB"

        /** The canonical, human-viewable page — stored as the recipe's source. */
        fun webUrl(id: String): String = "https://www.themealdb.com/meal/$id"
    }
}
```

- [ ] **Step 5: Register it**

In `DeliziosoApplication.kt`, add `TheMealDbImporter()` to the `RecipeImporterRegistry` list, after `BlogImporter()`:

```kotlin
            BlogImporter(),
            TheMealDbImporter(),
```

and add the import `com.delizioso.app.data.import.TheMealDbImporter`.

- [ ] **Step 6: Run the tests and confirm they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*TheMealDbImporterTest*'`
Expected: PASS, 4 tests.

Then run the whole suite to prove the new `Platform` branch broke nothing:
Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — in particular `PlatformDetectorTest`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/delizioso/app/data/import/ app/src/main/java/com/delizioso/app/data/local/Entities.kt app/src/main/java/com/delizioso/app/DeliziosoApplication.kt app/src/test/java/com/delizioso/app/data/import/TheMealDbImporterTest.kt
git commit -m "feat: themealdb.com links import and refresh like any other source"
```

---

### Task 5: Hand a search result to the existing preview

**Files:**
- Modify: `app/src/main/java/com/delizioso/app/ui/screens/import/ImportViewModel.kt`
- Modify: `app/src/main/java/com/delizioso/app/data/search/MealDbMapper.kt`
- Test: `app/src/test/java/com/delizioso/app/data/search/SearchResultHandoffTest.kt`

**Interfaces:**
- Produces: `fun ImportViewModel.importSearchResult(recipe: StructuredRecipe, raw: RawImport)`

Note: `ImportViewModel` needs an Android `Context`, so it cannot be constructed in a plain unit test. The testable part is the `RawImport` a search result becomes, which is a pure function — put that in the search package and test it there.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/delizioso/app/data/search/SearchResultHandoffTest.kt`:

```kotlin
package com.delizioso.app.data.search

import com.delizioso.app.data.import.ImportContent
import com.delizioso.app.data.import.Platform
import com.delizioso.app.data.import.TheMealDbImporter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultHandoffTest {

    @Test
    fun `a meal becomes a RawImport carrying its source url`() {
        val meal = Json.parseToJsonElement(
            """{"idMeal":"52771","strMeal":"Penne","strMealThumb":"https://x/y.jpg"}"""
        ) as JsonObject

        val raw = MealDbMapper.toRawImport(meal)

        assertEquals(Platform.MEALDB.key, raw.platform)
        assertEquals("https://www.themealdb.com/meal/52771", raw.url)
        assertEquals(TheMealDbImporter.AUTHOR, raw.author)
        assertEquals("https://x/y.jpg", raw.thumbnailUrl)
        assertEquals("Penne", (raw.content as ImportContent.Structured).recipe.title)
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*SearchResultHandoffTest*'`
Expected: FAIL — `Unresolved reference 'toRawImport'`.

- [ ] **Step 3: Add `toRawImport` to the mapper**

In `MealDbMapper.kt`, add:

```kotlin
    /**
     * The meal as the import flow expects it, source URL and all.
     *
     * Search results travel to the preview screen as a [RawImport] so that saving,
     * photo caching and the source link behave exactly as they do for a pasted
     * link — one path, not two.
     */
    fun toRawImport(meal: JsonObject): RawImport {
        val recipe = toRecipe(meal)
        val id = mealId(meal)
        return RawImport(
            platform = Platform.MEALDB.key,
            url = id?.let(TheMealDbImporter::webUrl),
            author = TheMealDbImporter.AUTHOR,
            content = ImportContent.Structured(recipe),
            thumbnailUrl = recipe.imageUrl,
        )
    }
```

with the imports `com.delizioso.app.data.import.ImportContent`, `com.delizioso.app.data.import.Platform`, `com.delizioso.app.data.import.RawImport`, `com.delizioso.app.data.import.TheMealDbImporter`.

- [ ] **Step 4: Add the view-model entry point**

In `ImportViewModel.kt`, add beside `importText`:

```kotlin
    /**
     * Show a recipe found through online search in the preview.
     *
     * The search screen owns finding it; from here on it is an import like any
     * other, which is why this only sets the state the preview already reads.
     */
    fun importSearchResult(recipe: StructuredRecipe, raw: RawImport) {
        lastRaw = raw
        lastUrl = raw.url
        _state.value = ImportUiState.Ready(recipe = recipe, raw = raw)
    }
```

- [ ] **Step 5: Run the tests and confirm they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*SearchResultHandoffTest*'`
Expected: PASS, 1 test.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/delizioso/app/data/search/MealDbMapper.kt app/src/main/java/com/delizioso/app/ui/screens/import/ImportViewModel.kt app/src/test/java/com/delizioso/app/data/search/SearchResultHandoffTest.kt
git commit -m "feat: a search result enters the library through the existing preview"
```

---

### Task 6: Give Coil the app's own HTTP client

Remote thumbnails currently load without the browser User-Agent that every other request in the app carries. The results grid is the first screen made mostly of remote images, so this belongs here.

**Files:**
- Modify: `app/src/main/java/com/delizioso/app/DeliziosoApplication.kt`

**Interfaces:**
- Produces: `DeliziosoApplication` implements `coil.ImageLoaderFactory`. No call site changes — Coil picks the factory up automatically.

- [ ] **Step 1: Implement the factory**

In `DeliziosoApplication.kt`, change the class declaration and add the override:

```kotlin
class DeliziosoApplication : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * Coil otherwise builds its own HTTP client, so remote thumbnails went out
     * without the browser User-Agent every other request in the app carries —
     * which some recipe sites answer with a 403 for hotlinked images.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(ImportHttp.client)
        .crossfade(true)
        .build()
}
```

Add the imports:

```kotlin
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.delizioso.app.data.import.ImportHttp
```

- [ ] **Step 2: Build and confirm it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Verify on the device**

Install and open the library. Recipes with a remote thumbnail must still show their photo — a regression here would show as placeholders where images used to be.

```bash
./gradlew :app:installDebug
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/delizioso/app/DeliziosoApplication.kt
git commit -m "fix: Coil loads images through the app's own HTTP client"
```

---

### Task 7: Search view model

**Files:**
- Create: `app/src/main/java/com/delizioso/app/ui/screens/search/OnlineSearchViewModel.kt`

**Interfaces:**
- Consumes: `TheMealDbClient` (Tasks 2–3), `MealDbMapper.toRawImport` (Task 5).
- Produces:
  - `sealed interface SearchUiState { Idle, Loading, Results(val results: List<TheMealDbClient.SearchResult>), Empty(val ingredients: List<String>), Failed(val message: String) }`
  - `class OnlineSearchViewModel(client: TheMealDbClient)` with:
    - `val state: StateFlow<SearchUiState>`
    - `val ingredientNames: StateFlow<List<String>>`
    - `val chosenIngredients: StateFlow<List<String>>`
    - `fun searchByName(query: String)`
    - `fun addIngredient(name: String)` / `fun removeIngredient(name: String)`
    - `fun openResult(id: String, onReady: (StructuredRecipe, RawImport) -> Unit)`
    - `companion object { val Factory: ViewModelProvider.Factory }`

- [ ] **Step 1: Write the view model**

Create `app/src/main/java/com/delizioso/app/ui/screens/search/OnlineSearchViewModel.kt`:

```kotlin
package com.delizioso.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.delizioso.app.data.import.RawImport
import com.delizioso.app.data.import.StructuredRecipe
import com.delizioso.app.data.search.MealDbMapper
import com.delizioso.app.data.search.TheMealDbClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the search screen is showing. */
sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Results(val results: List<TheMealDbClient.SearchResult>) : SearchUiState
    /** Nothing matched. [ingredients] names the combination, so the user can undo one. */
    data class Empty(val ingredients: List<String>) : SearchUiState
    data class Failed(val message: String) : SearchUiState
}

class OnlineSearchViewModel(
    private val client: TheMealDbClient,
) : ViewModel() {

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /** The 992 names offered as suggestions; empty when the list could not load. */
    private val _ingredientNames = MutableStateFlow<List<String>>(emptyList())
    val ingredientNames: StateFlow<List<String>> = _ingredientNames.asStateFlow()

    private val _chosenIngredients = MutableStateFlow<List<String>>(emptyList())
    val chosenIngredients: StateFlow<List<String>> = _chosenIngredients.asStateFlow()

    init {
        // Failure is not fatal: the picker falls back to free text.
        viewModelScope.launch {
            _ingredientNames.value = runCatching { client.ingredientNames() }.getOrDefault(emptyList())
        }
    }

    fun searchByName(query: String) {
        if (query.isBlank()) return
        _chosenIngredients.value = emptyList()
        viewModelScope.launch {
            _state.value = SearchUiState.Loading
            _state.value = try {
                val meals = client.searchByName(query)
                if (meals.isEmpty()) {
                    SearchUiState.Empty(emptyList())
                } else {
                    SearchUiState.Results(
                        meals.mapNotNull { meal ->
                            val id = MealDbMapper.mealId(meal) ?: return@mapNotNull null
                            val recipe = MealDbMapper.toRecipe(meal)
                            TheMealDbClient.SearchResult(id, recipe.title.orEmpty(), recipe.imageUrl)
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SearchUiState.Failed(e.message.orEmpty())
            }
        }
    }

    fun addIngredient(name: String) {
        if (name.isBlank() || name in _chosenIngredients.value) return
        _chosenIngredients.value = _chosenIngredients.value + name
        searchByIngredients()
    }

    fun removeIngredient(name: String) {
        _chosenIngredients.value = _chosenIngredients.value - name
        if (_chosenIngredients.value.isEmpty()) _state.value = SearchUiState.Idle else searchByIngredients()
    }

    /** One request per ingredient, in parallel, then the intersection. */
    private fun searchByIngredients() {
        val chosen = _chosenIngredients.value
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            _state.value = SearchUiState.Loading
            _state.value = try {
                val perIngredient = chosen
                    .map { name -> async { client.mealsWithIngredient(name) } }
                    .awaitAll()
                val shared = TheMealDbClient.intersect(perIngredient)
                if (shared.isEmpty()) SearchUiState.Empty(chosen) else SearchUiState.Results(shared)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SearchUiState.Failed(e.message.orEmpty())
            }
        }
    }

    /**
     * Fetch the full recipe for a chosen result and hand it back.
     *
     * Ingredient results carry only a name and a thumbnail, so the recipe itself
     * is fetched here — on tap, rather than for every result in the grid.
     */
    fun openResult(id: String, onReady: (StructuredRecipe, RawImport) -> Unit) {
        viewModelScope.launch {
            _state.value = SearchUiState.Loading
            try {
                val meal = client.lookup(id)
                if (meal == null) {
                    _state.value = SearchUiState.Failed("")
                    return@launch
                }
                onReady(MealDbMapper.toRecipe(meal), MealDbMapper.toRawImport(meal))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = SearchUiState.Failed(e.message.orEmpty())
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { OnlineSearchViewModel(TheMealDbClient()) }
        }
    }
}
```

- [ ] **Step 2: Build and confirm it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/delizioso/app/ui/screens/search/OnlineSearchViewModel.kt
git commit -m "feat: online search view model with ingredient chips and intersection"
```

---

### Task 8: The search screen, and the way in

Compose UI. The project has no instrumentation tests and this is not the feature that should introduce them, so this task's deliverable is a build plus a named on-device check.

**Files:**
- Create: `app/src/main/java/com/delizioso/app/ui/screens/search/OnlineSearchScreen.kt` (screen + `SearchResultTile`)
- Modify: `app/src/main/java/com/delizioso/app/ui/navigation/DeliziosoNav.kt` (add the route constant)
- Modify: `app/src/main/java/com/delizioso/app/ui/DeliziosoApp.kt` (register the destination)
- Modify: `app/src/main/java/com/delizioso/app/ui/screens/import/ImportScreen.kt` (the card that opens it)
- Modify: `app/src/main/res/values/strings.xml` and `app/src/main/res/values-it/strings.xml`

**Interfaces:**
- Consumes: `OnlineSearchViewModel` (Task 7), `ImportViewModel.importSearchResult` (Task 5), existing `ClayTextField`, `ClayButton`, `ClayChip`, `ClayTopBar`, `ClayEmptyState`, `RecipeImage`, `clayCard`.
- Produces: `Routes.IMPORT_SEARCH = "importSearch"`, `OnlineSearchScreen(viewModel, importViewModel, onBack, onPreview)`.

**Navigation, precisely.** `ImportPreviewScreen` reads `ImportViewModel` scoped to the `Routes.IMPORT` back stack entry. The search destination must use that same scoped instance, or the preview will show nothing:

```kotlin
val parentEntry = navController.getBackStackEntry(Routes.IMPORT)
```

The search screen navigates to the preview itself after `importSearchResult` — `ImportScreen`'s own `LaunchedEffect(state)` will not fire, because its composition is gone once the search destination is on top.

- [ ] **Step 1: Add the strings**

In `app/src/main/res/values/strings.xml`, before `</resources>`:

```xml
    <string name="import_search_title">Search online</string>
    <string name="import_search_body">Find a recipe by name or by what you have in the fridge, from TheMealDB.</string>
    <string name="import_search_open">Search online</string>
    <string name="search_title">Search online</string>
    <string name="search_by_name">By name</string>
    <string name="search_by_ingredient">By ingredient</string>
    <string name="search_name_placeholder">Dish name, in English…</string>
    <string name="search_ingredient_placeholder">Ingredient, in English…</string>
    <string name="search_action">Search</string>
    <string name="search_empty_name">No recipe by that name. The catalogue is in English — try "chicken" rather than "pollo".</string>
    <string name="search_empty_ingredients">No recipe has all of: %1$s. The catalogue holds about 300 recipes, so try removing one.</string>
    <string name="search_failed">Search failed: %1$s</string>
    <string name="search_failed_unknown">The search did not work. Check your connection and try again.</string>
    <string name="search_no_ingredient_list">Ingredient suggestions could not be loaded. Type the English name instead.</string>
```

In `app/src/main/res/values-it/strings.xml`, before `</resources>` — note the escaped apostrophes:

```xml
    <string name="import_search_title">Cerca online</string>
    <string name="import_search_body">Trova una ricetta per nome o per quello che hai in frigo, da TheMealDB.</string>
    <string name="import_search_open">Cerca online</string>
    <string name="search_title">Cerca online</string>
    <string name="search_by_name">Per nome</string>
    <string name="search_by_ingredient">Per ingrediente</string>
    <string name="search_name_placeholder">Nome del piatto, in inglese…</string>
    <string name="search_ingredient_placeholder">Ingrediente, in inglese…</string>
    <string name="search_action">Cerca</string>
    <string name="search_empty_name">Nessuna ricetta con quel nome. Il catalogo è in inglese: prova "chicken" invece di "pollo".</string>
    <string name="search_empty_ingredients">Nessuna ricetta ha tutti questi: %1$s. Il catalogo ha circa 300 ricette, prova a toglierne uno.</string>
    <string name="search_failed">Ricerca non riuscita: %1$s</string>
    <string name="search_failed_unknown">La ricerca non ha funzionato. Controlla la connessione e riprova.</string>
    <string name="search_no_ingredient_list">Non è stato possibile caricare i suggerimenti. Scrivi il nome in inglese.</string>
```

- [ ] **Step 2: Write the screen**

Create `app/src/main/java/com/delizioso/app/ui/screens/search/OnlineSearchScreen.kt`.

It contains two composables:

`SearchResultTile(title: String, thumbnailUrl: String?, onClick: () -> Unit, modifier: Modifier)` — a clay card with a 120dp `RecipeImage` and the title below, two lines maximum. **Do not reuse `ClayRecipeTile`:** that binds to `RecipeWithDetails`, a persisted entity, and a search result has no database row.

`OnlineSearchScreen(viewModel, importViewModel, onBack, onPreview)` — a `ClayTopBar` with a back arrow, `ClaySegmentedTabs` for "By name" / "By ingredient", then:

- **By name:** a `ClayTextField` plus a `ClayButton` calling `viewModel.searchByName(query)`.
- **By ingredient:** a `ClayTextField` whose value filters `ingredientNames` (case-insensitive `contains`), showing at most 8 suggestions as `ClayChip`s that call `addIngredient`; chosen ingredients render as removable chips calling `removeIngredient`. When `ingredientNames` is empty, show `search_no_ingredient_list` and let the typed text be submitted directly.
- **Results:** `LazyVerticalGrid(columns = GridCells.Fixed(2))` of `SearchResultTile`, each calling
  `viewModel.openResult(id) { recipe, raw -> importViewModel.importSearchResult(recipe, raw); onPreview() }`.
- **Loading:** `CircularProgressIndicator`.
- **Empty:** `ClayEmptyState` with `search_empty_name` or `search_empty_ingredients` formatted with the joined chosen ingredients.
- **Failed:** the error card pattern from `ImportScreen`, with a retry that re-runs the last action. `Failed.message` is blank when the failure had no message of its own (a lookup that came back empty), so render `search_failed` only when it is non-blank and `search_failed_unknown` otherwise — never a dangling colon.

- [ ] **Step 3: Add the route**

In `DeliziosoNav.kt`, inside `object Routes`:

```kotlin
    const val IMPORT_SEARCH = "importSearch"
```

In `DeliziosoApp.kt`, after the `Routes.IMPORT_PREVIEW` destination:

```kotlin
            composable(Routes.IMPORT_SEARCH) {
                val parentEntry = navController.getBackStackEntry(Routes.IMPORT)
                OnlineSearchScreen(
                    importViewModel = viewModel(viewModelStoreOwner = parentEntry, factory = ImportViewModel.Factory),
                    onBack = { navController.popBackStack() },
                    onPreview = { navController.navigate(Routes.IMPORT_PREVIEW) },
                )
            }
```

with the import `com.delizioso.app.ui.screens.search.OnlineSearchScreen`.

- [ ] **Step 4: Add the card that opens it**

In `ImportScreen.kt`, add a parameter `onSearchOnline: () -> Unit` and render a card between `PasteTextCard` and the platform chips row: title `import_search_title`, body `import_search_body`, and a full-width `ClayButton` with `Icons.Filled.TravelExplore` labelled `import_search_open` calling `onSearchOnline`.

In `DeliziosoApp.kt`, pass `onSearchOnline = { navController.navigate(Routes.IMPORT_SEARCH) }` to `ImportScreen`.

- [ ] **Step 5: Build and run the whole suite**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, all tests passing.

- [ ] **Step 6: Verify on the device**

```bash
./gradlew :app:installDebug
```

Check each of these by hand:

1. Import → "Search online" opens the screen.
2. By name, "arrabiata" returns at least one tile with its photo.
3. Tapping it opens the preview with ingredients and steps filled in; Save adds it to the library.
4. The saved recipe's source reads TheMealDB and **"Refresh" works on it**.
5. The macro panel reads "For the whole recipe (no serving count)" — expected, since the API has no servings.
6. By ingredient, "chicken" returns results; adding "mushrooms" narrows or empties them, and an empty result names both ingredients.
7. Aeroplane mode: searching shows the failure message and Retry, not a crash.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/delizioso/app/ui/ app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml
git commit -m "feat: online recipe search screen, reached from Import"
```

---

## Self-review

**Spec coverage.** §4 architecture → Tasks 1–3, 7, 8. §5 name search → Tasks 2, 8. §6 ingredient picker and intersection → Tasks 3, 7, 8. §7 mapping → Task 1; source and refresh → Task 4. §8 image loading → Task 6. §9 error states → Tasks 7, 8. §10 testing → Tasks 1–5. The hand-off named in §4 → Task 5. No spec section is unimplemented.

**Naming consistency.** `MealDbMapper.toRecipe` / `mealId` / `toRawImport`; `TheMealDbClient.searchByName` / `lookup` / `ingredientNames` / `mealsWithIngredient` / `intersect`; `SearchResult(id, title, thumbnailUrl)`; `Platform.MEALDB` in both the enum and the string-constant object; `TheMealDbImporter.webUrl` / `AUTHOR`. Each is defined once and referenced with the same signature everywhere.

**Known deviation from TDD.** Tasks 6, 7 and 8 have no unit tests. Task 6 is a four-line Application override, Task 7 is a view model whose collaborators are already covered, and Task 8 is Compose UI in a project with no UI test infrastructure. Rather than write tests that assert nothing, those tasks end with a build and the named device checks in Task 8 Step 6.
