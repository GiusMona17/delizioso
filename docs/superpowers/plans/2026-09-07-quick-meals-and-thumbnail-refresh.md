# Quick Meals Planning & Source Thumbnail Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable automatic source thumbnail downloading during recipe refresh ("Aggiorna"), and add smart quick meal planning for non-recipe items (e.g., milk and biscuits, frozen fish) with on-device / external AI estimation and macro tracking in the meal planner.

**Architecture:** 
- Source thumbnail refresh: `RecipeDetailViewModel` downloads the fresh source cover image via `ImageStore.downloadToInternal` on IO dispatcher, sets it via `RecipeRepository.setImage`, and purges old local images.
- Data layer: Room migration `MIGRATION_7_8` (v7 → v8) makes `PlannedMealEntity.recipeId` nullable and adds direct custom macro and title columns.
- Nutrition: `NutritionAggregator` aggregates custom macros from quick meals alongside recipe-calculated macros.
- AI & UI: `QuickMealPrompt` and `QuickMealJsonParser` support ChatGPT/Claude external round-trips while `QuickMealEstimator` leverages on-device Gemini Nano when available; `QuickMealSheet` and `AddMealChooserSheet` provide intuitive entry points in `PlannerScreen`.

**Tech Stack:** Kotlin, Jetpack Compose, Room (SQLite), Google AICore / Gemini Nano, Coroutines / Flow.

**Spec:** [`docs/superpowers/specs/2026-09-07-quick-meals-and-source-thumbnail-refresh-design.md`](file:///d:/Projects/Coding/delizioso/docs/superpowers/specs/2026-09-07-quick-meals-and-source-thumbnail-refresh-design.md)

## Global Constraints
- Do not break existing recipe foreign keys or cascading deletes.
- Room table recreations must preserve existing `planned_meals` data.
- UI components must follow Delizioso clay design language (`ClayCard`, `ClayButton`, `ClayTextField`, `clayBevel`, `PillShape`).
- All strings must be localized in both `values/strings.xml` (English) and `values-it/strings.xml` (Italian).

---

### Task 1: Source Thumbnail Refresh on "Aggiorna"

**Files:**
- Modify: `app/src/main/java/com/delizioso/app/ui/screens/detail/RecipeDetailScreen.kt:149-198`
- Modify: `app/src/main/res/values/strings.xml:427-434`
- Modify: `app/src/main/res/values-it/strings.xml:425-432`

**Interfaces:**
- Consumes: `SourceRefresher.refetch(url): Refreshed`, `ImageStore.downloadToInternal(context, url): String?`, `ImageStore.deleteIfOwned(context, path)`
- Produces: Updated recipe photo in `RecipeRepository` and UI when refreshing from source

- [ ] **Step 1: Update confirmation dialog strings in `strings.xml` and `values-it/strings.xml`**

In `app/src/main/res/values/strings.xml`:
```xml
    <string name="detail_refresh_body">The ingredients, steps and cover photo will be replaced with whatever the source says now. Favourites and planner entries are kept. Any edits you made by hand will be lost.</string>
```

In `app/src/main/res/values-it/strings.xml`:
```xml
    <string name="detail_refresh_body">Ingredienti, passaggi e foto di copertina saranno sostituiti con quanto dice ora la fonte. Preferiti e voci del pianificatore restano. Le modifiche fatte a mano andranno perse.</string>
```

- [ ] **Step 2: Update `RecipeDetailViewModel` constructor & `refreshFromSource`**

In `app/src/main/java/com/delizioso/app/ui/screens/detail/RecipeDetailScreen.kt`:
Pass `context: android.content.Context` to `RecipeDetailViewModel` in its constructor and factory:
```kotlin
class RecipeDetailViewModel(
    private val resources: android.content.res.Resources,
    private val repository: RecipeRepository,
    private val chatModel: RecipeChat,
    private val preferences: UserPreferences,
    private val refresher: SourceRefresher,
    private val context: android.content.Context,
    private val recipeId: Long,
) : ViewModel() {
```
In `factory`:
```kotlin
                RecipeDetailViewModel(
                    resources = app.resources,
                    repository = app.container.recipeRepository,
                    chatModel = app.container.recipeChat,
                    refresher = app.container.sourceRefresher,
                    preferences = app.container.preferences,
                    context = app.applicationContext,
                    recipeId = recipeId,
                )
```
In `refreshFromSource()`:
```kotlin
    fun refreshFromSource() {
        val d = details.value ?: return
        val url = d.source?.url.orEmpty()
        viewModelScope.launch {
            _refresh.value = RefreshState.Running
            try {
                val fresh = refresher.refetch(url)
                val preserved = fresh.recipe.copy(
                    servings = fresh.recipe.servings ?: d.recipe.servings,
                    prepTimeMinutes = fresh.recipe.prepTimeMinutes ?: d.recipe.prepTimeMinutes,
                    cookTimeMinutes = fresh.recipe.cookTimeMinutes ?: d.recipe.cookTimeMinutes,
                )
                repository.update(
                    recipeId,
                    preserved,
                    fresh.recipe.categories.ifEmpty { d.tags.map { it.name } },
                )
                fresh.rawText?.let { repository.updateSourceText(recipeId, it) }

                // Download and update thumbnail if source provided an image
                val imageUrl = fresh.recipe.imageUrl
                if (!imageUrl.isNullOrBlank()) {
                    val downloaded = withContext(Dispatchers.IO) {
                        ImageStore.downloadToInternal(context, imageUrl)
                    }
                    if (downloaded != null) {
                        val previous = repository.currentImage(recipeId)
                        repository.setImage(recipeId, downloaded)
                        withContext(Dispatchers.IO) {
                            ImageStore.deleteIfOwned(context, previous)
                        }
                    }
                }

                _refresh.value = RefreshState.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _refresh.value = RefreshState.Failed(
                    e.message ?: resources.getString(R.string.detail_refresh_failed)
                )
            }
        }
    }
```

- [ ] **Step 3: Run unit tests to verify compilation**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS

- [ ] **Step 4: Commit changes**

```bash
git add app/src/main/java/com/delizioso/app/ui/screens/detail/RecipeDetailScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml
git commit -m "feat: download and update thumbnail during recipe source refresh"
```

---

### Task 2: Database Schema & Entity Update (`MIGRATION_7_8`)

**Files:**
- Modify: `app/src/main/java/com/delizioso/app/data/local/PlannedMealEntity.kt`
- Modify: `app/src/main/java/com/delizioso/app/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/delizioso/app/data/RecipeRepository.kt`

**Interfaces:**
- Consumes: Room database, SQLite migration
- Produces: `PlannedMealEntity` with nullable `recipeId` and custom macro fields; `RecipeRepository.addQuickMeal(...)`

- [ ] **Step 1: Update `PlannedMealEntity.kt`**

Update `PlannedMealEntity` in `app/src/main/java/com/delizioso/app/data/local/PlannedMealEntity.kt`:
```kotlin
@Entity(
    tableName = "planned_meals",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("recipeId")],
)
data class PlannedMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long? = null,
    val customName: String? = null,
    val caloriesKcal: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val carbsG: Double? = null,
    /** Days since epoch (java.time.LocalDate.toEpochDay). */
    val dateEpochDay: Long,
    /** BREAKFAST | LUNCH | DINNER | SNACK. */
    val slot: String,
    val servings: Int = 1,
    /** Set once the user finishes cook mode for this meal (drives the planner's "cooked" dot). */
    val cooked: Boolean = false,
    /** True if this is an attached side dish, sauce, or bread for the slot. */
    val isSide: Boolean = false,
)
```

- [ ] **Step 2: Add `MIGRATION_7_8` and bump version in `AppDatabase.kt`**

In `app/src/main/java/com/delizioso/app/data/local/AppDatabase.kt`:
Set `version = 8`.
Add migration:
```kotlin
        /** v7 → v8: make recipeId nullable and add custom quick meal columns. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `planned_meals_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `recipeId` INTEGER,
                        `customName` TEXT,
                        `caloriesKcal` REAL,
                        `proteinG` REAL,
                        `fatG` REAL,
                        `carbsG` REAL,
                        `dateEpochDay` INTEGER NOT NULL,
                        `slot` TEXT NOT NULL,
                        `servings` INTEGER NOT NULL,
                        `cooked` INTEGER NOT NULL,
                        `isSide` INTEGER NOT NULL,
                        FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `planned_meals_new` (`id`, `recipeId`, `dateEpochDay`, `slot`, `servings`, `cooked`, `isSide`)
                    SELECT `id`, `recipeId`, `dateEpochDay`, `slot`, `servings`, `cooked`, `isSide`
                    FROM `planned_meals`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `planned_meals`")
                db.execSQL("ALTER TABLE `planned_meals_new` RENAME TO `planned_meals`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_planned_meals_recipeId` ON `planned_meals` (`recipeId`)")
            }
        }
```
Add `MIGRATION_7_8` to `Room.databaseBuilder(...).addMigrations(...)`.

- [ ] **Step 3: Add `addQuickMeal` to `RecipeRepository.kt`**

In `app/src/main/java/com/delizioso/app/data/RecipeRepository.kt`:
```kotlin
    suspend fun addQuickMeal(
        dateEpochDay: Long,
        slot: String,
        customName: String,
        caloriesKcal: Double?,
        proteinG: Double?,
        fatG: Double?,
        carbsG: Double?,
        isSide: Boolean = false,
    ): Long = dao.insertMeal(
        PlannedMealEntity(
            recipeId = null,
            customName = customName.trim(),
            caloriesKcal = caloriesKcal,
            proteinG = proteinG,
            fatG = fatG,
            carbsG = carbsG,
            dateEpochDay = dateEpochDay,
            slot = slot,
            servings = 1,
            isSide = isSide,
        )
    )
```

- [ ] **Step 4: Run unit tests to verify migration compilation**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/delizioso/app/data/local/PlannedMealEntity.kt app/src/main/java/com/delizioso/app/data/local/AppDatabase.kt app/src/main/java/com/delizioso/app/data/RecipeRepository.kt
git commit -m "feat: add quick meal fields to PlannedMealEntity and migration 7-8"
```

---

### Task 3: Nutrition Aggregation for Quick Meals

**Files:**
- Modify: `app/src/main/java/com/delizioso/app/data/nutrition/NutritionAggregator.kt`
- Modify: `app/src/test/java/com/delizioso/app/data/nutrition/NutritionAggregatorTest.kt`

**Interfaces:**
- Consumes: `PlannedMealWithRecipe`, `PlannedMealEntity`
- Produces: `NutritionAggregator.computeDayRecap` & `computeWeekRecap` supporting quick meals

- [ ] **Step 1: Write failing test in `NutritionAggregatorTest.kt`**

Add tests to `app/src/test/java/com/delizioso/app/data/nutrition/NutritionAggregatorTest.kt`:
```kotlin
    @Test
    fun `computeDayRecap aggregates quick meals without recipes`() {
        val date = LocalDate.of(2026, 9, 7)
        val quickMeal = PlannedMealWithRecipe(
            meal = PlannedMealEntity(
                id = 99L,
                recipeId = null,
                customName = "Latte e biscotti",
                caloriesKcal = 350.0,
                proteinG = 12.0,
                fatG = 10.0,
                carbsG = 50.0,
                dateEpochDay = date.toEpochDay(),
                slot = MealSlot.BREAKFAST,
            ),
            recipe = null,
        )

        val recap = NutritionAggregator.computeDayRecap(
            date = date,
            meals = listOf(quickMeal),
            allRecipes = emptyList(),
        )

        assertEquals(1, recap.mealCount)
        assertEquals(350, recap.nutrients.caloriesKcal)
        assertEquals(12, recap.nutrients.proteinG)
        assertEquals(10, recap.nutrients.fatG)
        assertEquals(50, recap.nutrients.carbsG)
        assertTrue(recap.isCompleteEstimate)
    }

    @Test
    fun `computeDayRecap combines recipes and quick meals in daily total`() {
        val date = LocalDate.of(2026, 9, 7)
        val recipe = RecipeEntity(
            id = 1L,
            title = "Pollo e riso",
            servings = 1,
            caloriesKcal = 500.0,
            proteinG = 45.0,
            fatG = 10.0,
            carbsG = 55.0,
        )
        val recipeMeal = PlannedMealWithRecipe(
            meal = PlannedMealEntity(id = 1L, recipeId = 1L, dateEpochDay = date.toEpochDay(), slot = MealSlot.LUNCH),
            recipe = recipe,
        )
        val quickMeal = PlannedMealWithRecipe(
            meal = PlannedMealEntity(
                id = 2L,
                recipeId = null,
                customName = "Yogurt greco",
                caloriesKcal = 120.0,
                proteinG = 15.0,
                fatG = 2.0,
                carbsG = 8.0,
                dateEpochDay = date.toEpochDay(),
                slot = MealSlot.SNACK,
            ),
            recipe = null,
        )

        val recap = NutritionAggregator.computeDayRecap(
            date = date,
            meals = listOf(recipeMeal, quickMeal),
            allRecipes = listOf(RecipeWithDetails(recipe = recipe)),
        )

        assertEquals(2, recap.mealCount)
        assertEquals(620, recap.nutrients.caloriesKcal)
        assertEquals(60, recap.nutrients.proteinG)
        assertEquals(12, recap.nutrients.fatG)
        assertEquals(63, recap.nutrients.carbsG)
    }
```

- [ ] **Step 2: Run test to verify failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.delizioso.app.data.nutrition.NutritionAggregatorTest"`
Expected: FAIL (quick meals ignored in existing logic)

- [ ] **Step 3: Update `computeDayRecap` in `NutritionAggregator.kt`**

In `app/src/main/java/com/delizioso/app/data/nutrition/NutritionAggregator.kt`:
```kotlin
        dayMeals.forEach { planned ->
            if (planned.meal.recipeId != null) {
                val recipe = planned.recipe ?: return@forEach
                val details = allRecipes.firstOrNull { it.recipe.id == recipe.id } ?: RecipeWithDetails(recipe = recipe)
                val macros = MacroCalculator.of(details)
                if (macros != null) {
                    hasRecognizedNutrition = true
                    totalKcal += macros.kcal
                    totalProtein += macros.proteinG
                    totalFat += macros.fatG
                    totalCarbs += macros.carbsG
                }
            } else {
                val kcal = planned.meal.caloriesKcal ?: 0.0
                val protein = planned.meal.proteinG ?: 0.0
                val fat = planned.meal.fatG ?: 0.0
                val carbs = planned.meal.carbsG ?: 0.0
                if (kcal > 0.0 || protein > 0.0 || fat > 0.0 || carbs > 0.0) {
                    hasRecognizedNutrition = true
                    totalKcal += kcal
                    totalProtein += protein
                    totalFat += fat
                    totalCarbs += carbs
                }
            }
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.delizioso.app.data.nutrition.NutritionAggregatorTest"`
Expected: PASS

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/delizioso/app/data/nutrition/NutritionAggregator.kt app/src/test/java/com/delizioso/app/data/nutrition/NutritionAggregatorTest.kt
git commit -m "feat: add quick meals support to NutritionAggregator"
```

---

### Task 4: Quick Meal AI Prompt & Parser

**Files:**
- Create: `app/src/main/java/com/delizioso/app/data/export/QuickMealPrompt.kt`
- Create: `app/src/main/java/com/delizioso/app/data/import/QuickMealJsonParser.kt`
- Create: `app/src/main/java/com/delizioso/app/data/ai/QuickMealEstimator.kt`
- Create: `app/src/test/java/com/delizioso/app/data/export/QuickMealPromptTest.kt`
- Create: `app/src/test/java/com/delizioso/app/data/import/QuickMealJsonParserTest.kt`

**Interfaces:**
- Produces:
  - `QuickMealPrompt.buildPrompt(foodDescription: String, targetLanguage: String): String`
  - `QuickMealJsonParser.parse(text: String): QuickMealData?`
  - `QuickMealEstimator.estimate(foodDescription: String): QuickMealData?`

- [ ] **Step 1: Write tests for `QuickMealPromptTest` and `QuickMealJsonParserTest`**

In `app/src/test/java/com/delizioso/app/data/export/QuickMealPromptTest.kt`:
```kotlin
package com.delizioso.app.data.export

import org.junit.Assert.assertTrue
import org.junit.Test

class QuickMealPromptTest {

    @Test
    fun `buildPrompt includes description targetLanguage and expected json keys`() {
        val prompt = QuickMealPrompt.buildPrompt("Latte e biscotti", "Italian")
        assertTrue(prompt.contains("Latte e biscotti"))
        assertTrue(prompt.contains("Italian"))
        assertTrue(prompt.contains("caloriesKcal"))
        assertTrue(prompt.contains("proteinG"))
        assertTrue(prompt.contains("fatG"))
        assertTrue(prompt.contains("carbsG"))
    }
}
```

In `app/src/test/java/com/delizioso/app/data/import/QuickMealJsonParserTest.kt`:
```kotlin
package com.delizioso.app.data.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QuickMealJsonParserTest {

    @Test
    fun `parse valid json object`() {
        val json = """{"name":"Latte e 6 biscotti","caloriesKcal":380,"proteinG":14,"fatG":12,"carbsG":54}"""
        val result = QuickMealJsonParser.parse(json)
        assertNotNull(result)
        assertEquals("Latte e 6 biscotti", result?.name)
        assertEquals(380.0, result?.caloriesKcal)
        assertEquals(14.0, result?.proteinG)
        assertEquals(12.0, result?.fatG)
        assertEquals(54.0, result?.carbsG)
    }

    @Test
    fun `parse markdown fenced json with numeric strings`() {
        val text = """
            Here is your estimate:
            ```json
            {
               "name": "Frozen Cod with Olive Oil",
               "caloriesKcal": "250.5",
               "proteinG": "38",
               "fatG": "10",
               "carbsG": "2"
            }
            ```
        """.trimIndent()
        val result = QuickMealJsonParser.parse(text)
        assertNotNull(result)
        assertEquals("Frozen Cod with Olive Oil", result?.name)
        assertEquals(250.5, result?.caloriesKcal)
        assertEquals(38.0, result?.proteinG)
        assertEquals(10.0, result?.fatG)
        assertEquals(2.0, result?.carbsG)
    }

    @Test
    fun `parse invalid text returns null`() {
        assertNull(QuickMealJsonParser.parse("I don't know what to calculate"))
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.delizioso.app.data.export.QuickMealPromptTest" --tests "com.delizioso.app.data.import.QuickMealJsonParserTest"`
Expected: FAIL (unresolved classes)

- [ ] **Step 3: Implement `QuickMealPrompt.kt`, `QuickMealJsonParser.kt`, and `QuickMealEstimator.kt`**

In `app/src/main/java/com/delizioso/app/data/export/QuickMealPrompt.kt`:
```kotlin
package com.delizioso.app.data.export

object QuickMealPrompt {

    fun buildPrompt(foodDescription: String, targetLanguage: String): String = buildString {
        appendLine("Below is a description of food or a quick meal eaten.")
        appendLine("Estimate the nutritional values and convert it into a valid JSON object — no markdown, no commentary —")
        appendLine("in exactly this shape:")
        appendLine()
        appendLine(SCHEMA)
        appendLine()
        appendLine("Rules:")
        appendLine("- Write the \"name\" concisely in $targetLanguage.")
        appendLine("- Estimate realistic macros (caloriesKcal, proteinG, fatG, carbsG as numbers).")
        appendLine()
        appendLine("--- FOOD DESCRIPTION ---")
        appendLine(foodDescription.trim())
    }

    private const val SCHEMA = """{
  "name": "Concise food title",
  "caloriesKcal": 380,
  "proteinG": 14,
  "fatG": 12,
  "carbsG": 54
}"""
}
```

In `app/src/main/java/com/delizioso/app/data/import/QuickMealJsonParser.kt`:
```kotlin
package com.delizioso.app.data.import

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class QuickMealData(
    val name: String,
    val caloriesKcal: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val carbsG: Double? = null,
)

object QuickMealJsonParser {

    fun parse(text: String, json: Json = Json { ignoreUnknownKeys = true }): QuickMealData? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end <= start) return null
        val body = text.substring(start, end + 1)
        val obj = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null

        fun num(key: String): Double? {
            val prim = obj[key] as? JsonPrimitive ?: return null
            return prim.content.replace(',', '.').toDoubleOrNull()
        }

        fun str(key: String): String? =
            (obj[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() && it != "null" }

        val name = str("name") ?: str("title") ?: return null
        return QuickMealData(
            name = name,
            caloriesKcal = num("caloriesKcal") ?: num("calories"),
            proteinG = num("proteinG") ?: num("protein"),
            fatG = num("fatG") ?: num("fat"),
            carbsG = num("carbsG") ?: num("carbs"),
        )
    }
}
```

In `app/src/main/java/com/delizioso/app/data/ai/QuickMealEstimator.kt`:
```kotlin
package com.delizioso.app.data.ai

import com.delizioso.app.data.export.QuickMealPrompt
import com.delizioso.app.data.import.QuickMealData
import com.delizioso.app.data.import.QuickMealJsonParser
import com.google.ai.client.generativeai.type.GenerateContentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QuickMealEstimator(
    private val consentProvider: suspend () -> Boolean = { true }
) {

    suspend fun isAvailable(): Boolean =
        NanoInference.availability() == NanoInference.Availability.AVAILABLE && consentProvider()

    suspend fun estimate(foodDescription: String, targetLanguage: String): QuickMealData? = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext null
        runCatching {
            val prompt = QuickMealPrompt.buildPrompt(foodDescription, targetLanguage)
            val client = Generation.getClient()
            val response: GenerateContentResponse = NanoInference.generateContentWithBackoff(client, prompt)
            val raw = response.candidates.firstOrNull()?.text ?: return@runCatching null
            QuickMealJsonParser.parse(raw)
        }.getOrNull()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.delizioso.app.data.export.QuickMealPromptTest" --tests "com.delizioso.app.data.import.QuickMealJsonParserTest"`
Expected: PASS

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/delizioso/app/data/export/QuickMealPrompt.kt app/src/main/java/com/delizioso/app/data/import/QuickMealJsonParser.kt app/src/main/java/com/delizioso/app/data/ai/QuickMealEstimator.kt app/src/test/java/com/delizioso/app/data/export/QuickMealPromptTest.kt app/src/test/java/com/delizioso/app/data/import/QuickMealJsonParserTest.kt
git commit -m "feat: add QuickMealPrompt, QuickMealJsonParser, and QuickMealEstimator"
```

---

### Task 5: Quick Meal UI Sheets (`QuickMealSheet` & `AddMealChooserSheet`)

**Files:**
- Create: `app/src/main/java/com/delizioso/app/ui/screens/planner/QuickMealSheet.kt`
- Create: `app/src/main/java/com/delizioso/app/ui/screens/planner/AddMealChooserSheet.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-it/strings.xml`

**Interfaces:**
- Produces:
  - `QuickMealSheet(slot: String, initialQuery: String, onSave: (name: String, kcal: Double?, p: Double?, f: Double?, c: Double?) -> Unit, onClose: () -> Unit)`
  - `AddMealChooserSheet(slot: String, onPickRecipe: () -> Unit, onAddQuickMeal: () -> Unit, onClose: () -> Unit)`

- [ ] **Step 1: Add strings to `strings.xml` and `values-it/strings.xml`**

In `app/src/main/res/values/strings.xml`:
```xml
    <string name="planner_add_quick_meal">+ Quick meal</string>
    <string name="planner_choose_add_title">Add to %1$s</string>
    <string name="planner_choose_recipe">Choose from Recipes</string>
    <string name="planner_choose_recipe_desc">Pick an existing recipe from your library</string>
    <string name="planner_choose_quick">Add Quick Meal</string>
    <string name="planner_choose_quick_desc">Log ad-hoc food with macros or AI estimation</string>
    <string name="quick_meal_title">Quick Meal</string>
    <string name="quick_meal_food_hint">What did you eat? (e.g. 250ml milk and 6 cookies)</string>
    <string name="quick_meal_name_hint">Meal name</string>
    <string name="quick_meal_kcal">Kcal</string>
    <string name="quick_meal_protein">Protein (g)</string>
    <string name="quick_meal_carbs">Carbs (g)</string>
    <string name="quick_meal_fat">Fat (g)</string>
    <string name="quick_meal_ai_estimate">Estimate with AI</string>
    <string name="quick_meal_ai_copy">Copy Prompt</string>
    <string name="quick_meal_ai_send">Send to AI</string>
    <string name="quick_meal_ai_paste">Paste JSON</string>
    <string name="quick_meal_save">Add to Planner</string>
    <string name="planner_search_fallback_quick">Can\'t find it? Add \"%1$s\" as quick meal</string>
```

In `app/src/main/res/values-it/strings.xml`:
```xml
    <string name="planner_add_quick_meal">+ Pasto rapido</string>
    <string name="planner_choose_add_title">Aggiungi a %1$s</string>
    <string name="planner_choose_recipe">Scegli dalle ricette</string>
    <string name="planner_choose_recipe_desc">Seleziona una ricetta dalla tua raccolta</string>
    <string name="planner_choose_quick">Aggiungi pasto rapido</string>
    <string name="planner_choose_quick_desc">Registra cibi veloci con macro o stima AI</string>
    <string name="quick_meal_title">Pasto rapido</string>
    <string name="quick_meal_food_hint">Cosa hai mangiato? (es. 250ml latte e 6 biscotti)</string>
    <string name="quick_meal_name_hint">Nome pasto</string>
    <string name="quick_meal_kcal">Kcal</string>
    <string name="quick_meal_protein">Proteine (g)</string>
    <string name="quick_meal_carbs">Carboidrati (g)</string>
    <string name="quick_meal_fat">Grassi (g)</string>
    <string name="quick_meal_ai_estimate">Calcola con AI</string>
    <string name="quick_meal_ai_copy">Copia prompt</string>
    <string name="quick_meal_ai_send">Invia all\'AI</string>
    <string name="quick_meal_ai_paste">Incolla JSON</string>
    <string name="quick_meal_save">Aggiungi al piano</string>
    <string name="planner_search_fallback_quick">Non trovi la ricetta? Aggiungi \"%1$s\" come pasto rapido</string>
```

- [ ] **Step 2: Create `AddMealChooserSheet.kt`**

Create `app/src/main/java/com/delizioso/app/ui/screens/planner/AddMealChooserSheet.kt`:
```kotlin
package com.delizioso.app.ui.screens.planner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.delizioso.app.R
import com.delizioso.app.ui.components.ClayRoundButton
import com.delizioso.app.ui.theme.clayCard

@Composable
fun AddMealChooserSheet(
    slotTitle: String,
    onPickRecipe: () -> Unit,
    onAddQuickMeal: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.planner_choose_add_title, slotTitle),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            ClayRoundButton(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.topbar_close),
                onClick = onClose,
                container = MaterialTheme.colorScheme.surfaceContainerLowest,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 22.dp)
                .clickable(onClick = onPickRecipe)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.planner_choose_recipe), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(stringResource(R.string.planner_choose_recipe_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 22.dp)
                .clickable(onClick = onAddQuickMeal)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text(stringResource(R.string.planner_choose_quick), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(stringResource(R.string.planner_choose_quick_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

- [ ] **Step 3: Create `QuickMealSheet.kt`**

Create `app/src/main/java/com/delizioso/app/ui/screens/planner/QuickMealSheet.kt`:
```kotlin
package com.delizioso.app.ui.screens.planner

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.delizioso.app.R
import com.delizioso.app.data.ai.QuickMealEstimator
import com.delizioso.app.data.export.QuickMealPrompt
import com.delizioso.app.data.import.QuickMealJsonParser
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayRoundButton
import com.delizioso.app.ui.components.ClayTextField
import com.delizioso.app.ui.theme.clayCard
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun QuickMealSheet(
    slotTitle: String,
    initialDescription: String = "",
    estimator: QuickMealEstimator? = null,
    onSave: (name: String, kcal: Double?, p: Double?, f: Double?, c: Double?) -> Unit,
    onClose: () -> Unit,
) {
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var name by rememberSaveable { mutableStateOf(initialDescription) }
    var kcal by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var isEstimating by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val language = remember { Locale.getDefault().getDisplayLanguage(Locale.ENGLISH) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.quick_meal_title) + " ($slotTitle)",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            ClayRoundButton(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.topbar_close),
                onClick = onClose,
                container = MaterialTheme.colorScheme.surfaceContainerLowest,
            )
        }

        ClayTextField(
            value = description,
            onValueChange = {
                description = it
                if (name.isBlank() || name == description.take(name.length)) {
                    name = it
                }
            },
            placeholder = stringResource(R.string.quick_meal_food_hint),
            singleLine = false,
            minLines = 2,
            cornerRadius = 20.dp,
            modifier = Modifier.fillMaxWidth(),
        )

        // AI Tools Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 20.dp)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ClayButton(
                    text = stringResource(R.string.quick_meal_ai_copy),
                    icon = Icons.Filled.AutoAwesome,
                    onClick = {
                        val prompt = QuickMealPrompt.buildPrompt(description.ifBlank { name }, language)
                        clipboard.setText(AnnotatedString(prompt))
                    },
                    container = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                ClayButton(
                    text = stringResource(R.string.quick_meal_ai_send),
                    icon = Icons.Filled.IosShare,
                    onClick = {
                        val prompt = QuickMealPrompt.buildPrompt(description.ifBlank { name }, language)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, prompt)
                        }
                        context.startActivity(Intent.createChooser(send, context.getString(R.string.quick_meal_ai_send)))
                    },
                    container = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ClayButton(
                    text = stringResource(R.string.quick_meal_ai_paste),
                    icon = Icons.Filled.ContentPaste,
                    onClick = { showPasteDialog = true },
                    container = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                if (estimator != null) {
                    ClayButton(
                        text = if (isEstimating) stringResource(R.string.detail_thinking) else stringResource(R.string.quick_meal_ai_estimate),
                        icon = Icons.Filled.Bolt,
                        enabled = !isEstimating && description.isNotBlank(),
                        onClick = {
                            scope.launch {
                                isEstimating = true
                                val result = estimator.estimate(description, language)
                                if (result != null) {
                                    name = result.name
                                    result.caloriesKcal?.let { kcal = it.toInt().toString() }
                                    result.proteinG?.let { protein = it.toInt().toString() }
                                    result.carbsG?.let { carbs = it.toInt().toString() }
                                    result.fatG?.let { fat = it.toInt().toString() }
                                }
                                isEstimating = false
                            }
                        },
                        container = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        ClayTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.quick_meal_name_hint),
            singleLine = true,
            cornerRadius = 18.dp,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ClayTextField(
                value = kcal,
                onValueChange = { kcal = it },
                placeholder = stringResource(R.string.quick_meal_kcal),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cornerRadius = 16.dp,
                modifier = Modifier.weight(1f),
            )
            ClayTextField(
                value = protein,
                onValueChange = { protein = it },
                placeholder = stringResource(R.string.quick_meal_protein),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cornerRadius = 16.dp,
                modifier = Modifier.weight(1f),
            )
            ClayTextField(
                value = carbs,
                onValueChange = { carbs = it },
                placeholder = stringResource(R.string.quick_meal_carbs),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cornerRadius = 16.dp,
                modifier = Modifier.weight(1f),
            )
            ClayTextField(
                value = fat,
                onValueChange = { fat = it },
                placeholder = stringResource(R.string.quick_meal_fat),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cornerRadius = 16.dp,
                modifier = Modifier.weight(1f),
            )
        }

        ClayButton(
            text = stringResource(R.string.quick_meal_save),
            icon = Icons.Filled.Check,
            enabled = name.isNotBlank(),
            onClick = {
                onSave(
                    name,
                    kcal.toDoubleOrNull(),
                    protein.toDoubleOrNull(),
                    fat.toDoubleOrNull(),
                    carbs.toDoubleOrNull(),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showPasteDialog) {
        var pasteText by remember { mutableStateOf("") }
        var failed by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = { Text(stringResource(R.string.edit_ai_paste_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(if (failed) R.string.edit_ai_paste_failed else R.string.edit_ai_paste_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ClayTextField(
                        value = pasteText,
                        onValueChange = { pasteText = it; failed = false },
                        placeholder = stringResource(R.string.edit_ai_paste_placeholder),
                        singleLine = false,
                        minLines = 4,
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ClayButton(
                        text = stringResource(R.string.action_paste),
                        icon = Icons.Filled.ContentPaste,
                        onClick = { clipboard.getText()?.text?.let { pasteText = it; failed = false } },
                        container = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pasteText.isNotBlank(),
                    onClick = {
                        val parsed = QuickMealJsonParser.parse(pasteText)
                        if (parsed != null) {
                            name = parsed.name
                            parsed.caloriesKcal?.let { kcal = it.toInt().toString() }
                            parsed.proteinG?.let { protein = it.toInt().toString() }
                            parsed.carbsG?.let { carbs = it.toInt().toString() }
                            parsed.fatG?.let { fat = it.toInt().toString() }
                            showPasteDialog = false
                        } else {
                            failed = true
                        }
                    },
                ) { Text(stringResource(R.string.edit_ai_paste_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
```

- [ ] **Step 4: Run unit tests to verify compilation**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/delizioso/app/ui/screens/planner/QuickMealSheet.kt app/src/main/java/com/delizioso/app/ui/screens/planner/AddMealChooserSheet.kt app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml
git commit -m "feat: add QuickMealSheet and AddMealChooserSheet"
```

---

### Task 6: Planner Screen Integration & Quick Meal Row

**Files:**
- Modify: `app/src/main/java/com/delizioso/app/ui/screens/planner/PlannerViewModel.kt`
- Modify: `app/src/main/java/com/delizioso/app/ui/screens/planner/PlannerScreen.kt`

**Interfaces:**
- Consumes: `RecipeRepository.addQuickMeal`, `QuickMealSheet`, `AddMealChooserSheet`, `QuickMealEstimator`
- Produces: Complete quick meal creation and rendering in `PlannerScreen`

- [ ] **Step 1: Add `addQuickMeal` to `PlannerViewModel.kt`**

In `app/src/main/java/com/delizioso/app/ui/screens/planner/PlannerViewModel.kt`:
```kotlin
    fun addQuickMeal(
        dateEpochDay: Long,
        slot: String,
        name: String,
        caloriesKcal: Double?,
        proteinG: Double?,
        fatG: Double?,
        carbsG: Double?,
        isSide: Boolean = false,
    ) {
        viewModelScope.launch {
            repository.addQuickMeal(
                dateEpochDay = dateEpochDay,
                slot = slot,
                customName = name,
                caloriesKcal = caloriesKcal,
                proteinG = proteinG,
                fatG = fatG,
                carbsG = carbsG,
                isSide = isSide,
            )
        }
    }
```
Also expose `val quickMealEstimator = QuickMealEstimator { preferences.aiConsentGiven.first() }` in `PlannerViewModel`.

- [ ] **Step 2: Update `PlannedMealRow` to handle `recipeId == null`**

In `app/src/main/java/com/delizioso/app/ui/screens/planner/PlannerScreen.kt`:
Update `PlannedMealRow`:
```kotlin
@Composable
private fun PlannedMealRow(
    planned: PlannedMealWithRecipe,
    recipes: List<RecipeWithDetails>,
    onOpen: (Long) -> Unit,
    onRemove: () -> Unit,
) {
    val recipe = planned.recipe
    var menuOpen by remember { mutableStateOf(false) }

    if (recipe != null) {
        val details = recipes.firstOrNull { it.recipe.id == recipe.id } ?: RecipeWithDetails(recipe = recipe)
        ClayRecipeRow(
            details = details,
            onClick = { onOpen(recipe.id) },
            extraBadge = if (planned.meal.isSide) {
                {
                    ClayChip(
                        text = stringResource(R.string.planner_side_badge),
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            } else null,
            trailing = {
                MealRowMenu(
                    menuOpen = menuOpen,
                    onToggleMenu = { menuOpen = it },
                    onRemove = onRemove,
                )
            },
        )
    } else {
        // Quick meal without a library recipe
        val meal = planned.meal
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 22.dp)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    meal.customName ?: stringResource(R.string.quick_meal_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val macroParts = buildList {
                    meal.caloriesKcal?.let { add("${it.toInt()} kcal") }
                    meal.proteinG?.let { add("${it.toInt()}P") }
                    meal.carbsG?.let { add("${it.toInt()}C") }
                    meal.fatG?.let { add("${it.toInt()}F") }
                }
                if (macroParts.isNotEmpty()) {
                    Text(
                        macroParts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MealRowMenu(
                menuOpen = menuOpen,
                onToggleMenu = { menuOpen = it },
                onRemove = onRemove,
            )
        }
    }
}
```

- [ ] **Step 3: Update `PlannerScreen` sheet states and buttons**

In `PlannerScreen.kt`:
1. Add state for `var chooserSlot by remember { mutableStateOf<String?>(null) }` and `var quickMealRequest by remember { mutableStateOf<QuickMealRequest?>(null) }`.
2. When empty slot is tapped:
   Set `chooserSlot = slot`.
3. In `MealSlot.all.forEach`:
   In `Row(horizontalArrangement = Arrangement.spacedBy(12.dp), ...)` add the third button:
   ```kotlin
   Text(
       stringResource(R.string.planner_add_quick_meal),
       style = MaterialTheme.typography.labelLarge,
       color = MaterialTheme.colorScheme.tertiary,
       modifier = Modifier
           .clip(PillShape)
           .clickable { quickMealRequest = QuickMealRequest(slot) }
           .padding(horizontal = 10.dp, vertical = 6.dp),
   )
   ```
4. In `RecipePickerSheet`:
   If `visible.isEmpty()` and `query.isNotBlank()`:
   Add fallback button:
   ```kotlin
   ClayButton(
       text = stringResource(R.string.planner_search_fallback_quick, query),
       icon = Icons.Filled.Bolt,
       onClick = {
           onClose()
           onAddQuickMealFallback(query)
       },
       container = MaterialTheme.colorScheme.primaryContainer,
       contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
       modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
   )
   ```
5. Render modal bottom sheets:
   - `chooserSlot?.let { slot -> ... AddMealChooserSheet ... }`
   - `quickMealRequest?.let { req -> ... QuickMealSheet ... }`

- [ ] **Step 4: Run unit tests to verify compilation**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: PASS

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/delizioso/app/ui/screens/planner/PlannerViewModel.kt app/src/main/java/com/delizioso/app/ui/screens/planner/PlannerScreen.kt
git commit -m "feat: integrate QuickMealSheet, AddMealChooserSheet, and quick meal row into Planner"
```

---

### Task 7: Full Verification

**Files:**
- All changed files across Tasks 1–6

- [ ] **Step 1: Run complete test suite**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: BUILD SUCCESSFUL with 0 failures

- [ ] **Step 2: Verify git status is clean**

Run: `git status`
Expected: working tree clean
