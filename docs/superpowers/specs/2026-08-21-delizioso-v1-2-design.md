# Delizioso V1.2 — Component Categories, Responsive Import UX & Smart Home Dashboard Spec

> **Status:** Approved  
> **Target Release:** Delizioso v1.2  
> **Author:** Antigravity & GiusMona17  
> **Date:** 2026-08-21  

---

## 1. Executive Summary

This milestone evolves Delizioso from a recipe library into an active everyday kitchen companion through three interconnected architectural and UX enhancements:
1. **Component & Non-Meal Categories**: Support for sauces, breads, condiments, dressings, bases, and side dishes with visual category grouping and the ability to link them as side items in the Meal Planner.
2. **In-Place Responsive Import UX**: Real-time animated transformation of the URL import card so users immediately see extraction and structuring feedback without scrolling.
3. **Smart Home Dashboard & Dock Streamlining**: A dedicated kitchen home screen featuring today's planned meals with 1-tap cooking, empty-slot smart suggestions, quick action shortcuts, and a streamlined 5-tab navigation dock.

---

## 2. Architecture & Design Specifications

### Feature 1: Component Categories & Planner Side Items

#### 1.1 Category Hierarchy & Vocabulary
Categories in [`Categories.kt`](file:///d:/Projects/Coding/delizioso/app/src/main/java/com/delizioso/app/data/Categories.kt) are expanded and organized into 3 logical groups:

```kotlin
enum class CategoryGroup(val id: String, val titleRes: Int) {
    MEAL_TYPE("meal_type", R.string.category_group_meal_type),
    COURSE_COMPONENT("course_component", R.string.category_group_course_component),
    DIET_STYLE("diet_style", R.string.category_group_diet_style),
}
```

**Category Vocabulary Definition:**
- **Meal Types**: `Breakfast`, `Lunch`, `Dinner`, `Snack`, `Dessert`.
- **Course & Components**:
  - `Pasta`, `Soup`, `Salad`, `Baking`
  - `Sauce` (pestos, tomato sauces, gravies, dips, condiments)
  - `Bread` (focaccia, sourdough, flatbreads, doughs)
  - `Side` (contorni, roasted vegetables, side salads)
  - `Drink` (smoothies, cocktails, infusions, hot drinks)
  - `Dressing & Marinade` (vinaigrettes, meat/fish marinades, rubs)
  - `Base & Broth` (stocks, broths, basic pasta doughs)
  - `Preserve` (jams, pickles, ferments, preserves)
- **Diet & Style**: `Vegetarian`, `Vegan`, `Healthy`, `Quick`, `Comfort`, `Spicy`.

#### 1.2 Synonym Mapping & AI Prompt Schema
- Synonyms updated in `Categories.SYNONYMS`:
  - `"sauce"`, `"salsa"`, `"pesto"`, `"gravy"`, `"condimento"` $\rightarrow$ `"Sauce"`
  - `"bread"`, `"pane"`, `"focaccia"`, `"dough"`, `"impasto"` $\rightarrow$ `"Bread"`
  - `"side"`, `"side dish"`, `"contorno"` $\rightarrow$ `"Side"`
  - `"dressing"`, `"marinade"`, `"vinaigrette"` $\rightarrow$ `"Dressing & Marinade"`
  - `"broth"`, `"stock"`, `"brodo"` $\rightarrow$ `"Base & Broth"`
  - `"jam"`, `"marmellata"`, `"pickle"`, `"sottoli"` $\rightarrow$ `"Preserve"`
  - `"drink"`, `"beverage"`, `"cocktail"`, `"smoothie"` $\rightarrow$ `"Drink"`
- External AI enrichment prompt in `RecipePrompt.kt` updated with the new category vocabulary.

#### 1.3 Meal Planner Side Items Integration
- In `PlannedMealEntity`: allow multiple planned recipes for a single `(date, mealType)` slot or add `isSide: Boolean = false`.
- The meal planner UI allows selecting a primary dish and optionally tapping **"+ Add Side or Sauce"** to attach component recipes (e.g. Carbonara + Garlic Focaccia + Tomato Salad).

---

### Feature 2: In-Place Responsive Import Card UX

#### 2.1 Problem & Solution
Currently, the import progress card is appended below all content in `ImportScreen.kt`, requiring manual scrolling to see.

#### 2.2 In-Place Card Transformation
- When `ImportBusy.FETCHING` or `ImportBusy.STRUCTURING` begins:
  - The URL text input card smoothly transitions into an active Clay processing state.
  - Features an animated indeterminate circular progress indicator, animated status text (*"Fetching page content..."* $\rightarrow$ *"Parsing recipe with AI..."*), and a **Cancel** button.
  - Positioned at the very top of the scrollable column, ensuring 100% viewport visibility on all screen aspect ratios.

---

### Feature 3: Smart Home Dashboard & Navigation Dock

#### 3.1 Home Dashboard Sections
`HomeScreen.kt` (`Routes.HOME = "home"`) provides a rich daily kitchen overview:
1. **Header & Daily Greeting**:
   - Greeting adapted to time of day ("Good morning", "Good afternoon", "Good evening") with current date and recipe library count badge.
2. **Today's Meals Timeline**:
   - Cards for Breakfast, Lunch, Dinner, Snack for current day.
   - For planned meals: Recipe photo, title, duration, calories, and a prominent **"Start Cooking"** button opening `CookScreen`.
   - For empty meals: Friendly placeholder with **"+ Plan Meal"** and **"Quick Pick"**.
3. **Smart Meal Suggestions ("What to Cook?")**:
   - Carousel suggesting recipes based on user favorites, quick recipes (<30m), or recipes not cooked recently.
   - 1-tap button to add to today's or tomorrow's planner.
4. **Quick Action Grid**:
   - 4 tactile Clay action cards:
     - 📸 **Scan Cookbook** (ML Kit OCR)
     - 🔗 **Import Link** (URL fetcher)
     - 🔍 **Search Online** (Multi-portal aggregator)
     - ✍️ **Create Recipe** (Manual form)
5. **Favorites & Recently Cooked Carousel**:
   - Quick horizontal card row to jump directly to beloved recipes.

#### 3.2 Navigation Dock Redesign
- Bottom dock updated to 5 clean tabs: `[Home] [Library] [Planner] [Import] [Profile]`.
- Default start destination set to `Routes.HOME`.
- `Create` screen accessible via:
  - Quick Action Grid on Home.
  - Top bar / action button on Library.

---

## 3. Localization & Strings

All new titles, section headers, category group names, empty states, and quick actions will be fully localized in `values/strings.xml` and `values-it/strings.xml`.
