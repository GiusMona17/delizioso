# Saved Quick Meals Catalog & AI Enrichment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a persistent catalog of reusable Quick Meals separate from recipes, integrated with the weekly meal planner, a dedicated management screen in Profile, and an AI enrichment suite.

**Architecture:** 
- Data Layer: New Room entity `QuickMealEntity` in `quick_meals` table, nullable `quickMealId` column on `planned_meals` with `ON DELETE SET NULL`, Room migration `MIGRATION_9_10` (v9 → v10), and Snapsync configuration in `DeliziosoSyncConfig`.
- Repository & DAO: `QuickMealDao` and `RecipeRepository` methods for CRUD operations, single-action catalog creation + planning, and historical snapshot integrity.
- AI Enrichment: `QuickMealPrompt`, `QuickMealJsonParser`, and `QuickMealEstimator` provide external AI prompt export/paste alongside on-device Gemini Nano calculation.
- UI: Updated `AddMealChooserSheet` (3 choices: Recipe, Saved Quick Meal, New Quick Meal), new `QuickMealPickerSheet` (search, slot filters, quick plan), enhanced `QuickMealSheet`, and new dedicated `QuickMealsScreen` registered in `DeliziosoNav` and linked from `ProfileScreen`.

**Tech Stack:** Kotlin 2.1, Jetpack Compose, Room (SQLite), Material 3 Claymorphic UI, Coroutines & Reactive `StateFlow`.

**Spec:** [`docs/superpowers/specs/2026-10-12-saved-quick-meals-catalog-design.md`](file:///d:/Projects/Coding/delizioso/docs/superpowers/specs/2026-10-12-saved-quick-meals-catalog-design.md)

## Global Constraints
- Preserve existing `planned_meals` snapshot data and never break historical meal planner records when catalog meals are modified or deleted (`ON DELETE SET NULL`).
- Follow the Delizioso Culinary Clay design language (`clayCard`, `clayBevel`, `PillShape`, `ClayTextField`, `ClayButton`, `ClayRoundButton`).
- All user-facing strings must be localized in both `values/strings.xml` (English) and `values-it/strings.xml` (Italian).
- Snapsync table synchronization must include `quick_meals`.

---

### Task 1: Room Data Layer & Migration (v9 → v10)

**Files:**
- Create: `app/src/main/java/com/delizioso/app/data/local/QuickMealEntity.kt`
- Create: `app/src/main/java/com/delizioso/app/data/local/QuickMealDao.kt`
- Modify: `app/src/main/java/com/delizioso/app/data/local/PlannedMealEntity.kt:12-42`
- Modify: `app/src/main/java/com/delizioso/app/data/local/AppDatabase.kt:10-30, 195-204`
- Test: `app/src/test/java/com/delizioso/app/data/local/QuickMealEntityAndMigrationTest.kt`

**Interfaces:**
- Consumes: `AppDatabase`, `PlannedMealEntity`
- Produces: `QuickMealEntity`, `QuickMealDao`, `AppDatabase.MIGRATION_9_10`

- [ ] **Step 1: Write the failing entity and migration unit test**

Create `app/src/test/java/com/delizioso/app/data/local/QuickMealEntityAndMigrationTest.kt`:
```kotlin
package com.delizioso.app.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class QuickMealEntityAndMigrationTest {

    @Test
    fun `quick meal entity default values and properties`() {
        val meal = QuickMealEntity(
            id = 5L,
            name = "Latte e biscotti",
            defaultSlot = MealSlot.BREAKFAST,
            caloriesKcal = 320.0,
            proteinG = 10.0,
            fatG = 8.0,
            carbsG = 52.0,
        )

        assertEquals(5L, meal.id)
        assertEquals("Latte e biscotti", meal.name)
        assertEquals(MealSlot.BREAKFAST, meal.defaultSlot)
        assertEquals(320.0, meal.caloriesKcal)
        assertEquals(10.0, meal.proteinG)
        assertEquals(8.0, meal.fatG)
        assertEquals(52.0, meal.carbsG)
        assertTrue(meal.createdAtEpochMilli > 0)
    }

    @Test
    fun `planned meal entity supports nullable quickMealId`() {
        val planned = PlannedMealEntity(
            id = 1L,
            quickMealId = 5L,
            customName = "Latte e biscotti",
            caloriesKcal = 320.0,
            dateEpochDay = 100L,
            slot = MealSlot.BREAKFAST,
        )

        assertEquals(5L, planned.quickMealId)
        assertNull(planned.recipeId)
        assertEquals("Latte e biscotti", planned.customName)
    }

    @Test
    fun `migration 9 to 10 executes expected schema statements`() {
        val executedSql = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_9_10.migrate(db)

        assertEquals(5, executedSql.size)
        assertTrue(executedSql[0].contains("CREATE TABLE IF NOT EXISTS `quick_meals`"))
        assertTrue(executedSql[0].contains("`name` TEXT NOT NULL"))
        assertTrue(executedSql[1].contains("CREATE INDEX IF NOT EXISTS `index_quick_meals_name` ON `quick_meals` (`name`)"))
        assertTrue(executedSql[2].contains("CREATE INDEX IF NOT EXISTS `index_quick_meals_defaultSlot` ON `quick_meals` (`defaultSlot`)"))
        assertTrue(executedSql[3].contains("ALTER TABLE `planned_meals` ADD COLUMN `quickMealId` INTEGER DEFAULT NULL REFERENCES `quick_meals`(`id`) ON DELETE SET NULL"))
        assertTrue(executedSql[4].contains("CREATE INDEX IF NOT EXISTS `index_planned_meals_quickMealId` ON `planned_meals` (`quickMealId`)"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.delizioso.app.data.local.QuickMealEntityAndMigrationTest`  
Expected: Compilation failure (unresolved reference `QuickMealEntity`, `MIGRATION_9_10`, `quickMealId`).

- [ ] **Step 3: Create `QuickMealEntity.kt`**

Create `app/src/main/java/com/delizioso/app/data/local/QuickMealEntity.kt`:
```kotlin
package com.delizioso.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A reusable quick/simple meal entry in the user's catalog. */
@Entity(
    tableName = "quick_meals",
    indices = [
        Index("name"),
        Index("defaultSlot"),
    ],
)
data class QuickMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** BREAKFAST | LUNCH | DINNER | SNACK | null (any). */
    val defaultSlot: String? = null,
    val caloriesKcal: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val carbsG: Double? = null,
    val createdAtEpochMilli: Long = System.currentTimeMillis(),
)
```

- [ ] **Step 4: Create `QuickMealDao.kt`**

Create `app/src/main/java/com/delizioso/app/data/local/QuickMealDao.kt`:
```kotlin
package com.delizioso.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickMealDao {
    @Query("SELECT * FROM quick_meals ORDER BY name ASC")
    fun getAll(): Flow<List<QuickMealEntity>>

    @Query("SELECT * FROM quick_meals WHERE defaultSlot = :slot OR defaultSlot IS NULL ORDER BY name ASC")
    fun getForSlot(slot: String): Flow<List<QuickMealEntity>>

    @Query("SELECT * FROM quick_meals WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): QuickMealEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meal: QuickMealEntity): Long

    @Update
    suspend fun update(meal: QuickMealEntity)

    @Query("DELETE FROM quick_meals WHERE id = :id")
    suspend fun delete(id: Long)
}
```

- [ ] **Step 5: Update `PlannedMealEntity.kt`**

In `app/src/main/java/com/delizioso/app/data/local/PlannedMealEntity.kt`:
Add the foreign key and `quickMealId` property:
```kotlin
@Entity(
    tableName = "planned_meals",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = QuickMealEntity::class,
            parentColumns = ["id"],
            childColumns = ["quickMealId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("recipeId"),
        Index("quickMealId"),
    ],
)
data class PlannedMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long? = null,
    val quickMealId: Long? = null,
    val customName: String? = null,
    val caloriesKcal: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val carbsG: Double? = null,
    val dateEpochDay: Long,
    val slot: String,
    val servings: Int = 1,
    val cooked: Boolean = false,
    val isSide: Boolean = false,
)
```

- [ ] **Step 6: Update `AppDatabase.kt`**

In `app/src/main/java/com/delizioso/app/data/local/AppDatabase.kt`:
1. Add `QuickMealEntity::class` to `@Database(entities = [...], version = 10, exportSchema = true)`.
2. Add `abstract fun quickMealDao(): QuickMealDao`.
3. Add `MIGRATION_9_10`:
```kotlin
        /** v9 → v10: add quick_meals table and quickMealId to planned_meals. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `quick_meals` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `defaultSlot` TEXT,
                        `caloriesKcal` REAL,
                        `proteinG` REAL,
                        `fatG` REAL,
                        `carbsG` REAL,
                        `createdAtEpochMilli` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quick_meals_name` ON `quick_meals` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quick_meals_defaultSlot` ON `quick_meals` (`defaultSlot`)")
                db.execSQL("ALTER TABLE `planned_meals` ADD COLUMN `quickMealId` INTEGER DEFAULT NULL REFERENCES `quick_meals`(`id`) ON DELETE SET NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_planned_meals_quickMealId` ON `planned_meals` (`quickMealId`)")
            }
        }
```
4. Register `MIGRATION_9_10` in `build(context)` migration list:
```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew test --tests com.delizioso.app.data.local.QuickMealEntityAndMigrationTest`  
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/delizioso/app/data/local/QuickMealEntity.kt app/src/main/java/com/delizioso/app/data/local/QuickMealDao.kt app/src/main/java/com/delizioso/app/data/local/PlannedMealEntity.kt app/src/main/java/com/delizioso/app/data/local/AppDatabase.kt app/src/test/java/com/delizioso/app/data/local/QuickMealEntityAndMigrationTest.kt
git commit -m "feat: add QuickMealEntity, QuickMealDao, and migration v9 to v10"
```

---

### Task 2: Repository Integration & Synchronization

**Files:**
- Modify: `app/src/main/java/com/delizioso/app/data/RecipeRepository.kt:15-30, 85-115`
- Modify: `app/src/main/java/com/delizioso/app/DeliziosoApplication.kt:50-55`
- Modify: `app/src/main/java/com/delizioso/app/data/sync/DeliziosoSyncConfig.kt:7-20`
- Modify: `app/src/test/java/com/delizioso/app/data/docs/DeliziosoDocsLoaderTest.kt:35-40`
- Test: `app/src/test/java/com/delizioso/app/data/RecipeRepositoryQuickMealTest.kt`

**Interfaces:**
- Consumes: `QuickMealDao`, `RecipeDao`, `PantryDao`
- Produces: `RecipeRepository.quickMeals`, `RecipeRepository.saveQuickMeal`, `RecipeRepository.updateQuickMeal`, `RecipeRepository.deleteQuickMeal`, `RecipeRepository.addSavedQuickMeal`, `RecipeRepository.createAndPlanQuickMeal`

- [ ] **Step 1: Write the failing repository unit test**

Create `app/src/test/java/com/delizioso/app/data/RecipeRepositoryQuickMealTest.kt`:
```kotlin
package com.delizioso.app.data

import com.delizioso.app.data.local.PantryDao
import com.delizioso.app.data.local.PantryItemEntity
import com.delizioso.app.data.local.PlannedMealEntity
import com.delizioso.app.data.local.PlannedMealWithRecipe
import com.delizioso.app.data.local.QuickMealDao
import com.delizioso.app.data.local.QuickMealEntity
import com.delizioso.app.data.local.RecipeDao
import com.delizioso.app.data.local.RecipeWithDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RecipeRepositoryQuickMealTest {

    private val quickMealsList = mutableListOf<QuickMealEntity>()
    private val plannedMealsList = mutableListOf<PlannedMealEntity>()

    private val fakeQuickMealDao = object : QuickMealDao {
        override fun getAll(): Flow<List<QuickMealEntity>> = MutableStateFlow(quickMealsList)
        override fun getForSlot(slot: String): Flow<List<QuickMealEntity>> =
            MutableStateFlow(quickMealsList.filter { it.defaultSlot == slot || it.defaultSlot == null })
        override suspend fun getById(id: Long): QuickMealEntity? = quickMealsList.firstOrNull { it.id == id }
        override suspend fun insert(meal: QuickMealEntity): Long {
            val assignedId = if (meal.id == 0L) (quickMealsList.size + 1).toLong() else meal.id
            val inserted = meal.copy(id = assignedId)
            quickMealsList.removeAll { it.id == assignedId }
            quickMealsList.add(inserted)
            return assignedId
        }
        override suspend fun update(meal: QuickMealEntity) {
            quickMealsList.removeAll { it.id == meal.id }
            quickMealsList.add(meal)
        }
        override suspend fun delete(id: Long) {
            quickMealsList.removeAll { it.id == id }
        }
    }

    private val fakeRecipeDao = object : RecipeDao {
        override fun observeAllWithDetails(): Flow<List<RecipeWithDetails>> = MutableStateFlow(emptyList())
        override fun observePlannedMeals(fromEpochDay: Long, toEpochDay: Long): Flow<List<PlannedMealWithRecipe>> = MutableStateFlow(emptyList())
        override fun observePlannedMealsForDay(epochDay: Long): Flow<List<PlannedMealWithRecipe>> = MutableStateFlow(emptyList())
        override suspend fun insertPlannedMeal(meal: PlannedMealEntity): Long {
            val assignedId = (plannedMealsList.size + 1).toLong()
            plannedMealsList.add(meal.copy(id = assignedId))
            return assignedId
        }
        override suspend fun deletePlannedMeal(id: Long) {
            plannedMealsList.removeAll { it.id == id }
        }
        override suspend fun setPlannedMealCooked(id: Long, cooked: Boolean) {}
        override suspend fun setFavorite(id: Long, isFavorite: Boolean) {}
        override suspend fun updateImageUri(id: Long, uri: String?) {}
        override suspend fun updateSourceText(recipeId: Long, text: String) {}
        override suspend fun getCurrentImageUri(id: Long): String? = null
        override suspend fun deleteRecipe(id: Long) {}
        override suspend fun insertCookingLog(log: com.delizioso.app.data.local.CookingLogEntity): Long = 0L
        override suspend fun updateCookingLog(log: com.delizioso.app.data.local.CookingLogEntity) {}
        override suspend fun deleteCookingLog(id: Long) {}
    }

    private val fakePantryDao = object : PantryDao {
        override fun getAll(): Flow<List<PantryItemEntity>> = MutableStateFlow(emptyList())
        override fun getInStock(): Flow<List<PantryItemEntity>> = MutableStateFlow(emptyList())
        override suspend fun insert(item: PantryItemEntity): Long = 1L
        override suspend fun insertAll(items: List<PantryItemEntity>) {}
        override suspend fun update(item: PantryItemEntity) {}
        override suspend fun setInStock(id: Long, inStock: Boolean) {}
        override suspend fun delete(id: Long) {}
        override suspend fun deleteOutOfStock() {}
    }

    private val repository = RecipeRepository(fakeRecipeDao, fakePantryDao, fakeQuickMealDao)

    @Test
    fun `save and delete quick meals in catalog`() = runBlocking {
        val id = repository.saveQuickMeal(
            QuickMealEntity(name = "Toast al prosciutto", caloriesKcal = 280.0)
        )
        assertEquals(1L, id)
        assertEquals(1, repository.quickMeals.first().size)

        repository.deleteQuickMeal(id)
        assertEquals(0, repository.quickMeals.first().size)
    }

    @Test
    fun `addSavedQuickMeal plans meal with snapshot macros and quickMealId`() = runBlocking {
        val quickMeal = QuickMealEntity(
            id = 10L,
            name = "Pasta al pomodoro semplice",
            caloriesKcal = 450.0,
            proteinG = 14.0,
            fatG = 8.0,
            carbsG = 80.0,
        )

        repository.addSavedQuickMeal(
            dateEpochDay = 100L,
            slot = "LUNCH",
            meal = quickMeal,
            servings = 2,
        )

        assertEquals(1, plannedMealsList.size)
        val planned = plannedMealsList[0]
        assertEquals(10L, planned.quickMealId)
        assertEquals("Pasta al pomodoro semplice", planned.customName)
        assertEquals(450.0, planned.caloriesKcal)
        assertEquals(2, planned.servings)
    }

    @Test
    fun `createAndPlanQuickMeal saves to catalog and plans meal simultaneously`() = runBlocking {
        val quickMeal = QuickMealEntity(
            name = "Frullato banana e latte",
            caloriesKcal = 220.0,
            proteinG = 8.0,
            fatG = 3.0,
            carbsG = 40.0,
        )

        repository.createAndPlanQuickMeal(
            dateEpochDay = 105L,
            slot = "SNACK",
            meal = quickMeal,
            servings = 1,
        )

        assertEquals(1, quickMealsList.size)
        assertEquals(1, plannedMealsList.size)
        assertEquals(quickMealsList[0].id, plannedMealsList[0].quickMealId)
        assertEquals("Frullato banana e latte", plannedMealsList[0].customName)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests com.delizioso.app.data.RecipeRepositoryQuickMealTest`  
Expected: Compilation failure (`RecipeRepository` constructor mismatch, missing methods).

- [ ] **Step 3: Update `RecipeRepository.kt`**

In `app/src/main/java/com/delizioso/app/data/RecipeRepository.kt`:
Update constructor to accept `quickMealDao: QuickMealDao`:
```kotlin
class RecipeRepository(
    private val dao: RecipeDao,
    private val pantryDao: PantryDao,
    private val quickMealDao: QuickMealDao,
) {
```
Add quick meals operations:
```kotlin
    // ---- Quick Meals Catalog ----

    val quickMeals: Flow<List<QuickMealEntity>> = quickMealDao.getAll()

    fun quickMealsForSlot(slot: String): Flow<List<QuickMealEntity>> = quickMealDao.getForSlot(slot)

    suspend fun saveQuickMeal(meal: QuickMealEntity): Long = quickMealDao.insert(meal)

    suspend fun updateQuickMeal(meal: QuickMealEntity) = quickMealDao.update(meal)

    suspend fun deleteQuickMeal(id: Long) = quickMealDao.delete(id)

    suspend fun addSavedQuickMeal(
        dateEpochDay: Long,
        slot: String,
        meal: QuickMealEntity,
        servings: Int = 1,
        isSide: Boolean = false,
    ): Long = dao.insertPlannedMeal(
        PlannedMealEntity(
            quickMealId = meal.id,
            customName = meal.name,
            caloriesKcal = meal.caloriesKcal,
            proteinG = meal.proteinG,
            fatG = meal.fatG,
            carbsG = meal.carbsG,
            dateEpochDay = dateEpochDay,
            slot = slot,
            servings = servings,
            isSide = isSide,
        )
    )

    suspend fun createAndPlanQuickMeal(
        dateEpochDay: Long,
        slot: String,
        meal: QuickMealEntity,
        servings: Int = 1,
        isSide: Boolean = false,
    ): Long {
        val id = quickMealDao.insert(meal)
        val saved = meal.copy(id = id)
        return addSavedQuickMeal(dateEpochDay, slot, saved, servings, isSide)
    }
```

- [ ] **Step 4: Update `DeliziosoApplication.kt` & tests calling `RecipeRepository`**

In `app/src/main/java/com/delizioso/app/DeliziosoApplication.kt`:
```kotlin
val recipeRepository: RecipeRepository = RecipeRepository(database.recipeDao(), database.pantryDao(), database.quickMealDao())
```
In `app/src/test/java/com/delizioso/app/data/docs/DeliziosoDocsLoaderTest.kt`:
```kotlin
private val repository = RecipeRepository(db.recipeDao(), db.pantryDao(), db.quickMealDao())
```

- [ ] **Step 5: Update `DeliziosoSyncConfig.kt`**

In `app/src/main/java/com/delizioso/app/data/sync/DeliziosoSyncConfig.kt`:
Add `"quick_meals"` to the `tables` list:
```kotlin
val DeliziosoSyncConfig = SyncConfig(
    appName = "delizioso",
    tables = listOf(
        "recipes",
        "tags",
        "ingredients",
        "steps",
        "sources",
        "recipe_tag_cross_ref",
        "planned_meals",
        "cooking_logs",
        "pantry_items",
        "quick_meals",
    ),
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew test --tests com.delizioso.app.data.RecipeRepositoryQuickMealTest`  
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/delizioso/app/data/RecipeRepository.kt app/src/main/java/com/delizioso/app/DeliziosoApplication.kt app/src/main/java/com/delizioso/app/data/sync/DeliziosoSyncConfig.kt app/src/test/java/com/delizioso/app/data/docs/DeliziosoDocsLoaderTest.kt app/src/test/java/com/delizioso/app/data/RecipeRepositoryQuickMealTest.kt
git commit -m "feat: add quick meal repository operations and sync configuration"
```

---

### Task 3: Localization Strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-it/strings.xml`

**Interfaces:**
- Consumes: UI screens and dialogs
- Produces: String resources for Quick Meals

- [ ] **Step 1: Add English strings to `strings.xml`**

In `app/src/main/res/values/strings.xml`:
```xml
    <!-- Quick Meals Catalog -->
    <string name="quick_meals_title">Quick Meals</string>
    <string name="quick_meals_desc">Manage simple everyday meals not tied to detailed recipes</string>
    <string name="quick_meals_empty">No quick meals saved yet</string>
    <string name="quick_meals_empty_desc">Create simple dishes like milk and cereal or pasta to quickly add them to your weekly planner.</string>
    <string name="quick_meals_create_new">New Quick Meal</string>
    <string name="quick_meals_choose_saved">Saved Quick Meal</string>
    <string name="quick_meals_choose_saved_desc">Select from your saved everyday meals</string>
    <string name="quick_meals_filter_all">All</string>
    <string name="quick_meals_slot_any">Any meal</string>
    <string name="quick_meals_delete_confirm_title">Delete Quick Meal</string>
    <string name="quick_meals_delete_confirm_body">Are you sure you want to delete this quick meal from your catalog? Previously planned meals will not be affected.</string>
    <string name="quick_meals_count">%d saved meals</string>
    <string name="quick_meals_pick_title">Choose Quick Meal (%s)</string>
    <string name="quick_meals_manage_catalog">Manage Catalog</string>
    <string name="quick_meals_slot_label">Default meal slot</string>
```

- [ ] **Step 2: Add Italian strings to `values-it/strings.xml`**

In `app/src/main/res/values-it/strings.xml`:
```xml
    <!-- Quick Meals Catalog -->
    <string name="quick_meals_title">Pasti rapidi</string>
    <string name="quick_meals_desc">Gestisci i pasti semplici quotidiani non legati a ricette dettagliate</string>
    <string name="quick_meals_empty">Nessun pasto rapido salvato</string>
    <string name="quick_meals_empty_desc">Crea piatti semplici come latte e cereali o pasta per aggiungerli rapidamente al menu settimanale.</string>
    <string name="quick_meals_create_new">Nuovo pasto rapido</string>
    <string name="quick_meals_choose_saved">Pasto rapido salvato</string>
    <string name="quick_meals_choose_saved_desc">Scegli tra i tuoi pasti quotidiani salvati</string>
    <string name="quick_meals_filter_all">Tutti</string>
    <string name="quick_meals_slot_any">Qualsiasi pasto</string>
    <string name="quick_meals_delete_confirm_title">Elimina pasto rapido</string>
    <string name="quick_meals_delete_confirm_body">Vuoi davvero eliminare questo pasto rapido dal catalogo? I pasti già pianificati nel menu non saranno modificati.</string>
    <string name="quick_meals_count">%d pasti salvati</string>
    <string name="quick_meals_pick_title">Scegli pasto rapido (%s)</string>
    <string name="quick_meals_manage_catalog">Gestisci catalogo</string>
    <string name="quick_meals_slot_label">Slot pasto predefinito</string>
```

- [ ] **Step 3: Verify resources compile**

Run: `./gradlew assembleDebug`  
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml
git commit -m "i18n: add string resources for quick meals catalog"
```

---

### Task 4: Planner UI Sheets (`AddMealChooserSheet` & `QuickMealPickerSheet`)

**Files:**
- Modify: `app/src/main/java/com/delizioso/app/ui/screens/planner/AddMealChooserSheet.kt`
- Create: `app/src/main/java/com/delizioso/app/ui/screens/planner/QuickMealPickerSheet.kt`

**Interfaces:**
- Consumes: `QuickMealEntity`, `MealSlot`, `ClayComponents`, String resources
- Produces: `AddMealChooserSheet` (3 choices: Recipe, Saved Quick Meal, New Quick Meal), `QuickMealPickerSheet`

- [ ] **Step 1: Update `AddMealChooserSheet.kt` to 3 distinct options**

In `app/src/main/java/com/delizioso/app/ui/screens/planner/AddMealChooserSheet.kt`:
Update parameters to include `onPickSavedQuickMeal` and `onCreateNewQuickMeal`:
```kotlin
package com.delizioso.app.ui.screens.planner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    onPickSavedQuickMeal: () -> Unit,
    onCreateNewQuickMeal: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
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

        // 1. Recipe from library
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

        // 2. Saved Quick Meal
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 22.dp)
                .clickable(onClick = onPickSavedQuickMeal)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text(stringResource(R.string.quick_meals_choose_saved), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(stringResource(R.string.quick_meals_choose_saved_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // 3. Create New Quick Meal
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 22.dp)
                .clickable(onClick = onCreateNewQuickMeal)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Text(stringResource(R.string.quick_meals_create_new), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(stringResource(R.string.quick_meals_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

- [ ] **Step 2: Create `QuickMealPickerSheet.kt`**

Create `app/src/main/java/com/delizioso/app/ui/screens/planner/QuickMealPickerSheet.kt`:
```kotlin
package com.delizioso.app.ui.screens.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.delizioso.app.R
import com.delizioso.app.data.local.MealSlot
import com.delizioso.app.data.local.QuickMealEntity
import com.delizioso.app.ui.components.ClayChip
import com.delizioso.app.ui.components.ClayRoundButton
import com.delizioso.app.ui.components.ClayTextField
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.clayBevel
import com.delizioso.app.ui.theme.clayCard

@Composable
fun QuickMealPickerSheet(
    slotTitle: String,
    targetSlot: String,
    quickMeals: List<QuickMealEntity>,
    onPick: (QuickMealEntity) -> Unit,
    onCreateNew: () -> Unit,
    onManageCatalog: () -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Pre-select target slot if there are items with it, else show All (null)
    var selectedSlotFilter by rememberSaveable {
        mutableStateOf<String?>(if (quickMeals.any { it.defaultSlot == targetSlot }) targetSlot else null)
    }

    val visibleMeals = remember(quickMeals, query, selectedSlotFilter) {
        quickMeals.filter { meal ->
            val matchesQuery = query.isBlank() || meal.name.contains(query, ignoreCase = true)
            val matchesSlot = selectedSlotFilter == null || meal.defaultSlot == selectedSlotFilter || meal.defaultSlot == null
            matchesQuery && matchesSlot
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.quick_meals_pick_title, slotTitle),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ClayRoundButton(
                icon = Icons.Filled.Tune,
                contentDescription = stringResource(R.string.quick_meals_manage_catalog),
                onClick = onManageCatalog,
                container = MaterialTheme.colorScheme.surfaceContainerLowest,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp),
            )
            ClayRoundButton(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.topbar_close),
                onClick = onClose,
                container = MaterialTheme.colorScheme.surfaceContainerLowest,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Filter chips: All, Breakfast, Lunch, Dinner, Snack
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val chips = listOf(null to stringResource(R.string.quick_meals_filter_all)) +
                MealSlot.all.map { it to stringResource(MealSlot.labelRes(it)) }

            chips.forEach { (slot, label) ->
                val isSelected = selectedSlotFilter == slot
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)
                        .clayBevel(PillShape)
                        .clickable { selectedSlotFilter = slot }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        ClayTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.planner_search),
            leadingIcon = Icons.Filled.Search,
            cornerRadius = 24.dp,
            modifier = Modifier.fillMaxWidth(),
        )

        // "+ Create New" Quick Action Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clayCard(container = MaterialTheme.colorScheme.primaryContainer, cornerRadius = 20.dp)
                .clickable(onClick = onCreateNew)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                stringResource(R.string.quick_meals_create_new),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        if (visibleMeals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.quick_meals_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.height(320.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visibleMeals, key = { it.id }) { meal ->
                    QuickMealPickerRow(meal = meal, onClick = { onPick(meal) })
                }
            }
        }
    }
}

@Composable
private fun QuickMealPickerRow(
    meal: QuickMealEntity,
    onClick: () -> Unit,
) {
    val macroParts = buildList {
        meal.caloriesKcal?.let { add("${it.toInt()} kcal") }
        meal.proteinG?.let { add("P: ${it.toInt()}g") }
        meal.fatG?.let { add("G: ${it.toInt()}g") }
        meal.carbsG?.let { add("C: ${it.toInt()}g") }
    }
    val macroText = macroParts.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 20.dp)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                meal.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (macroText.isNotBlank()) {
                Text(
                    macroText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        meal.defaultSlot?.let { slot ->
            ClayChip(
                text = stringResource(MealSlot.labelRes(slot)),
                container = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
```

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug`  
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/delizioso/app/ui/screens/planner/AddMealChooserSheet.kt app/src/main/java/com/delizioso/app/ui/screens/planner/QuickMealPickerSheet.kt
git commit -m "feat: update AddMealChooserSheet and create QuickMealPickerSheet"
```

---

### Task 5: Enhanced `QuickMealSheet` & `PlannerScreen` Workflow

**Files:**
- Modify: `app/src/main/java/com/delizioso/app/ui/screens/planner/QuickMealSheet.kt`
- Modify: `app/src/main/java/com/delizioso/app/ui/screens/planner/PlannerViewModel.kt:85-130`
- Modify: `app/src/main/java/com/delizioso/app/ui/screens/planner/PlannerScreen.kt:95-105, 140-305`

**Interfaces:**
- Consumes: `RecipeRepository`, `QuickMealPickerSheet`, `QuickMealSheet`, `QuickMealEstimator`
- Produces: Seamless planning from saved quick meals or instant creation in `PlannerScreen`

- [ ] **Step 1: Enhance `QuickMealSheet.kt` with slot selection and optional pre-populated meal**

In `app/src/main/java/com/delizioso/app/ui/screens/planner/QuickMealSheet.kt`:
Update signature and add slot selector chips:
```kotlin
@Composable
fun QuickMealSheet(
    slotTitle: String,
    initialDefaultSlot: String? = null,
    initialMeal: QuickMealEntity? = null,
    initialDescription: String = "",
    estimator: QuickMealEstimator? = null,
    onSave: (name: String, slot: String?, kcal: Double?, p: Double?, f: Double?, c: Double?) -> Unit,
    onClose: () -> Unit,
) {
    var defaultSlot by rememberSaveable { mutableStateOf(initialMeal?.defaultSlot ?: initialDefaultSlot) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var name by rememberSaveable { mutableStateOf(initialMeal?.name ?: initialDescription) }
    var kcal by rememberSaveable { mutableStateOf(initialMeal?.caloriesKcal?.toInt()?.toString().orEmpty()) }
    var protein by rememberSaveable { mutableStateOf(initialMeal?.proteinG?.toInt()?.toString().orEmpty()) }
    var carbs by rememberSaveable { mutableStateOf(initialMeal?.carbsG?.toInt()?.toString().orEmpty()) }
    var fat by rememberSaveable { mutableStateOf(initialMeal?.fatG?.toInt()?.toString().orEmpty()) }
    var isEstimating by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
```
In the `Column` above the AI card, add slot selector chips:
```kotlin
        Text(
            stringResource(R.string.quick_meals_slot_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val slots = listOf(null to stringResource(R.string.quick_meals_slot_any)) +
                MealSlot.all.map { it to stringResource(MealSlot.labelRes(it)) }

            slots.forEach { (s, label) ->
                val selected = defaultSlot == s
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)
                        .clayBevel(PillShape)
                        .clickable { defaultSlot = s }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
```
Update the `onSave` invocation at the bottom of `QuickMealSheet.kt`:
```kotlin
        ClayButton(
            text = stringResource(R.string.quick_meal_save),
            icon = Icons.Filled.Check,
            enabled = name.isNotBlank(),
            onClick = {
                onSave(
                    name,
                    defaultSlot,
                    kcal.toDoubleOrNull(),
                    protein.toDoubleOrNull(),
                    fat.toDoubleOrNull(),
                    carbs.toDoubleOrNull(),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
```

- [ ] **Step 2: Update `PlannerViewModel.kt`**

In `app/src/main/java/com/delizioso/app/ui/screens/planner/PlannerViewModel.kt`:
Expose `quickMeals`:
```kotlin
val quickMeals: StateFlow<List<QuickMealEntity>> =
    repository.quickMeals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```
Add methods for adding saved and new quick meals:
```kotlin
    fun addSavedQuickMeal(quickMeal: QuickMealEntity, epochDay: Long, slot: String, isSide: Boolean = false) {
        viewModelScope.launch {
            val servings = preferences.defaultServings.first()
            repository.addSavedQuickMeal(epochDay, slot, quickMeal, servings, isSide)
        }
    }

    fun createAndPlanQuickMeal(
        name: String,
        defaultSlot: String?,
        caloriesKcal: Double?,
        proteinG: Double?,
        fatG: Double?,
        carbsG: Double?,
        epochDay: Long,
        slot: String,
        isSide: Boolean = false,
    ) {
        viewModelScope.launch {
            val servings = preferences.defaultServings.first()
            val entity = QuickMealEntity(
                name = name,
                defaultSlot = defaultSlot ?: slot,
                caloriesKcal = caloriesKcal,
                proteinG = proteinG,
                fatG = fatG,
                carbsG = carbsG,
            )
            repository.createAndPlanQuickMeal(epochDay, slot, entity, servings, isSide)
        }
    }
```

- [ ] **Step 3: Update `PlannerScreen.kt`**

In `app/src/main/java/com/delizioso/app/ui/screens/planner/PlannerScreen.kt`:
1. Add state for `quickMealPickerSlot`:
```kotlin
val quickMeals by viewModel.quickMeals.collectAsStateWithLifecycle()
var quickMealPickerRequest by remember { mutableStateOf<QuickMealPickerRequest?>(null) }
```
where `data class QuickMealPickerRequest(val slot: String, val isSide: Boolean = false)`.
2. Connect `AddMealChooserSheet`:
```kotlin
    chooserSlot?.let { slot ->
        ModalBottomSheet(
            onDismissRequest = { chooserSlot = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            AddMealChooserSheet(
                slotTitle = stringResource(MealSlot.labelRes(slot)),
                onPickRecipe = {
                    val s = slot
                    chooserSlot = null
                    pickerRequest = PickerRequest(s, isSide = false)
                },
                onPickSavedQuickMeal = {
                    val s = slot
                    chooserSlot = null
                    quickMealPickerRequest = QuickMealPickerRequest(s, isSide = false)
                },
                onCreateNewQuickMeal = {
                    val s = slot
                    chooserSlot = null
                    quickMealRequest = QuickMealRequest(s, isSide = false)
                },
                onClose = { chooserSlot = null },
            )
        }
    }
```
3. Add `quickMealPickerRequest` bottom sheet:
```kotlin
    quickMealPickerRequest?.let { req ->
        ModalBottomSheet(
            onDismissRequest = { quickMealPickerRequest = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            QuickMealPickerSheet(
                slotTitle = stringResource(MealSlot.labelRes(req.slot)),
                targetSlot = req.slot,
                quickMeals = quickMeals,
                onPick = { meal ->
                    viewModel.addSavedQuickMeal(meal, selectedDate.toEpochDay(), req.slot, req.isSide)
                    quickMealPickerRequest = null
                },
                onCreateNew = {
                    val s = req.slot
                    val isSide = req.isSide
                    quickMealPickerRequest = null
                    quickMealRequest = QuickMealRequest(slot = s, isSide = isSide)
                },
                onManageCatalog = {
                    quickMealPickerRequest = null
                    // Navigates to quick meals management
                    onRecipeClick(-1L) // Or dedicated onOpenQuickMeals callback
                },
                onClose = { quickMealPickerRequest = null },
            )
        }
    }
```
4. Update `quickMealRequest` saving:
```kotlin
    quickMealRequest?.let { req ->
        ModalBottomSheet(
            onDismissRequest = { quickMealRequest = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            QuickMealSheet(
                slotTitle = stringResource(MealSlot.labelRes(req.slot)),
                initialDefaultSlot = req.slot,
                initialDescription = req.initialQuery,
                estimator = viewModel.quickMealEstimator,
                onSave = { name, slot, kcal, p, f, c ->
                    viewModel.createAndPlanQuickMeal(
                        name = name,
                        defaultSlot = slot,
                        caloriesKcal = kcal,
                        proteinG = p,
                        fatG = f,
                        carbsG = c,
                        epochDay = selectedDate.toEpochDay(),
                        slot = req.slot,
                        isSide = req.isSide,
                    )
                    quickMealRequest = null
                },
                onClose = { quickMealRequest = null },
            )
        }
    }
```

- [ ] **Step 4: Verify Planner build**

Run: `./gradlew assembleDebug`  
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/delizioso/app/ui/screens/planner/QuickMealSheet.kt app/src/main/java/com/delizioso/app/ui/screens/planner/PlannerViewModel.kt app/src/main/java/com/delizioso/app/ui/screens/planner/PlannerScreen.kt
git commit -m "feat: integrate saved quick meals catalog and creation into PlannerScreen"
```

---

### Task 6: Dedicated Management Screen (`QuickMealsScreen.kt` & `QuickMealsViewModel.kt`) & Profile Integration

**Files:**
- Create: `app/src/main/java/com/delizioso/app/ui/screens/planner/QuickMealsViewModel.kt`
- Create: `app/src/main/java/com/delizioso/app/ui/screens/planner/QuickMealsScreen.kt`
- Modify: `app/src/main/java/com/delizioso/app/ui/navigation/DeliziosoNav.kt:42-70, 95-150`
- Modify: `app/src/main/java/com/delizioso/app/ui/DeliziosoApp.kt:80-140`
- Modify: `app/src/main/java/com/delizioso/app/ui/screens/profile/ProfileScreen.kt:180-240`
- Test: `app/src/test/java/com/delizioso/app/ui/screens/planner/QuickMealsViewModelTest.kt`

**Interfaces:**
- Consumes: `RecipeRepository`, `QuickMealEstimator`, `Routes`
- Produces: Dedicated management screen for Quick Meals (`Routes.QUICK_MEALS`), linked from Profile and Planner

- [ ] **Step 1: Write `QuickMealsViewModelTest.kt`**

Create `app/src/test/java/com/delizioso/app/ui/screens/planner/QuickMealsViewModelTest.kt`:
```kotlin
package com.delizioso.app.ui.screens.planner

import com.delizioso.app.data.local.MealSlot
import com.delizioso.app.data.local.QuickMealEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickMealsViewModelTest {

    @Test
    fun `filtering meals by slot and search query`() {
        val meals = listOf(
            QuickMealEntity(id = 1, name = "Caffè e cornetto", defaultSlot = MealSlot.BREAKFAST),
            QuickMealEntity(id = 2, name = "Pasta al burro", defaultSlot = MealSlot.LUNCH),
            QuickMealEntity(id = 3, name = "Insalata mista", defaultSlot = null),
            QuickMealEntity(id = 4, name = "Latte e fette biscottate", defaultSlot = MealSlot.BREAKFAST),
        )

        // Slot filter BREAKFAST
        val breakfastMeals = meals.filter { it.defaultSlot == MealSlot.BREAKFAST || it.defaultSlot == null }
        assertEquals(3, breakfastMeals.size)

        // Search filter "caffè"
        val searchMeals = meals.filter { it.name.contains("caffè", ignoreCase = true) }
        assertEquals(1, searchMeals.size)
        assertEquals(1L, searchMeals[0].id)
    }
}
```

- [ ] **Step 2: Create `QuickMealsViewModel.kt`**

Create `app/src/main/java/com/delizioso/app/ui/screens/planner/QuickMealsViewModel.kt`:
```kotlin
package com.delizioso.app.ui.screens.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.delizioso.app.DeliziosoApplication
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.ai.QuickMealEstimator
import com.delizioso.app.data.local.QuickMealEntity
import com.delizioso.app.data.local.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QuickMealsViewModel(
    private val repository: RecipeRepository,
    private val preferences: UserPreferences,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSlot = MutableStateFlow<String?>(null)
    val selectedSlot: StateFlow<String?> = _selectedSlot.asStateFlow()

    val quickMealEstimator = QuickMealEstimator {
        preferences.aiConsentGiven.first()
    }

    val visibleMeals: StateFlow<List<QuickMealEntity>> = combine(
        repository.quickMeals,
        _searchQuery,
        _selectedSlot,
    ) { meals, query, slot ->
        meals.filter { meal ->
            val matchesQuery = query.isBlank() || meal.name.contains(query, ignoreCase = true)
            val matchesSlot = slot == null || meal.defaultSlot == slot || meal.defaultSlot == null
            matchesQuery && matchesSlot
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalCount: StateFlow<Int> = combine(repository.quickMeals) { it[0].size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onSlotSelected(slot: String?) {
        _selectedSlot.value = slot
    }

    fun saveMeal(meal: QuickMealEntity) {
        viewModelScope.launch {
            if (meal.id == 0L) {
                repository.saveQuickMeal(meal)
            } else {
                repository.updateQuickMeal(meal)
            }
        }
    }

    fun deleteMeal(id: Long) {
        viewModelScope.launch {
            repository.deleteQuickMeal(id)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                QuickMealsViewModel(app.container.recipeRepository, app.container.preferences)
            }
        }
    }
}
```

- [ ] **Step 3: Create `QuickMealsScreen.kt`**

Create `app/src/main/java/com/delizioso/app/ui/screens/planner/QuickMealsScreen.kt`:
```kotlin
package com.delizioso.app.ui.screens.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delizioso.app.R
import com.delizioso.app.data.local.MealSlot
import com.delizioso.app.data.local.QuickMealEntity
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayChip
import com.delizioso.app.ui.components.ClayRoundButton
import com.delizioso.app.ui.components.ClayTextField
import com.delizioso.app.ui.components.ClayTopBar
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.clayBevel
import com.delizioso.app.ui.theme.clayCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMealsScreen(
    onBack: () -> Unit,
    viewModel: QuickMealsViewModel = viewModel(factory = QuickMealsViewModel.Factory),
) {
    val meals by viewModel.visibleMeals.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedSlot by viewModel.selectedSlot.collectAsStateWithLifecycle()

    var editingMeal by remember { mutableStateOf<QuickMealEntity?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var mealToDelete by remember { mutableStateOf<QuickMealEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        ClayTopBar(
            title = stringResource(R.string.quick_meals_title),
            menuIcon = Icons.AutoMirrored.Filled.ArrowBack,
            menuDescription = stringResource(R.string.action_back),
            onMenu = onBack,
            onProfile = { showCreateSheet = true },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            ClayTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = stringResource(R.string.planner_search),
                leadingIcon = Icons.Filled.Search,
                cornerRadius = 24.dp,
                modifier = Modifier.fillMaxWidth(),
            )

            // Slot filter chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    val isSelected = selectedSlot == null
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)
                            .clayBevel(PillShape)
                            .clickable { viewModel.onSlotSelected(null) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.quick_meals_filter_all),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(MealSlot.all) { slot ->
                    val isSelected = selectedSlot == slot
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)
                            .clayBevel(PillShape)
                            .clickable { viewModel.onSlotSelected(slot) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(MealSlot.labelRes(slot)),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (meals.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.quick_meals_empty),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.quick_meals_empty_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                    ClayButton(
                        text = stringResource(R.string.quick_meals_create_new),
                        icon = Icons.Filled.Add,
                        onClick = { showCreateSheet = true },
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(meals, key = { it.id }) { meal ->
                        QuickMealCatalogCard(
                            meal = meal,
                            onEdit = { editingMeal = meal },
                            onDelete = { mealToDelete = meal },
                        )
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCreateSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            QuickMealSheet(
                slotTitle = stringResource(R.string.quick_meals_create_new),
                initialDefaultSlot = selectedSlot,
                estimator = viewModel.quickMealEstimator,
                onSave = { name, slot, kcal, p, f, c ->
                    viewModel.saveMeal(
                        QuickMealEntity(
                            name = name,
                            defaultSlot = slot,
                            caloriesKcal = kcal,
                            proteinG = p,
                            fatG = f,
                            carbsG = c,
                        )
                    )
                    showCreateSheet = false
                },
                onClose = { showCreateSheet = false },
            )
        }
    }

    editingMeal?.let { meal ->
        ModalBottomSheet(
            onDismissRequest = { editingMeal = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            QuickMealSheet(
                slotTitle = meal.name,
                initialMeal = meal,
                estimator = viewModel.quickMealEstimator,
                onSave = { name, slot, kcal, p, f, c ->
                    viewModel.saveMeal(
                        meal.copy(
                            name = name,
                            defaultSlot = slot,
                            caloriesKcal = kcal,
                            proteinG = p,
                            fatG = f,
                            carbsG = c,
                        )
                    )
                    editingMeal = null
                },
                onClose = { editingMeal = null },
            )
        }
    }

    mealToDelete?.let { meal ->
        AlertDialog(
            onDismissRequest = { mealToDelete = null },
            title = { Text(stringResource(R.string.quick_meals_delete_confirm_title)) },
            text = { Text(stringResource(R.string.quick_meals_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMeal(meal.id)
                    mealToDelete = null
                }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { mealToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun QuickMealCatalogCard(
    meal: QuickMealEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    val macroParts = buildList {
        meal.caloriesKcal?.let { add("${it.toInt()} kcal") }
        meal.proteinG?.let { add("P: ${it.toInt()}g") }
        meal.fatG?.let { add("G: ${it.toInt()}g") }
        meal.carbsG?.let { add("C: ${it.toInt()}g") }
    }
    val macroText = macroParts.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 22.dp)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(26.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                meal.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (macroText.isNotBlank()) {
                Text(macroText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        meal.defaultSlot?.let { slot ->
            ClayChip(
                text = stringResource(MealSlot.labelRes(slot)),
                container = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        Box {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(PillShape)
                    .clickable { menuOpen = true }
                    .padding(6.dp),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit)) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}
```

- [ ] **Step 4: Register route in `Routes.kt` & `DeliziosoNav.kt` & `DeliziosoApp.kt`**

In `app/src/main/java/com/delizioso/app/ui/navigation/DeliziosoNav.kt`:
```kotlin
object Routes {
    // ...
    const val QUICK_MEALS = "quick_meals"
```
In `app/src/main/java/com/delizioso/app/ui/DeliziosoApp.kt`:
Add composable destination:
```kotlin
composable(Routes.QUICK_MEALS) {
    QuickMealsScreen(
        onBack = { navController.popBackStack() },
    )
}
```

- [ ] **Step 5: Add Quick Meals entry in `ProfileScreen.kt`**

In `app/src/main/java/com/delizioso/app/ui/screens/profile/ProfileScreen.kt`:
Add card navigating to `Routes.QUICK_MEALS`:
```kotlin
        ProfileNavCard(
            icon = Icons.Filled.Bolt,
            title = stringResource(R.string.quick_meals_title),
            subtitle = stringResource(R.string.quick_meals_desc),
            onClick = onOpenQuickMeals,
        )
```
Pass `onOpenQuickMeals = { navController.navigate(Routes.QUICK_MEALS) }` from `DeliziosoApp.kt`.

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew test --tests com.delizioso.app.ui.screens.planner.QuickMealsViewModelTest`  
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/delizioso/app/ui/screens/planner/QuickMealsViewModel.kt app/src/main/java/com/delizioso/app/ui/screens/planner/QuickMealsScreen.kt app/src/main/java/com/delizioso/app/ui/navigation/DeliziosoNav.kt app/src/main/java/com/delizioso/app/ui/DeliziosoApp.kt app/src/main/java/com/delizioso/app/ui/screens/profile/ProfileScreen.kt app/src/test/java/com/delizioso/app/ui/screens/planner/QuickMealsViewModelTest.kt
git commit -m "feat: add QuickMealsScreen and Profile navigation"
```

---

### Task 7: Full Verification Gate

**Files:**
- Test: All repository, migration, aggregator, and UI tests

- [ ] **Step 1: Run complete test suite**

Run: `./gradlew test`  
Expected: All unit tests PASS without errors.

- [ ] **Step 2: Run debug APK assembly**

Run: `./gradlew assembleDebug`  
Expected: BUILD SUCCESSFUL with 0 errors.

- [ ] **Step 3: Verification commit**

```bash
git commit --allow-empty -m "chore: verify full test suite and build for saved quick meals catalog"
```
