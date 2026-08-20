# Modular Recipe Sources Registry & Settings Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a modular recipe source registry with user-configurable source toggles in Settings, enabling or disabling individual recipe sources and routing imports appropriately.

**Architecture:** Define strongly-typed `RecipeSource` and `RecipeSourceCategory` enums with domain patterns; persist user-enabled source IDs in AndroidX DataStore via `UserPreferences`; enforce enabled checks in `PlatformDetector` and `RecipeImporterRegistry`; and surface a categorized `RecipeSourcesScreen` with Clay switch toggles in Settings.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, AndroidX DataStore, JUnit 4, Kotlinx Coroutines.

**Spec:** [`docs/superpowers/specs/2026-08-20-recipe-sources-registry-design.md`](file:///d:/Projects/Coding/delizioso/docs/superpowers/specs/2026-08-20-recipe-sources-registry-design.md)

## Global Constraints

- **Language & JVM:** Kotlin 2.0+, JVM 17.
- **UI Design System:** Custom Clay neumorphic components (`ClayTopBar`, `ClayCard`, `ClayButton`, `ClaySectionHeader`).
- **Persistence:** AndroidX DataStore Preferences (no Room schema migrations needed).
- **Localization:** All user-facing strings must be localized in `values/strings.xml` and `values-it/strings.xml`.

---

### Task 1: Domain Model (`RecipeSource` & `RecipeSourceCategory`)

**Files:**
- Create: `app/src/main/java/com/delizioso/app/data/import/RecipeSource.kt`
- Test: `app/src/test/java/com/delizioso/app/data/import/RecipeSourceTest.kt`

**Interfaces:**
- Produces: `enum class RecipeSourceCategory`, `enum class RecipeSource(val id: String, val category: RecipeSourceCategory, val defaultEnabled: Boolean, val domains: List<String>)`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.delizioso.app.data.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeSourceTest {

    @Test
    fun `all expected sources exist and have unique IDs`() {
        val sources = RecipeSource.values()
        val ids = sources.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(sources.any { it.id == "giallo_zafferano" })
        assertTrue(sources.any { it.id == "the_meal_db" })
        assertTrue(sources.any { it.id == "youtube" })
        assertTrue(sources.any { it.id == "generic_web" })
    }

    @Test
    fun `lookup from ID works case-insensitively`() {
        assertEquals(RecipeSource.GIALLO_ZAFFERANO, RecipeSource.fromId("giallo_zafferano"))
        assertEquals(RecipeSource.GIALLO_ZAFFERANO, RecipeSource.fromId("GIALLO_ZAFFERANO"))
        assertNotNull(RecipeSource.fromId("tiktok"))
    }

    @Test
    fun `default active sources contains all sources with defaultEnabled true`() {
        val active = RecipeSource.defaultActiveSources()
        assertTrue(active.contains(RecipeSource.GIALLO_ZAFFERANO))
        assertTrue(active.contains(RecipeSource.GENERIC_WEB))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew test --tests "com.delizioso.app.data.import.RecipeSourceTest"`
Expected: FAIL (unresolved reference `RecipeSource`)

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/delizioso/app/data/import/RecipeSource.kt`:

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

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew test --tests "com.delizioso.app.data.import.RecipeSourceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/delizioso/app/data/import/RecipeSource.kt app/src/test/java/com/delizioso/app/data/import/RecipeSourceTest.kt
git commit -m "feat: add RecipeSource and RecipeSourceCategory domain models"
```

---

### Task 2: DataStore Preference Storage for Enabled Sources (`UserPreferences`)

**Files:**
- Modify: `app/src/main/java/com/delizioso/app/data/local/UserPreferences.kt`

**Interfaces:**
- Consumes: `RecipeSource` from Task 1
- Produces: `UserPreferences.enabledSources: Flow<Set<RecipeSource>>`, `UserPreferences.setSourceEnabled(RecipeSource, Boolean)`, `UserPreferences.setAllSourcesEnabled(Boolean)`

- [ ] **Step 1: Update `UserPreferences.kt`**

In `app/src/main/java/com/delizioso/app/data/local/UserPreferences.kt`:
1. Add `val ENABLED_SOURCES = stringSetPreferencesKey("enabled_recipe_sources")` to `Keys`.
2. Add `val enabledSources: Flow<Set<RecipeSource>>` property:
```kotlin
    val enabledSources: Flow<Set<RecipeSource>> =
        context.userDataStore.data.map { prefs ->
            val stored = prefs[Keys.ENABLED_SOURCES]
            if (stored == null) {
                RecipeSource.defaultActiveSources()
            } else {
                stored.mapNotNull { RecipeSource.fromId(it) }.toSet()
            }
        }
```
3. Add mutation methods:
```kotlin
    suspend fun setSourceEnabled(source: RecipeSource, enabled: Boolean) {
        context.userDataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_SOURCES] ?: RecipeSource.defaultActiveSources().map { it.id }.toSet()
            val updated = current.toMutableSet()
            if (enabled) updated.add(source.id) else updated.remove(source.id)
            prefs[Keys.ENABLED_SOURCES] = updated
        }
    }

    suspend fun setAllSourcesEnabled(enabled: Boolean) {
        context.userDataStore.edit { prefs ->
            prefs[Keys.ENABLED_SOURCES] = if (enabled) {
                RecipeSource.values().map { it.id }.toSet()
            } else {
                emptySet()
            }
        }
    }
```

- [ ] **Step 2: Run test suite to verify compilation and baseline tests**

Run: `.\gradlew test`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/delizioso/app/data/local/UserPreferences.kt
git commit -m "feat: add enabled recipe sources storage to UserPreferences"
```

---

### Task 3: Domain Detection & Interception in Registry (`PlatformDetector` & `RecipeImporterRegistry`)

**Files:**
- Modify: `app/src/main/java/com/delizioso/app/data/import/PlatformDetector.kt`
- Modify: `app/src/main/java/com/delizioso/app/data/import/RecipeImporterRegistry.kt`
- Modify: `app/src/main/java/com/delizioso/app/AppContainer.kt`
- Test: `app/src/test/java/com/delizioso/app/data/import/RecipeImporterRegistrySourceTest.kt`

**Interfaces:**
- Consumes: `RecipeSource`, `UserPreferences`
- Produces: `PlatformDetector.sourceFor(rawUrl: String): RecipeSource`, `RecipeImporterRegistry.import(rawUrl: String)` enforcing active sources

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/delizioso/app/data/import/RecipeImporterRegistrySourceTest.kt`:

```kotlin
package com.delizioso.app.data.import

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeImporterRegistrySourceTest {

    @Test
    fun `detects correct RecipeSource for different URLs`() {
        assertEquals(RecipeSource.GIALLO_ZAFFERANO, PlatformDetector.sourceFor("https://ricette.giallozafferano.it/Spaghetti-alla-Carbonara.html"))
        assertEquals(RecipeSource.COOKIST, PlatformDetector.sourceFor("https://www.cookist.it/tiramisu/"))
        assertEquals(RecipeSource.CUCCHIAIO, PlatformDetector.sourceFor("https://www.cucchiaio.it/ricetta/pasta-alla-norma/"))
        assertEquals(RecipeSource.RICETTE_BIMBY, PlatformDetector.sourceFor("https://www.ricetteperbimby.it/ricette/pasta/"))
        assertEquals(RecipeSource.ALL_RECIPES, PlatformDetector.sourceFor("https://www.allrecipes.com/recipe/123/pancakes/"))
        assertEquals(RecipeSource.BBC_GOOD_FOOD, PlatformDetector.sourceFor("https://www.bbcgoodfood.com/recipes/classic-scones"))
        assertEquals(RecipeSource.SERIOUS_EATS, PlatformDetector.sourceFor("https://www.seriouseats.com/easy-pan-pizza-recipe"))
        assertEquals(RecipeSource.YOUTUBE, PlatformDetector.sourceFor("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(RecipeSource.TIKTOK, PlatformDetector.sourceFor("https://www.tiktok.com/@user/video/1234567890"))
        assertEquals(RecipeSource.INSTAGRAM, PlatformDetector.sourceFor("https://www.instagram.com/reel/C123456/"))
        assertEquals(RecipeSource.FACEBOOK, PlatformDetector.sourceFor("https://www.facebook.com/reel/123456/"))
        assertEquals(RecipeSource.GENERIC_WEB, PlatformDetector.sourceFor("https://unknownrecipeblog.com/pie"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew test --tests "com.delizioso.app.data.import.RecipeImporterRegistrySourceTest"`
Expected: FAIL (unresolved reference `sourceFor`)

- [ ] **Step 3: Update `PlatformDetector.kt` and `RecipeImporterRegistry.kt`**

In `PlatformDetector.kt`:
Add `fun sourceFor(rawUrl: String): RecipeSource`:
```kotlin
    fun sourceFor(rawUrl: String): RecipeSource {
        val url = rawUrl.trim().lowercase()
        return when {
            RecipeSource.GIALLO_ZAFFERANO.domains.any { url.contains(it) } -> RecipeSource.GIALLO_ZAFFERANO
            RecipeSource.COOKIST.domains.any { url.contains(it) } -> RecipeSource.COOKIST
            RecipeSource.CUCCHIAIO.domains.any { url.contains(it) } -> RecipeSource.CUCCHIAIO
            RecipeSource.RICETTE_BIMBY.domains.any { url.contains(it) } -> RecipeSource.RICETTE_BIMBY
            RecipeSource.ALL_RECIPES.domains.any { url.contains(it) } -> RecipeSource.ALL_RECIPES
            RecipeSource.BBC_GOOD_FOOD.domains.any { url.contains(it) } -> RecipeSource.BBC_GOOD_FOOD
            RecipeSource.SERIOUS_EATS.domains.any { url.contains(it) } -> RecipeSource.SERIOUS_EATS
            RecipeSource.THE_MEAL_DB.domains.any { url.contains(it) } -> RecipeSource.THE_MEAL_DB
            youtube.find(url) != null -> RecipeSource.YOUTUBE
            tiktok.find(url) != null -> RecipeSource.TIKTOK
            instagram.find(url) != null || instagramShort.find(url) != null -> RecipeSource.INSTAGRAM
            facebook.find(url) != null || facebookShort.find(url) != null -> RecipeSource.FACEBOOK
            else -> RecipeSource.GENERIC_WEB
        }
    }
```

In `RecipeImporterRegistry.kt`:
Pass `enabledSourcesProvider: () -> Set<RecipeSource> = { RecipeSource.values().toSet() }` and check:
```kotlin
    suspend fun import(rawUrl: String): RawImport {
        val source = PlatformDetector.sourceFor(rawUrl)
        val enabled = enabledSourcesProvider()
        if (source !in enabled && RecipeSource.GENERIC_WEB !in enabled) {
            throw ImportException("This recipe source is disabled in Settings", retryable = false)
        }
        val importer = importerFor(rawUrl)
            ?: throw ImportException("This link is not supported yet")
        return importer.fetch(rawUrl)
    }
```

In `AppContainer.kt`:
Wire `RecipeImporterRegistry` to read enabled sources from `preferences`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew test --tests "com.delizioso.app.data.import.RecipeImporterRegistrySourceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/delizioso/app/data/import/PlatformDetector.kt app/src/main/java/com/delizioso/app/data/import/RecipeImporterRegistry.kt app/src/main/java/com/delizioso/app/AppContainer.kt app/src/test/java/com/delizioso/app/data/import/RecipeImporterRegistrySourceTest.kt
git commit -m "feat: add domain source detection and enabled-source enforcement in RecipeImporterRegistry"
```

---

### Task 4: Settings UI & Navigation (`RecipeSourcesScreen`, `RecipeSourcesViewModel`, `ProfileScreen`, `DeliziosoNav`)

**Files:**
- Create: `app/src/main/java/com/delizioso/app/ui/screens/profile/RecipeSourcesScreen.kt`
- Modify: `app/src/main/java/com/delizioso/app/ui/screens/profile/ProfileScreen.kt`
- Modify: `app/src/main/java/com/delizioso/app/ui/DeliziosoNav.kt`
- Modify: `app/src/main/java/com/delizioso/app/ui/DeliziosoApp.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-it/strings.xml`

**Interfaces:**
- Consumes: `UserPreferences.enabledSources`, `RecipeSource`, `RecipeSourceCategory`
- Produces: `RecipeSourcesScreen` composable, `Routes.RECIPE_SOURCES` navigation

- [ ] **Step 1: Add strings to `strings.xml` and `values-it/strings.xml`**

Add localized string entries for:
- `sources_title`: "Recipe Sources" / "Fonti ricette"
- `sources_enable_all`: "Enable all" / "Attiva tutte"
- `sources_disable_all`: "Disable all" / "Disattiva tutte"
- `sources_cat_search`: "Search Databases" / "Database di ricerca"
- `sources_cat_italian`: "Italian Recipe Portals" / "Portali di ricette italiani"
- `sources_cat_intl`: "International Sites" / "Siti internazionali"
- `sources_cat_social`: "Social Media & Video" / "Social media e video"
- `sources_cat_generic`: "Generic Web" / "Web generico"
- `profile_sources_title`: "Recipe Sources" / "Fonti ricette"
- `profile_sources_subtitle`: "Manage active portals and scrapers" / "Gestisci portali e scraper attivi"
- `import_source_disabled`: "This recipe source is disabled in Settings" / "Questa fonte di ricette è disattivata nelle impostazioni"

- [ ] **Step 2: Create `RecipeSourcesScreen.kt`**

Create `app/src/main/java/com/delizioso/app/ui/screens/profile/RecipeSourcesScreen.kt`:
1. `RecipeSourcesViewModel` observing `preferences.enabledSources` and providing `toggleSource(RecipeSource, Boolean)` and `setAll(Boolean)`.
2. `RecipeSourcesScreen` displaying top bar, global toggle buttons, and categorized lists with Clay card items and toggle switches.

- [ ] **Step 3: Connect navigation in `DeliziosoNav.kt`, `DeliziosoApp.kt`, and `ProfileScreen.kt`**

1. In `DeliziosoNav.kt`, add `const val RECIPE_SOURCES = "profile/sources"`.
2. In `DeliziosoApp.kt`, add `composable(Routes.RECIPE_SOURCES)` navigating to `RecipeSourcesScreen(onBack = { navController.popBackStack() })`.
3. In `ProfileScreen.kt`, add a clickable card in Settings navigating to `onOpenRecipeSources()`.

- [ ] **Step 4: Run full test suite & build**

Run: `.\gradlew test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/delizioso/app/ui/screens/profile/RecipeSourcesScreen.kt app/src/main/java/com/delizioso/app/ui/screens/profile/ProfileScreen.kt app/src/main/java/com/delizioso/app/ui/DeliziosoNav.kt app/src/main/java/com/delizioso/app/ui/DeliziosoApp.kt app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml
git commit -m "feat: add RecipeSourcesScreen with per-source toggles in Settings"
```

---

### Task 5: Final Device Deployment & Verification

**Files:**
- Modify: `BACKLOG.md`

- [ ] **Step 1: Run complete Gradle test suite**

Run: `.\gradlew test`
Expected: All tests pass.

- [ ] **Step 2: Build & install onto connected Pixel 10 Pro**

Run: `$env:PATH = "$env:LOCALAPPDATA\Android\Sdk\platform-tools;$env:PATH"; .\gradlew installDebug`
Expected: BUILD SUCCESSFUL and installed on 1 device.

- [ ] **Step 3: Update documentation and commit**

```bash
git add BACKLOG.md
git commit -m "docs: update backlog with modular recipe sources registry completion"
```
