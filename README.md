# 🍝 Delizioso!

> A beautiful, local-first Android recipe manager, smart importer, and cooking assistant with on-device intelligence.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-purple.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android%2014%2B%20(API%2034%2B)-green.svg)](https://developer.android.com)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%203-blue.svg)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Database-Room%202.7-orange.svg)](https://developer.android.com/training/data-storage/room)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 📖 Overview

**Delizioso!** is an open-source, privacy-respecting Android application designed to collect, organize, scale, and cook recipes gathered from web portals, social media, scanned cookbooks, or manual entry. 

Built from the ground up on a **local-first** philosophy, all data, photos, and calculations live on your device.

---

## ✨ Features

### 📥 1. Smart Multi-Platform Recipe Importer
- **Generic Web & Schema.org**: Automatically extracts and parses structured `Recipe` JSON-LD from food blogs and websites worldwide.
- **Dedicated Portals & Scrapers**:
  - 🇮🇹 *Italian Portals*: GialloZafferano, Cookist, Il Cucchiaio d'Argento, Ricette per Bimby.
  - 🌍 *International Sites*: BBC Good Food, AllRecipes, Serious Eats, TheMealDB.
- **Social Media Captions**: Extracts recipes embedded in video descriptions and captions from **Instagram Reels**, **TikTok**, **YouTube**, and **Facebook**.
- **Cookbook OCR**: Scan physical cookbook pages or handwritten recipe cards using **ML Kit Text Recognition v2**.
- **Plain Text / Paste**: Paste messy text or formatted JSON directly to structure into a recipe.

### 🔎 2. Multi-Source Online Search Aggregator
- Parallel search across multiple cooking databases simultaneously (TheMealDB, GialloZafferano, Cookist, Bimby, BBC Good Food).
- **Curated Italian Ingredient Dictionary**: 150+ common cooking terms in Italian with dynamic translation to English when querying international databases.
- **Modular Sources Registry**: Toggle active or disabled search sources and scrapers in Settings.

### ⚖️ 3. Dynamic Portion Scaling & Robust Ingredient Parsing
- **Intelligent Ingredient Parsing**: Recognizes both standard leading amounts (`320 g spaghetti`, `3 eggs`) and Italian trailing quantity formats (`Spaghetti 320 g`, `Guanciale 150g`, `Tuorli 6`, `Sale fino q.b.`).
- **Servings Stepper**: Dynamically multiplies ingredient amounts as you change the number of portions.
- **Fixed Quantity Warnings**: Warns if an ingredient has unscaleable or non-numeric amounts and offers one-click AI structuring.

### 🥗 4. Nutritional Macros & AI Enrichment
- **Deterministic Macro Calculation**: Calculates calories, protein, fat, and carbs per serving using a curated local ingredient density and nutrition lookup table.
- **External AI Enrichment**: Generate structured prompts for external AI assistants (ChatGPT, Claude, Gemini) to extract and calculate macros, preparation/cooking times, portions, and scaleable quantities, saving them directly into Room database (Schema v5).

### 👨‍🍳 5. Interactive Cooking Mode & Meal Planning
- **Step-by-Step Cooking View**: Clean fullscreen steps with screen wake-lock, quick ingredient drawers, and integrated timers.
- **Weekly Meal Planner**: Assign recipes to breakfast, lunch, dinner, or snack slots across calendar days.
- **Smart Grocery List**: Aggregates ingredients across planned meals with interactive checklist.

### 💾 6. Privacy & Offline Backups
- **100% On-Device**: No analytics, no third-party trackers, no mandatory cloud accounts.
- **Complete Library Archive**: Export and restore your entire recipe collection (metadata, ingredients, instructions, and full-resolution photos) as a single portable `.zip` backup file.

### 🎨 7. Culinary Clay UI
- Distinctive claymorphic / neumorphic visual language with tactile bevels, pill shapes, soft depth shadows, and warm culinary color palettes.
- Adaptive Light and Dark theme support.

---

## 🛠️ Architecture & Tech Stack

```
app/
 ├── data/
 │    ├── ai/            # Gemini Nano / Gemma Prompt API & ML Kit OCR
 │    ├── backup/        # ZIP archive exporter and restorer
 │    ├── export/        # AI prompt generators, JSON & Markdown export
 │    ├── import/        # Scrapers, JSON-LD parser, IngredientParser, Registry
 │    ├── local/         # Room Database (v5), DAOs, Entities, DataStore UserPreferences
 │    ├── nutrition/     # MacroCalculator and curated NutritionTable
 │    └── search/        # Multi-source search aggregator & IngredientDictionary
 ├── ui/
 │    ├── components/    # Reusable Claymorphic UI tokens, buttons, cards, headers
 │    ├── navigation/    # Jetpack Compose Navigation routes
 │    ├── screens/       # Detail, Create, Import, Edit, Search, Planner, Grocery, Profile
 │    └── theme/         # Clay design system colors, bevels, typography, shapes
 └── DeliziosoApplication.kt
```

- **Language:** [Kotlin 2.1](https://kotlinlang.org/)
- **UI Toolkit:** [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3
- **Database:** [Room 2.7](https://developer.android.com/training/data-storage/room) with automated migrations
- **Preferences:** [Jetpack DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- **Async & Concurrency:** Kotlin Coroutines & Reactive `StateFlow`
- **Serialization:** `kotlinx.serialization` (JSON)
- **HTML & Scrapers:** `Jsoup`
- **Vision & On-Device AI:** Google ML Kit Text Recognition v2 + Generative Prompt API

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio Ladybug | 2024.2+** (or command-line Gradle)
- **JDK 17** or higher
- **Android Device / Emulator** running Android 14+ (API 34+)

### Building and Running

1. **Clone the repository:**
   ```bash
   git clone https://github.com/GiusMona17/delizioso.git
   cd delizioso
   ```

2. **Run Unit Tests:**
   ```bash
   ./gradlew test
   ```

3. **Build the Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Install on a connected device:**
   ```bash
   ./gradlew installDebug
   ```

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.
