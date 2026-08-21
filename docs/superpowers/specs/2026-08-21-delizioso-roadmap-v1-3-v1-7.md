# Delizioso Product Roadmap & Architecture Spec (V1.3 – V1.7)

## Overview
This specification outlines the evolution of Delizioso into a comprehensive, privacy-first, on-device culinary assistant with tactile Clay UI aesthetics.

---

## Roadmap Milestones

### Milestone 1 (V1.3): 🥗 Nutritional Insights & Macro Tracking
- **Goal**: Provide transparent, per-person daily and weekly macronutrient tracking based on planned and cooked meals.
- **Core Components**:
  - `NutritionAggregator.kt`: Pure domain logic calculating daily and weekly totals (calories, protein, fat, carbs) scaled per person.
  - `UserPreferences.kt`: Daily target goals (target kcal, protein, carbs, fat).
  - `HomeScreen.kt`: Tactile Clay Daily Nutrition Widget showing intake vs target with macro breakdown bar.
  - `PlannerScreen.kt`: Weekly nutritional recap with daily average and macro distribution.
  - `ProfileScreen.kt`: Macro goals configuration dialog/screen.

### Milestone 2 (V1.4): 🧊 Smart Pantry & Leftover Matcher
- **Goal**: Reduce food waste and answer "What can I cook tonight with what I have in my fridge?".
- **Core Components**:
  - `PantryItemEntity.kt` in Room DB (name, category, quantity, expirationDate).
  - `PantryMatcher.kt`: Compares pantry stock against recipe ingredients using synonym normalization.
  - `PantryScreen.kt`: Interactive pantry organizer with quick-add chips from recent grocery trips.
  - `HomeScreen.kt`: "Cook With What You Have" carousel ranking matching recipes.

### Milestone 3 (V1.5): 🛒 Smart Grocery 2.0 (Aisle Auto-Sorting & Share)
- **Goal**: Frictionless supermarket shopping organized by aisle and easy list sharing.
- **Core Components**:
  - `AisleCategorizer.kt`: Groups grocery items into physical supermarket aisles (Produce, Dairy, Meat/Fish, Bakery, Pantry, Spices).
  - `GroceryScreen.kt`: Aisle-grouped view with swipe-to-check and item progress.
  - `GroceryShare.kt`: Formatted plain-text export for WhatsApp, Telegram, Notes.

### Milestone 4 (V1.6): 🪄 AI Recipe Variations & Swaps (Gemini Nano)
- **Goal**: 1-tap on-device recipe adaptations.
- **Core Components**:
  - Pre-engineered Gemini Nano prompts for Air Fryer conversion, Low-Carb / Keto, Vegetarian / Vegan swap, and portion adjustments.
  - Parent/child recipe tracking or instant fork creation in library.

### Milestone 5 (V1.7): 📸 Social Card Sharing & Meal Plan PDF Export
- **Goal**: Share culinary creations aesthetically and print weekly schedules.
- **Core Components**:
  - `ClayRecipeCardBitmapGenerator.kt`: Generates high-res image cards styled with Delizioso Clay theme for Instagram Stories and social media.
  - `MealPlanPdfExporter.kt`: Generates printable 2-page PDF (weekly menu + shopping checklist).

---

## Technical Standards
- **UI System**: Pure Jetpack Compose with custom Clay modifiers (`clayCard`, `clayBevel`, `clayInset`, `clayOuter`, `PillShape`).
- **Data Persistence**: Room 2.6+ with explicit migrations, Kotlin StateFlow, and DataStore Preferences.
- **Privacy & AI**: 100% on-device processing via Gemini Nano / MLKit.
- **Localization**: Full English and Italian (`values/strings.xml`, `values-it/strings.xml`).
