# Design Spec: Modular Recipe Sources Registry & Settings Management

## Context & Motivation

Delizioso currently imports recipes through a fixed set of importers (`BlogImporter`, `TikTokImporter`, `YouTubeImporter`) and searches via `TheMealDbClient`. Inspired by the sibling project `deliziosa` (which features a modular multi-platform scraper registry and customizable harvester sources), this specification introduces a structured, pluggable **Recipe Source Registry** and user-configurable source toggles in Settings.

This enables:
1. Clear organization of known Italian recipe portals (GialloZafferano, Cookist, Cucchiaio d'Argento, Ricette per Bimby), international sites (AllRecipes, BBC Good Food, Serious Eats), video/social platforms (YouTube, TikTok, Instagram, Facebook), and generic schema.org web extraction.
2. User control via Settings to enable or disable individual recipe sources, persisting preferences locally via AndroidX DataStore.
3. Fast rejection or fallback routing when URLs from disabled sources are pasted.

---

## 1. Domain Model: `RecipeSource` & `RecipeSourceCategory`

### Location: `app/src/main/java/com/delizioso/app/data/import/RecipeSource.kt`

```kotlin
package com.delizioso.app.data.import

enum class RecipeSourceCategory {
    SEARCH_APIS,
    ITALIAN_SITES,
    INTERNATIONAL,
    SOCIAL_MEDIA,
    GENERIC,
}

enum class RecipeSource(
    val id: String,
    val category: RecipeSourceCategory,
    val defaultEnabled: Boolean = true,
    val domains: List<String> = emptyList(),
) {
    // Search APIs
    THE_MEAL_DB("the_meal_db", RecipeSourceCategory.SEARCH_APIS, domains = listOf("themealdb.com")),

    // Italian Recipe Portals
    GIALLO_ZAFFERANO("giallo_zafferano", RecipeSourceCategory.ITALIAN_SITES, domains = listOf("ricette.giallozafferano.it", "giallozafferano.it")),
    COOKIST("cookist", RecipeSourceCategory.ITALIAN_SITES, domains = listOf("cookist.it")),
    CUCCHIAIO("cucchiaio", RecipeSourceCategory.ITALIAN_SITES, domains = listOf("cucchiaio.it")),
    RICETTE_BIMBY("ricette_bimby", RecipeSourceCategory.ITALIAN_SITES, domains = listOf("ricetteperbimby.it")),

    // International Recipe Sites
    ALL_RECIPES("all_recipes", RecipeSourceCategory.INTERNATIONAL, domains = listOf("allrecipes.com")),
    BBC_GOOD_FOOD("bbc_good_food", RecipeSourceCategory.INTERNATIONAL, domains = listOf("bbcgoodfood.com")),
    SERIOUS_EATS("serious_eats", RecipeSourceCategory.INTERNATIONAL, domains = listOf("seriouseats.com")),

    // Social Media & Video
    YOUTUBE("youtube", RecipeSourceCategory.SOCIAL_MEDIA, domains = listOf("youtube.com", "youtu.be")),
    TIKTOK("tiktok", RecipeSourceCategory.SOCIAL_MEDIA, domains = listOf("tiktok.com")),
    INSTAGRAM("instagram", RecipeSourceCategory.SOCIAL_MEDIA, domains = listOf("instagram.com", "ig.me")),
    FACEBOOK("facebook", RecipeSourceCategory.SOCIAL_MEDIA, domains = listOf("facebook.com", "fb.watch")),

    // Generic Fallback
    GENERIC_WEB("generic_web", RecipeSourceCategory.GENERIC);

    companion object {
        fun fromId(id: String): RecipeSource? = values().firstOrNull { it.id.equals(id, ignoreCase = true) }
        fun defaultActiveSources(): Set<RecipeSource> = values().filter { it.defaultEnabled }.toSet()
    }
}
```

---

## 2. Preference Storage: `UserPreferences`

### Location: `app/src/main/java/com/delizioso/app/data/local/UserPreferences.kt`

- Add `Keys.ENABLED_RECIPE_SOURCES = stringSetPreferencesKey("enabled_recipe_sources")`.
- Expose `open val enabledSources: Flow<Set<RecipeSource>>` mapped from stored strings (falling back to `RecipeSource.defaultActiveSources()`).
- Add mutations:
  - `suspend fun setSourceEnabled(source: RecipeSource, enabled: Boolean)`
  - `suspend fun setAllSourcesEnabled(enabled: Boolean)`

---

## 3. Dispatcher & Interception Logic: `RecipeImporterRegistry` & `PlatformDetector`

### Location: `app/src/main/java/com/delizioso/app/data/import/`

1. **Domain Detection (`PlatformDetector.kt`)**:
   - Maps URL host / regex to `RecipeSource` as well as `Platform`.
   - Helper `sourceFor(rawUrl: String): RecipeSource`.

2. **Registry Execution (`RecipeImporterRegistry.kt`)**:
   - `RecipeImporterRegistry` receives `UserPreferences` (or checks enabled set).
   - In `suspend fun import(rawUrl: String): RawImport`:
     - Determine matching `RecipeSource`.
     - Verify source is enabled in `UserPreferences.enabledSources`.
     - If disabled, throw `ImportException(appContext.getString(R.string.import_source_disabled, source.name))` with `retryable = false`.
     - If enabled, delegate to the corresponding importer (`BlogImporter` for web portals and generic web, `TikTokImporter`, `YouTubeImporter`, etc.).

---

## 4. UI: `RecipeSourcesScreen`

### Location: `app/src/main/java/com/delizioso/app/ui/screens/profile/RecipeSourcesScreen.kt`

1. **Top Bar**: Back button navigating to Profile screen, Title ("Recipe Sources" / "Fonti Ricette").
2. **Global Actions**: Top row with "Enable all" and "Disable all" clay buttons.
3. **Categorized List**:
   - Grouped by `RecipeSourceCategory`:
     - 🔍 Search & Database
     - 🇮🇹 Italian Portals
     - 🌍 International
     - 📱 Social Media & Video
     - 🌐 Generic Web (Schema.org)
   - Each item includes:
     - Portal name
     - Domains badge / subtitle
     - Clay switch bound to ViewModel.
4. **Integration**:
   - Add "Recipe Sources" entry card in `ProfileScreen.kt` under appearance/settings.
   - Add navigation route `Routes.RECIPE_SOURCES` in `DeliziosoNav.kt` / `DeliziosoApp.kt`.

---

## 5. Localization

Add string resources to `app/src/main/res/values/strings.xml` and `values-it/strings.xml` for all category names, source names, descriptions, and error messages.

---

## 6. Verification Plan

1. **Unit Tests**:
   - `UserPreferencesTest`: Test toggling individual and all sources in DataStore.
   - `RecipeSourceDetectionTest`: Test domain detection for all defined portals.
   - `RecipeImporterRegistryTest`: Test that disabled sources throw expected `ImportException` and enabled sources succeed.
2. **Device Verification**:
   - Navigate to Profile $\rightarrow$ Recipe Sources.
   - Toggle sources off/on.
   - Test importing a link from a disabled source $\rightarrow$ verify clean error card.
   - Test importing from an enabled source $\rightarrow$ verify successful parsing.
