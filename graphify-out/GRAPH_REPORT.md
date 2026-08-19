# Graph Report - delizioso  (2026-08-18)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 851 nodes · 1861 edges · 54 communities (32 shown, 22 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 46 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `ca2a1f39`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- ClayComponents.kt
- RecipeWithDetails
- ImportViewModel.kt
- Json
- EditRecipeScreen.kt
- RecipeRepository
- clayBevel
- AiUnavailableException
- RecipeDetailScreen.kt
- UserPreferences
- ProfileScreen.kt
- StructuredRecipe
- GroceryViewModel.kt
- DeliziosoApplication.kt
- ImageStore
- Platform
- RecipeImporter.kt
- TikTokImporter
- UnitConverterTest
- ChatMessage
- RecipeTranslator
- CaptionLinesTest
- RecipeChat
- BackupManager
- MainActivity.kt
- MacroCalculatorTest
- BlogImporter
- RecipeImporter
- CategoriesTest
- CaptionRecipeParserTest
- QuantitiesScaleInTextTest
- PastedRecipeParserTest
- PlatformDetectorTest
- SharedLinkTest
- StepTimerTest
- CaptionLines
- CaptionRecipeParser
- PastedRecipeParser
- AppDatabase
- UnitConverter
- NanoChatTest
- IngredientParserTest
- QuantitiesTest
- UnitNamesTest
- ChatPrompt
- OcrTextExtractor.kt
- SharedLink
- ImperialUnitsTest
- StepTimer
- gradlew

## God Nodes (most connected - your core abstractions)
1. `StructuredRecipe` - 53 edges
2. `RecipeWithDetails` - 52 edges
3. `ClayButton()` - 32 edges
4. `RecipeDao` - 29 edges
5. `RecipeRepository` - 28 edges
6. `ImportViewModel` - 23 edges
7. `RecipeEntity` - 22 edges
8. `RecipeDetailScreen()` - 21 edges
9. `AppContainer` - 20 edges
10. `UserPreferences` - 20 edges

## Surprising Connections (you probably didn't know these)
- `NanoChatTest` --calls--> `NanoChat`  [INFERRED]
  app/src/test/java/com/delizioso/app/data/ai/NanoChatTest.kt → app/src/main/java/com/delizioso/app/data/ai/NanoChat.kt
- `AddToPlannerSheet()` --calls--> `ClayButton()`  [INFERRED]
  app/src/main/java/com/delizioso/app/ui/components/AddToPlannerSheet.kt → app/src/main/java/com/delizioso/app/ui/components/ClayComponents.kt
- `RefineRecipeCard()` --calls--> `ClayButton()`  [INFERRED]
  app/src/main/java/com/delizioso/app/ui/components/RefineRecipeCard.kt → app/src/main/java/com/delizioso/app/ui/components/ClayComponents.kt
- `AddToPlannerSheet()` --calls--> `ClayRoundButton()`  [INFERRED]
  app/src/main/java/com/delizioso/app/ui/components/AddToPlannerSheet.kt → app/src/main/java/com/delizioso/app/ui/components/ClayComponents.kt
- `EditableLineRow()` --calls--> `ClayTextField()`  [INFERRED]
  app/src/main/java/com/delizioso/app/ui/components/RecipeFormComponents.kt → app/src/main/java/com/delizioso/app/ui/components/ClayComponents.kt

## Import Cycles
- None detected.

## Communities (54 total, 22 thin omitted)

### Community 0 - "ClayComponents.kt"
Cohesion: 0.07
Nodes (71): PlannedMealWithRecipe, ClayAddPanel(), ClayButton(), ClayCheckbox(), ClayChip(), ClayEmptyState(), ClayFilterChip(), ClayGroupLabel() (+63 more)

### Community 1 - "RecipeWithDetails"
Cohesion: 0.07
Nodes (18): BackupFile, BackupIngredient, BackupRecipe, BackupSource, toBackup(), toDetails(), IngredientEntity, RecipeEntity (+10 more)

### Community 2 - "ImportViewModel.kt"
Cohesion: 0.06
Nodes (35): ImportContent, ImportException, Exception, RawImport, RawText, Structured, TikTokOEmbed, YouTubeApiResponse (+27 more)

### Community 3 - "Json"
Cohesion: 0.07
Nodes (13): Categories, RecipeExport, RecipeJsonLdParser, MacroCalculator, Macros, Nutrient, NutritionTable, Quantities (+5 more)

### Community 4 - "EditRecipeScreen.kt"
Cohesion: 0.11
Nodes (34): Failed, Idle, StateFlow, NothingToTranslate, RecipeRefiner, RefineState, Running, ClayLabelledField() (+26 more)

### Community 5 - "RecipeRepository"
Cohesion: 0.06
Nodes (20): MealSlot, PlannedMealEntity, Flow, RecipeRepository, PhotoArea(), CreateBusy, OCR, STRUCTURING (+12 more)

### Community 6 - "clayBevel"
Cohesion: 0.11
Nodes (34): AddToPlannerSheet(), dateLabel(), DayPill(), ImageVector, Modifier, mealSlotIcon(), MealTypeOption(), ClayDock() (+26 more)

### Community 7 - "AiUnavailableException"
Cohesion: 0.11
Nodes (13): GemmaEngine, Uri, Availability, AVAILABLE, DOWNLOADABLE, UNAVAILABLE, NanoInference, AiUnavailableException (+5 more)

### Community 8 - "RecipeDetailScreen.kt"
Cohesion: 0.12
Nodes (21): Dp, Modifier, PhotoPickerArea(), ChatState, ExportSheet(), HeaderCard(), IngredientList(), android (+13 more)

### Community 9 - "UserPreferences"
Cohesion: 0.09
Nodes (9): CustomGroceryLine, Keys, Flow, UserPreferences, CookViewModel, android, StateFlow, ViewModel (+1 more)

### Community 10 - "ProfileScreen.kt"
Cohesion: 0.13
Nodes (18): BackupControls(), BackupState, Exported, Failed, Idle, android, androidx, Modifier (+10 more)

### Community 11 - "StructuredRecipe"
Cohesion: 0.15
Nodes (5): NanoStructurer, ImperialUnits, StructuredRecipe, IngredientParser, toStructuredRecipe()

### Community 12 - "GroceryViewModel.kt"
Cohesion: 0.18
Nodes (7): GroceryAggregator, GroceryCategories, GroceryItem, GroceryViewModel, StateFlow, ViewModel, ViewModelProvider

### Community 13 - "DeliziosoApplication.kt"
Cohesion: 0.19
Nodes (8): OcrTextExtractor, FacebookImporter, Platform, InstagramImporter, Platform, AppContainer, DeliziosoApplication, Application

### Community 14 - "ImageStore"
Cohesion: 0.39
Nodes (4): ImageStore, Context, Uri, ByteArray

### Community 15 - "Platform"
Cohesion: 0.18
Nodes (7): Platform, BLOG, FACEBOOK, INSTAGRAM, TIKTOK, YOUTUBE, PlatformDetector

### Community 16 - "RecipeImporter.kt"
Cohesion: 0.27
Nodes (7): ImportHttp, newCallSuspend(), Platform, YouTubeImporter, OkHttpClient, Request, Response

### Community 17 - "TikTokImporter"
Cohesion: 0.27
Nodes (4): Platform, TikTokImporter, TikTokImporterTest, MockWebServer

### Community 19 - "ChatMessage"
Cohesion: 0.31
Nodes (6): ChatMessage, Flow, NanoChat, Role, ASSISTANT, USER

### Community 20 - "RecipeTranslator"
Cohesion: 0.27
Nodes (4): AlreadyInTargetLanguage, Exception, RecipeTranslator, UnitNames

### Community 22 - "RecipeChat"
Cohesion: 0.33
Nodes (5): Engine, GEMMA, NANO, Flow, RecipeChat

### Community 23 - "BackupManager"
Cohesion: 0.36
Nodes (5): BackupException, BackupManager, Exception, Uri, RestoreResult

### Community 24 - "MainActivity.kt"
Cohesion: 0.33
Nodes (5): Intent, MainActivity, DeliziosoTheme(), Bundle, ComponentActivity

### Community 26 - "BlogImporter"
Cohesion: 0.39
Nodes (3): BlogImporter, Platform, Document

### Community 27 - "RecipeImporter"
Cohesion: 0.32
Nodes (4): Platform, RecipeImporter, Platform, RecipeImporterRegistry

### Community 38 - "AppDatabase"
Cohesion: 0.47
Nodes (3): AppDatabase, Context, RoomDatabase

### Community 45 - "OcrTextExtractor.kt"
Cohesion: 0.50
Nodes (3): awaitResult(), Bitmap, T

### Community 49 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **36 isolated node(s):** `Idle`, `Working`, `RawText`, `Structured`, `TikTokOEmbed` (+31 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **22 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `StructuredRecipe` connect `StructuredRecipe` to `RecipeWithDetails`, `ImportViewModel.kt`, `Json`, `EditRecipeScreen.kt`, `CaptionRecipeParser`, `PastedRecipeParser`, `RecipeRepository`, `UnitConverter`, `RecipeDetailScreen.kt`, `NanoChatTest`, `ChatPrompt`, `UnitConverterTest`, `ChatMessage`, `RecipeTranslator`, `RecipeChat`, `MacroCalculatorTest`?**
  _High betweenness centrality (0.167) - this node is a cross-community bridge._
- **Why does `RecipeWithDetails` connect `RecipeWithDetails` to `ClayComponents.kt`, `ImportViewModel.kt`, `Json`, `EditRecipeScreen.kt`, `RecipeRepository`, `clayBevel`, `RecipeDetailScreen.kt`, `UserPreferences`, `StructuredRecipe`, `GroceryViewModel.kt`?**
  _High betweenness centrality (0.156) - this node is a cross-community bridge._
- **Why does `RecipeRepository` connect `RecipeRepository` to `ClayComponents.kt`, `RecipeWithDetails`, `ImportViewModel.kt`, `EditRecipeScreen.kt`, `RecipeDetailScreen.kt`, `UserPreferences`, `ProfileScreen.kt`, `GroceryViewModel.kt`, `DeliziosoApplication.kt`, `BackupManager`?**
  _High betweenness centrality (0.067) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `ClayButton()` (e.g. with `AddToPlannerSheet()` and `RefineRecipeCard()`) actually correct?**
  _`ClayButton()` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Idle`, `Working`, `RawText` to the rest of the system?**
  _36 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `ClayComponents.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.07314814814814814 - nodes in this community are weakly interconnected._
- **Should `RecipeWithDetails` be split into smaller, more focused modules?**
  _Cohesion score 0.06820119352088662 - nodes in this community are weakly interconnected._