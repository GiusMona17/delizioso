# Online recipe search — design

**Date:** 2026-08-19 · **Status:** approved, not yet implemented
**Scope:** one implementation plan. Everything deferred is listed in `BACKLOG.md`.

---

## 1. Goal

Let the user find recipes the app does not already have, by dish name or by
ingredient, and import them into the library with the flow that already exists.

Today every recipe enters through a link the user found somewhere else, or by
being typed in. There is no way to ask "what can I make with chicken and
mushrooms?".

## 2. What already exists (do not rebuild)

Measured before writing this, because a plan that re-does finished work wastes
the time it was meant to save.

| Capability | Where |
|---|---|
| schema.org JSON-LD extraction from any recipe site | `data/import/RecipeJsonLdParser.kt` |
| Fetch + readability fallback for blogs | `data/import/BlogImporter.kt` |
| Editable preview before saving | `ui/screens/import/ImportPreviewScreen.kt` |
| Photo cached locally on save | `data/ImageStore.kt` |
| Fixed category vocabulary + canonicalisation | `data/Categories.kt` |
| Unit conversion and translation, on demand | `data/UnitConverter.kt`, `data/ai/RecipeTranslator.kt` |
| Re-fetch a recipe from its source | `data/import/SourceRefresher.kt` |

GialloZafferano was checked directly: it publishes a complete JSON-LD `Recipe`
(name, ingredients, instructions, yield, ISO times, nutrition), all of which the
existing parser already reads except `nutrition`. No site-specific scraper is
needed for it.

## 3. Provider choice

**TheMealDB**, free, no signup, public test key `1`.

Endpoint shapes verified on 2026-08-19:

| Endpoint | Returns |
|---|---|
| `search.php?s=<name>` | Full meals, 54 fields, ingredients in `strIngredient1..20` + `strMeasure1..20` |
| `filter.php?i=<ingredient>` | `idMeal`, `strMeal`, `strMealThumb`, `strArea`, `strCountry` only |
| `lookup.php?i=<id>` | One full meal |
| `list.php?i=list` | 992 ingredient names |

Two consequences that shape the design:

- Ingredient search returns no ingredients or instructions, so a full recipe
  needs a second call — made **on tap**, not for every result.
- `strSource` is often `null` (it is for "Spicy Arrabiata Penne"), so the source
  URL cannot come from the API. See §7.

Rejected, with reasons, in `BACKLOG.md`: Spoonacular (quota and storage terms),
Edamam (returns links, not instructions), Chaquopy + `recipe-scrapers` (measured
to add nothing on the site it was proposed for).

## 4. Architecture

Concrete client, no provider abstraction. One implementation does not justify an
interface; extract one if a second provider ever arrives, the way `RecipeRefiner`
was extracted once two screens needed it.

```
data/search/TheMealDbClient.kt        HTTP, via the existing ImportHttp.client
data/search/MealDbMapper.kt           JSON -> StructuredRecipe
data/import/TheMealDbImporter.kt      themealdb.com/meal/<id> -> RawImport
ui/screens/search/OnlineSearchScreen.kt
ui/screens/search/OnlineSearchViewModel.kt
```

Flow:

```
Import screen
  └─ "Search online" card ──► OnlineSearchScreen
                                 │
              by name ───────────┴─────────── by ingredient
        search.php?s=                    filter.php?i= (xN, intersected)
        full recipe                      id + name + thumbnail only
                    │                                 │
                    └────────► results grid ◄─────────┘
                                 │ tap
                          lookup.php?i=<id> when needed
                                 │
                     ImportPreviewScreen (existing)
                                 │
                          Save ──► library
```

A search result becomes a `StructuredRecipe` plus a `RawImport` — exactly what
`ImportPreviewScreen` already consumes. Everything downstream (local photo copy,
"Convert and translate", category chips, macro calculation) comes for free.

**Hand-off, concretely.** `ImportPreviewScreen` reads `ImportViewModel.state`, so
the search screen cannot pass a recipe to it directly. `ImportViewModel` gains one
method beside `importLink` and `importText`:

```kotlin
fun importSearchResult(recipe: StructuredRecipe, raw: RawImport)
```

It sets `ImportUiState.Ready` and nothing else — the same shape `importText`
already produces. The search screen navigates to the preview after calling it.

Parsing does **not** use `@Serializable` DTOs for the meal object: 54 fields with
`strIngredient1..20` would mean forty declarations. The mapper reads a
`JsonObject` with a `1..20` loop, as `RecipeJsonLdParser` already does for
schema.org. The list and filter responses, which have five fields or fewer, do
use `@Serializable` DTOs, matching `ImportModels.kt`.

## 5. Search by name

One call to `search.php?s=`, which returns complete recipes. Tapping a result
opens the preview directly — no second request needed.

**Results need their own tile.** `ClayRecipeTile` binds to `RecipeWithDetails`, a
persisted entity; a search result is not saved and has no row. The grid therefore
gets a small `SearchResultTile` in `ui/screens/search/`, taking a title, a
thumbnail URL and a click — the same clay card and proportions, none of the
database coupling.

## 6. Search by ingredient

**The picker.** On opening the screen, `list.php?i=list` loads 992 ingredient
names into memory. Typing filters that list locally; each choice becomes a chip.

Nothing is cached to disk: ingredient search needs the network anyway, so an
offline copy would serve no one. The list is fetched once per screen instance.

The picker is what makes an English-only catalogue usable — the user chooses from
a list instead of guessing the English word.

**Intersection.** One `filter.php?i=` per chip (spaces become underscores), run in
parallel, then intersect on `idMeal`. Names and thumbnails come from the first
response, which already carries them.

**Honesty about the catalogue.** TheMealDB holds roughly 300 recipes, so three
ingredients together will often match nothing. The empty state names the
combination that emptied the search and leaves the chips ready to remove, rather
than saying "no results" and stopping there.

## 7. Mapping, and what the API does not have

| Field | Source |
|---|---|
| `title` | `strMeal` |
| `ingredients` | loop `1..20`: `"$strMeasure$i $strIngredient$i"` through `IngredientParser.split` |
| `steps` | `strInstructions` split on newlines, blanks dropped, leading numbering stripped |
| `categories` | `Categories.canonicalise(strCategory + strArea)` |
| `imageUrl` | `strMealThumb` |
| `servings`, `prepTimeMinutes`, `cookTimeMinutes` | **null** — the API has none, and inventing them would be worse than leaving them empty |

Empty entries inside the `1..20` range arrive as empty strings or `null`; both are
skipped rather than becoming blank ingredients.

Absent servings has a visible consequence worth stating in advance: the macro
panel will read *"For the whole recipe (no serving count)"*. That is the correct
and honest behaviour, not a defect to paper over.

**Source and refresh.** `strSource` is unreliable, so the stored source URL is
`https://www.themealdb.com/meal/<id>` under a new `Platform.MEALDB`, with
`TheMealDB` as the author.

A ~30-line `TheMealDbImporter` registered in `RecipeImporterRegistry` recognises
those URLs and returns `ImportContent.Structured`. This makes **Refresh work on
these recipes** and makes the URLs pasteable in the link field, reusing the client
written for search. Without it these would be the only recipes in the library that
cannot be refreshed.

`PlatformDetector` gains a branch for the `themealdb.com` host.

## 8. Image loading

Coil currently does not use `ImportHttp.client`, so remote thumbnails carry no
browser User-Agent. A shared `ImageLoader` built on that client fixes it.

Included here rather than deferred because the search results grid is the first
screen in the app that is mostly remote images.

## 9. Error states

| Situation | Behaviour |
|---|---|
| Network failure | Message and Retry, matching `ImportScreen`'s existing error card |
| Name search with no hits | Empty state suggesting the English name |
| Intersection empty | Names the combination that emptied it; chips stay, ready to remove |
| Ingredient list unavailable | Picker degrades to free text; the feature still works |

## 10. Testing

`okhttp-mockwebserver` is already a test dependency. Real payloads captured on
2026-08-19 become fixtures.

- **Mapper:** the `1..20` loop with gaps and nulls mid-range; instruction
  splitting; category canonicalisation; null servings and times preserved as null.
- **Client:** 200, empty `{"meals":null}`, and malformed responses.
- **Intersection:** pure function — zero chips, one chip, several chips, and a
  combination that intersects to nothing.

No instrumentation tests: the project has none, and this is not the feature that
should introduce them.

## 11. Out of scope

Deferred to `BACKLOG.md` with reasons: Spoonacular, Chaquopy + `recipe-scrapers`,
`nutrition` mapping from JSON-LD (that belongs to blog import — TheMealDB has no
nutrition data), automatic translation, and Italian ingredient aliases.
