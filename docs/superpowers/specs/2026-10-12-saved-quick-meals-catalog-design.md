# Design Spec: Saved Quick Meals Catalog & AI Enrichment in Meal Planner

**Date:** 2026-10-12  
**Status:** Approved for Implementation Planning  
**Target:** Delizioso (Android / Jetpack Compose)

---

## 1. Executive Summary & Goals

### 1.1 Motivation
In Delizioso, users plan weekly menus using recipes from their library. However, everyday dining frequently involves simple, recurring meals that do not warrant a full recipe with structured ingredients and preparation steps (e.g., breakfast milk and cereal, fruit and yogurt, quick pasta with tomato sauce, or frozen fish fillet).

Prior to this feature, quick meals were ephemeral one-off entries entered directly on a specific calendar slot (`planned_meals`). Once planned, they could not be reused or referenced across different weeks without re-typing their details and re-estimating nutritional macros.

### 1.2 Core Goals
1. **Persistent Quick Meals Catalog**: Provide a persistent, reusable collection of "Quick Meals" (*Pasti Rapidi*) separate from the recipe library.
2. **Unified Meal Planner Selection**: When planning a day slot, allow users to choose between:
   - Recipes from the library.
   - Saved quick meals from the new catalog.
   - Creating a new quick meal on the fly (which persists into the catalog and plans it simultaneously).
3. **Dedicated Management UI**: Provide a standalone management screen accessible from the Profile menu and Planner to browse, filter by slot, search, edit, and delete quick meals.
4. **AI Enrichment Suite**: Integrate a complete AI macro-analysis flow (external prompt sharing to ChatGPT/Claude/Gemini, clipboard JSON paste parsing, and on-device Gemini Nano estimation) both during creation and when editing catalog items.
5. **Data Resilience & Synchronization**: Snapshot macro data on `planned_meals` with a nullable foreign key (`ON DELETE SET NULL`) to protect historical planner logs, and include the catalog in Snapsync.

---

## 2. Data Architecture & Persistence

### 2.1 Entity Definition: `QuickMealEntity`
A new Room entity in `com.delizioso.app.data.local`:

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
    /** BREAKFAST | LUNCH | DINNER | SNACK | null (applicable to any slot). */
    val defaultSlot: String? = null,
    val caloriesKcal: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val carbsG: Double? = null,
    val createdAtEpochMilli: Long = System.currentTimeMillis(),
)
```

### 2.2 `PlannedMealEntity` Updates
In `PlannedMealEntity.kt`, keep all snapshot columns (`customName`, `caloriesKcal`, `proteinG`, `fatG`, `carbsG`, `slot`, `dateEpochDay`, `servings`, `cooked`, `isSide`) and add:
- `quickMealId: Long? = null` with a Foreign Key:
  ```kotlin
  ForeignKey(
      entity = QuickMealEntity::class,
      parentColumns = ["id"],
      childColumns = ["quickMealId"],
      onDelete = ForeignKey.SET_NULL,
  )
  ```
- Index on `quickMealId`.

*Design Decision Rationale:* Snapshotting values into `planned_meals` while keeping `quickMealId` nullable with `ON DELETE SET NULL` ensures that:
1. Past weekly logs and historical macro calculations never alter retroactively if the catalog item is renamed, adjusted, or deleted.
2. Deleting a catalog meal doesn't cascade-delete past planned meals.

### 2.3 Room Database Migration (v9 → v10)
In `AppDatabase.kt`:
1. Add `QuickMealEntity::class` to the `@Database` entities list.
2. Bump `version = 10`.
3. Implement `MIGRATION_9_10`:
   ```sql
   CREATE TABLE IF NOT EXISTS `quick_meals` (
       `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
       `name` TEXT NOT NULL,
       `defaultSlot` TEXT,
       `caloriesKcal` REAL,
       `proteinG` REAL,
       `fatG` REAL,
       `carbsG` REAL,
       `createdAtEpochMilli` INTEGER NOT NULL
   );
   CREATE INDEX IF NOT EXISTS `index_quick_meals_name` ON `quick_meals` (`name`);
   CREATE INDEX IF NOT EXISTS `index_quick_meals_defaultSlot` ON `quick_meals` (`defaultSlot`);

   ALTER TABLE `planned_meals` ADD COLUMN `quickMealId` INTEGER DEFAULT NULL
       REFERENCES `quick_meals`(`id`) ON DELETE SET NULL;
   CREATE INDEX IF NOT EXISTS `index_planned_meals_quickMealId` ON `planned_meals` (`quickMealId`);
   ```
4. Register `MIGRATION_9_10` in `AppDatabase.build()`.

### 2.4 Synchronization (`DeliziosoSyncConfig`)
In `DeliziosoSyncConfig.kt`:
- Add `"quick_meals"` to the `tables` list.
- Because `quick_meals` is pure structured user data without external file attachments, no entries in `fileColumns` are needed.
- Snapsync will automatically sync changes point-to-point with Raspberry Pi.

---

## 3. Data Access Layer & Repository

### 3.1 DAO: `QuickMealDao`
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
Expose `abstract fun quickMealDao(): QuickMealDao` in `AppDatabase.kt`.

### 3.2 Repository Additions (`RecipeRepository.kt`)
In `RecipeRepository`:
* `val quickMeals: Flow<List<QuickMealEntity>>`: Exposes `quickMealDao.getAll()`.
* `suspend fun saveQuickMeal(meal: QuickMealEntity): Long`: Inserts into `quick_meals`.
* `suspend fun updateQuickMeal(meal: QuickMealEntity)`: Updates in `quick_meals`.
* `suspend fun deleteQuickMeal(id: Long)`: Deletes from `quick_meals`.
* `suspend fun addSavedQuickMeal(dateEpochDay: Long, slot: String, meal: QuickMealEntity, isSide: Boolean = false): Long`:
  Inserts a new `PlannedMealEntity` with:
  ```kotlin
  PlannedMealEntity(
      quickMealId = meal.id,
      customName = meal.name,
      caloriesKcal = meal.caloriesKcal,
      proteinG = meal.proteinG,
      fatG = meal.fatG,
      carbsG = meal.carbsG,
      dateEpochDay = dateEpochDay,
      slot = slot,
      servings = preferences.defaultServings.first(),
      isSide = isSide,
  )
  ```
* `suspend fun createAndPlanQuickMeal(dateEpochDay: Long, slot: String, meal: QuickMealEntity, isSide: Boolean = false): Long`:
  1. Calls `saveQuickMeal(meal)` to insert into the catalog.
  2. Calls `addSavedQuickMeal(...)` with the persisted entity to add to the planner slot.

---

## 4. AI Integration & Macro Analysis

### 4.1 AI Capabilities
The quick meal editing and creation components provide three AI paths:
1. **External AI Prompt Generation (`QuickMealPrompt.kt`)**:
   Generates a structured prompt instructing ChatGPT, Claude, or Gemini to calculate realistic nutritional values and return a strict JSON payload:
   ```json
   {
     "name": "Concise food title",
     "caloriesKcal": 380,
     "proteinG": 14,
     "fatG": 12,
     "carbsG": 54
   }
   ```
   Exposed via "Copy Prompt" (copies to clipboard) and "Send to..." (Android `ACTION_SEND` Intent).
2. **Clipboard Paste & Auto-Fill (`QuickMealJsonParser.kt`)**:
   A modal dialog where the user pastes the AI's JSON output. The parser extracts the title and macros, auto-populating the UI input fields.
3. **On-Device AI Inference (`QuickMealEstimator.kt`)**:
   When on-device Gemini Nano / Gemma is available and user consent is granted, an "Estimate" button triggers direct on-device parsing without network round-trips.

---

## 5. User Interface & Experience Architecture

### 5.1 Planner Screen Flow

#### 5.1.1 Add Meal Chooser (`AddMealChooserSheet.kt`)
Tapping an empty slot (`ClayAddPanel`) or "Aggiungi altro" displays three options:
1. **Ricetta dalla libreria** (`Icons.Filled.MenuBook`): Opens `RecipePickerSheet`.
2. **Pasto rapido salvato** (`Icons.Filled.Bolt`): Opens `QuickMealPickerSheet`.
3. **Crea nuovo pasto rapido** (`Icons.Filled.Add`): Opens `QuickMealSheet` in creation mode for the clicked slot.

#### 5.1.2 Quick Meal Picker (`QuickMealPickerSheet.kt`)
A new bottom sheet dedicated to browsing saved quick meals:
* **Header**: Slot title (e.g. *"Colazione - Lunedì"*), close button, and a shortcut to open the full Quick Meals management screen.
* **Top Action**: `+ Crea nuovo pasto rapido` card that transitions directly to `QuickMealSheet`.
* **Search & Filters**:
  * Real-time search query text field.
  * Slot filter chips (*Tutti*, *Colazione*, *Pranzo*, *Cena*, *Snack*), initialized to the slot being planned if items exist.
* **Meal List**:
  * Clay cards presenting title, default slot badge, and macro summary (`350 kcal · P: 15g · G: 10g · C: 45g`).
  * Tapping any meal plans it immediately and closes the sheet.
  * Empty state with a prompt to create the first quick meal if no items match.

#### 5.1.3 Quick Meal Creation & Edit Sheet (`QuickMealSheet.kt`)
Enhanced to support both on-the-fly planning and standalone catalog management:
* **Slot Category Selector**: Pill chips to pick the slot (*Colazione*, *Pranzo*, *Cena*, *Snack*, or *Qualsiasi*).
* **Description & AI Tools**:
  * Meal description input.
  * AI Tools Card: Copy Prompt, Send to External AI, Paste JSON dialog, and on-device Estimate button.
* **Form Inputs**:
  * Meal name.
  * Numeric fields for Calories (kcal), Protein (g), Carbs (g), and Fat (g).
* **Save Action**:
  * When launched from Planner: Saves to `quick_meals` catalog AND inserts into `planned_meals` for the active day/slot.
  * When launched from Catalog: Saves or updates the `quick_meals` entry.

### 5.2 Dedicated Management Screen (`QuickMealsScreen.kt`)

#### 5.2.1 Navigation & Discovery
* Add route `Routes.QUICK_MEALS = "quick_meals"` in `Routes.kt`.
* In `ProfileScreen.kt`, add a new settings section card:
  * Icon: `Icons.Filled.Bolt`.
  * Title: *"Pasti rapidi salvati"* (*Saved Quick Meals*).
  * Subtitle: Dynamic counter of saved items (e.g. *"8 pasti salvati"*).
  * Action: Navigates to `Routes.QUICK_MEALS`.
* In `PlannerScreen.kt`:
  * Add a button/icon in `QuickMealPickerSheet` header to open the management screen.

#### 5.2.2 Screen Layout & Features
* **Top Bar**: `ClayTopBar` with back arrow, title *"Pasti rapidi"*, and a "+" button in the right action slot.
* **Controls**:
  * `ClayTextField` for name search.
  * Horizontal filter chips: *Tutti*, *Colazione*, *Pranzo*, *Cena*, *Snack*.
* **Meal Cards**:
  * Claymorphic cards displaying title, slot badge, and macros.
  * Context menu (`MoreVert`) with **Modifica** (*Edit*) and **Elimina** (*Delete* with confirmation dialog).
* **Floating Bottom Button**: Clay button to "+ Nuovo pasto rapido".
* **Empty State**: Visual card when no meals are saved, explaining the feature with a call-to-action button to create one.

---

## 6. Localization & Strings

Add localized strings in `res/values/strings.xml` and `res/values-it/strings.xml`:
* `quick_meals_title`: "Quick Meals" / "Pasti rapidi"
* `quick_meals_desc`: "Manage simple meals not tied to detailed recipes" / "Gestisci i pasti semplici non legati a ricette dettagliate"
* `quick_meals_empty`: "No quick meals saved yet" / "Nessun pasto rapido salvato"
* `quick_meals_empty_desc`: "Create simple everyday meals like breakfast cereal or pasta to quickly add them to your weekly planner." / "Crea pasti semplici quotidiani come latte e cereali o pasta per aggiungerli rapidamente al menu settimanale."
* `quick_meals_create_new`: "New Quick Meal" / "Nuovo pasto rapido"
* `quick_meals_choose_saved`: "Saved Quick Meal" / "Pasto rapido salvato"
* `quick_meals_choose_saved_desc`: "Select from your saved everyday meals" / "Scegli tra i tuoi pasti quotidiani salvati"
* `quick_meals_filter_all`: "All" / "Tutti"
* `quick_meals_slot_any`: "Any meal" / "Qualsiasi pasto"
* `quick_meals_delete_confirm_title`: "Delete Quick Meal" / "Elimina pasto rapido"
* `quick_meals_delete_confirm_body`: "Are you sure you want to delete this quick meal from your catalog? Previously planned meals will not be affected." / "Vuoi davvero eliminare questo pasto rapido dal catalogo? I pasti già pianificati nel menu non saranno modificati."
* `quick_meals_count`: "%d saved meals" / "%d pasti salvati"

---

## 7. Testing & Quality Assurance Plan

### 7.1 Automated Testing
1. **`AppDatabaseMigrationTest`**:
   - Verify `MIGRATION_9_10` applies cleanly from a v9 schema SQLite fixture.
   - Verify table structure and column defaults for `quick_meals` and `planned_meals.quickMealId`.
2. **`QuickMealDaoTest`**:
   - Verify insert, get all, get for slot, update, and delete operations.
3. **`RecipeRepositoryTest`**:
   - Verify `saveQuickMeal`, `deleteQuickMeal`.
   - Verify `addSavedQuickMeal` correctly copies snapshot values and associates `quickMealId`.
   - Verify `createAndPlanQuickMeal` atomically inserts both entities.
4. **`NutritionAggregatorTest`**:
   - Confirm `computeDayRecap` and `computeWeekRecap` seamlessly compute macros for meals referencing `quick_meals`.
5. **`QuickMealsViewModelTest`**:
   - Test search filtering and slot tab switching.
   - Test create, edit, and delete event processing.

### 7.2 Manual Validation
1. Open Planner -> Tap empty slot -> Choose "Pasto rapido salvato" -> Pick meal -> Verify it appears in slot with correct macros.
2. Open Planner -> Tap empty slot -> Choose "Crea nuovo pasto rapido" -> Use AI Estimate or Paste JSON -> Save -> Verify it appears in slot AND is listed in Profile -> Pasti rapidi salvati.
3. Open Profile -> Pasti rapidi salvati -> Create/Edit/Delete a meal -> Verify changes persist.
4. Delete a quick meal from catalog -> Verify that historical days in the Planner retaining that meal still display their name and macros without crashing.
