# Delizioso V1.3: Nutritional Insights & Macro Tracking Implementation Plan

## Goal
Implement a per-person daily and weekly nutritional recap system based on planned and cooked meals, with optional macro goals, tactile Clay progress indicators, and complete localization.

---

## User Review Required
> [!NOTE]
> - **Per-Person Scaling**: When aggregating macros for a planned meal, the system computes `(recipe.caloriesKcal / recipe.servings) * meal.servings / defaultServings` (or per 1 person if specified).
> - **Zero-Config Defaults**: If user has not set custom macro goals, the app still shows exact calculated daily/weekly macro totals and macronutrient distribution (% Protein / % Fat / % Carbs).

---

## Proposed Tasks

### Task 1: Domain & Nutrition Aggregation Engine (`NutritionAggregator.kt`)
- Create `com.delizioso.app.data.nutrition.NutritionAggregator`:
  - `data class DailyNutrients(val caloriesKcal: Int, val proteinG: Int, val fatG: Int, val carbsG: Int)`
  - `data class MacroDistribution(val proteinPct: Int, val fatPct: Int, val carbsPct: Int)`
  - `data class DayNutritionRecap(val date: LocalDate, val nutrients: DailyNutrients, val distribution: MacroDistribution, val mealCount: Int)`
  - `data class WeekNutritionRecap(val days: List<DayNutritionRecap>, val totalNutrients: DailyNutrients, val dailyAverage: DailyNutrients, val averageDistribution: MacroDistribution)`
  - `fun computeDayRecap(date: LocalDate, meals: List<PlannedMealWithRecipe>, allRecipes: List<RecipeWithDetails>): DayNutritionRecap`
  - `fun computeWeekRecap(weekStart: LocalDate, meals: List<PlannedMealWithRecipe>, allRecipes: List<RecipeWithDetails>): WeekNutritionRecap`
- Create comprehensive unit tests in `NutritionAggregatorTest.kt`.

### Task 2: User Nutrition Preferences (`UserPreferences.kt` & `ProfileScreen.kt`)
- In `UserPreferences.kt`:
  - `val TARGET_CALORIES_KCAL = intPreferencesKey("target_calories_kcal")`
  - `val TARGET_PROTEIN_G = intPreferencesKey("target_protein_g")`
  - `val TARGET_FAT_G = intPreferencesKey("target_fat_g")`
  - `val TARGET_CARBS_G = intPreferencesKey("target_carbs_g")`
  - Flow & setter functions for nutrition targets.
- In `ProfileScreen.kt`:
  - Add "Nutritional Goals" card where users can set target kcal, protein, carbs, and fat with a clean Clay dialog/input sheet.
- String localizations in `values/strings.xml` and `values-it/strings.xml`.

### Task 3: Home Dashboard Daily Nutrition Widget (`HomeScreen.kt` & `HomeViewModel.kt`)
- In `HomeViewModel.kt`:
  - Expose `dailyNutrition: StateFlow<DayNutritionRecap>` and `nutritionGoals: StateFlow<NutritionGoals?>`.
- In `HomeScreen.kt`:
  - Add `DailyNutritionCard` directly under `UpcomingMealHero`:
    - Shows total kcal consumed/planned today.
    - Progress ring / bar toward daily goal if set.
    - 3 tactile macro chips (`Protein`, `Carbs`, `Fat`) with grams and percentage bar.
    - Tap to open full nutritional details / planner.

### Task 4: Meal Planner Weekly Nutrition Bar (`PlannerScreen.kt` & `PlannerViewModel.kt`)
- In `PlannerViewModel.kt`:
  - Expose `weekNutritionRecap: StateFlow<WeekNutritionRecap>` for the active week.
- In `PlannerScreen.kt`:
  - Add collapsible `WeeklyNutritionSummaryCard` at the top or bottom of the weekly view.
  - Displays:
    - Weekly total & daily average kcal.
    - Macro distribution breakdown (e.g. 45% Carbs, 30% Protein, 25% Fat).
    - Day-by-day micro bar comparison.

### Task 5: Verification & Device Deployment
- Run all unit tests with `./gradlew testDebugUnitTest`.
- Install onto connected Pixel 10 Pro with `./gradlew installDebug`.
- Commit and push to GitHub `main`.
