# Design Spec: Quick Meals Planning & Source Thumbnail Refresh

**Date:** 2026-09-07  
**Status:** Approved for Implementation Planning  
**Target:** Delizioso (Android / Jetpack Compose)

---

## 1. Overview & Goals

This specification covers two user features in Delizioso:
1. **Source Thumbnail Refresh on "Aggiorna"**: When refreshing a recipe from its original web/social source, also fetch and cache the source cover photo/thumbnail locally in `ImageStore`, replacing the previous photo.
2. **Smart Quick Meals Planning**: Allow users to plan ad-hoc foods and meals that are not full recipes in their library (e.g. "milk and biscuits", "frozen fish") with macro tracking, direct manual entry, and AI assistance (on-device Gemini Nano and external AI prompt/paste round-trip).

---

## 2. Feature 1: Source Thumbnail Refresh

### 2.1 Problem
When the user taps "Aggiorna" on a recipe detail screen, `SourceRefresher.refetch(url)` successfully retrieves the recipe data and the thumbnail URL (`fresh.recipe.imageUrl ?: raw.thumbnailUrl`). However, `RecipeDetailViewModel.refreshFromSource` and `RecipeRepository.update` previously only updated ingredients, steps, and text, completely ignoring the thumbnail.

### 2.2 Solution
- **`RecipeDetailViewModel.kt`**:
  - In `refreshFromSource()`, if `fresh.recipe.imageUrl` is not null or blank:
    - Invoke `ImageStore.downloadToInternal(context, fresh.recipe.imageUrl)` on `Dispatchers.IO`.
    - Query `repository.currentImage(recipeId)`.
    - If the new thumbnail is downloaded successfully:
      - Call `repository.setImage(recipeId, downloadedPath)`.
      - If an existing locally owned image exists, delete it with `ImageStore.deleteIfOwned(context, oldPath)`.
    - If downloading fails (e.g. network error, 404, or rate limit), log a warning and retain the current image without breaking the rest of the recipe refresh.
- **Strings (`strings.xml` and `values-it/strings.xml`)**:
  - Update `detail_refresh_body` to inform the user that the cover image is also refreshed from the source.

---

## 3. Feature 2: Smart Quick Meals in Planner

### 3.1 Data Architecture & Schema Migration (v7 → v8)

Currently, `PlannedMealEntity` enforces a non-null foreign key `recipeId: Long`. Quick meals do not have an associated recipe entry in the `recipes` table.

#### 3.1.1 Entity Changes (`PlannedMealEntity.kt`)
- `recipeId: Long?`: Make nullable (defaults to `null` for quick meals).
- `customName: String? = null`: The title of the quick meal (e.g. "Latte e biscotti").
- `caloriesKcal: Double? = null`: Total estimated calories.
- `proteinG: Double? = null`: Estimated protein in grams.
- `fatG: Double? = null`: Estimated fat in grams.
- `carbsG: Double? = null`: Estimated carbs in grams.
- Keep existing fields: `id`, `dateEpochDay`, `slot`, `servings`, `cooked`, `isSide`.

#### 3.1.2 Database Migration (`MIGRATION_7_8` in `AppDatabase.kt`)
Because SQLite does not support changing column nullability or foreign keys via `ALTER TABLE`, recreate `planned_meals`:
```sql
CREATE TABLE `planned_meals_new` (
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
);

INSERT INTO `planned_meals_new` (`id`, `recipeId`, `dateEpochDay`, `slot`, `servings`, `cooked`, `isSide`)
SELECT `id`, `recipeId`, `dateEpochDay`, `slot`, `servings`, `cooked`, `isSide` FROM `planned_meals`;

DROP TABLE `planned_meals`;
ALTER TABLE `planned_meals_new` RENAME TO `planned_meals`;
CREATE INDEX `index_planned_meals_recipeId` ON `planned_meals` (`recipeId`);
```
Bump `AppDatabase` version to `8` and register `MIGRATION_7_8`.

#### 3.1.3 Repository & DAO Additions
In `RecipeDao` and `RecipeRepository`:
- `suspend fun addQuickMeal(dateEpochDay: Long, slot: String, name: String, calories: Double?, protein: Double?, fat: Double?, carbs: Double?, isSide: Boolean = false): Long`
- Update `deleteMeal(id: Long)` (already exists).

---

### 3.2 AI Integration for Quick Meals

#### 3.2.1 External AI Prompt (`QuickMealPrompt.kt`)
Create `QuickMealPrompt` following the existing `RecipePrompt` pattern:
- Generates a prompt for ChatGPT / Claude / Gemini:
  ```
  Below is a description of food or a quick meal.
  Estimate the nutritional values and return ONLY a valid JSON object — no markdown, no commentary — in this exact shape:
  {
    "name": "Concise meal title in <targetLanguage>",
    "caloriesKcal": 380,
    "proteinG": 14.0,
    "fatG": 12.0,
    "carbsG": 54.0
  }
  --- FOOD DESCRIPTION ---
  <userInput>
  ```

#### 3.2.2 Response Parser (`QuickMealJsonParser.kt`)
- Strips any markdown code fences (````json ... ````).
- Parses `name`, `caloriesKcal`, `proteinG`, `fatG`, `carbsG`.
- Handles both string numbers and numeric JSON primitives gracefully.

#### 3.2.3 On-Device AI Estimator (`QuickMealEstimator.kt`)
- If Gemini Nano (`NanoInference`) is available and downloaded:
  - Executes a single prompt requesting the JSON schema.
  - Automatically parses and populates the form without leaving the app.
- If unavailable or fails:
  - User can seamlessly use the "Copia prompt" or "Invia all'AI" button.

---

### 3.3 UI Implementation in Planner (`PlannerScreen.kt`)

#### 3.3.1 Entry Points
1. **Empty Slot**: Tapping `ClayAddPanel` opens an `AddMealChooserSheet`:
   - "Scegli ricetta" (opens `RecipePickerSheet`).
   - "Aggiungi pasto rapido" (opens `QuickMealSheet`).
2. **Populated Slot**: In the actions row, display three pills:
   - `+ Altro` (adds main recipe)
   - `+ Contorno` (adds side recipe)
   - `+ Pasto rapido` (opens `QuickMealSheet`).
3. **Recipe Picker Fallback**: When searching in `RecipePickerSheet` and no recipes match:
   - Display a clickable card: *"Non trovi la ricetta? Aggiungi come pasto rapido"*, pre-filling the quick meal description with the search query.

#### 3.3.2 `QuickMealSheet`
A bottom sheet with:
- Food description / name text field (`ClayTextField`).
- Numeric macro inputs: Kcal, Proteine, Carboidrati, Grassi.
- AI Action Buttons:
  - **Calcola con AI** (runs on-device inference if available).
  - **Copia prompt** (copies prompt for external AI to clipboard).
  - **Invia all'AI** (`Intent.ACTION_SEND` share sheet).
  - **Incolla JSON** (dialog to paste external AI response).
- Confirm button: "Aggiungi al piano".

#### 3.3.3 Planned Meal Row (`PlannedMealRow`)
- If `planned.meal.recipeId != null`: Renders `ClayRecipeRow` as before.
- If `planned.meal.recipeId == null`:
  - Renders a styled Clay card with:
    - Meal icon (`Icons.Filled.Bolt` or `Icons.Filled.Fastfood`) in a container.
    - Custom meal name (`planned.meal.customName`).
    - Subtitle or pill displaying macros (e.g. *380 kcal · 14g P · 54g C · 12g F*).
    - Trailing menu with "Rimuovi" (and optional "Segna come cucinato").

---

### 3.4 Nutrition Aggregation (`NutritionAggregator.kt`)

In `computeDayRecap()`:
- When iterating through `dayMeals`:
  - If `planned.meal.recipeId != null`: computes macros via `MacroCalculator.of(recipe)`.
  - If `planned.meal.recipeId == null`: extracts `caloriesKcal`, `proteinG`, `fatG`, `carbsG` directly from `planned.meal`.
  - Accrues into `totalKcal`, `totalProtein`, `totalFat`, `totalCarbs`.
- Daily and weekly summaries automatically reflect quick meals alongside full recipes.

---

## 4. Verification & Testing Plan

1. **Unit Tests**:
   - `NutritionAggregatorTest`: Test daily and weekly aggregation with both recipe-based meals and quick meals.
   - `QuickMealJsonParserTest`: Verify parsing standard JSON, fenced JSON, numbers as strings, and error handling.
   - `QuickMealPromptTest`: Verify prompt output format and language localization.
   - `AppDatabaseMigrationTest`: Test `MIGRATION_7_8` SQLite upgrade preserving existing rows.
2. **Automated Builds**:
   - Run `./gradlew testDebugUnitTest` to ensure all tests pass.
3. **Manual Verification**:
   - Test "Aggiorna" button on a recipe with a source link; confirm photo is downloaded and updated.
   - Add a quick meal (e.g. "Latte e biscotti") with macros in Breakfast slot; verify it shows in the planner and weekly nutrition summary.
   - Test AI copy prompt, share intent, and JSON paste dialog.
