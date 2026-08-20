package com.delizioso.app.data.search

import com.delizioso.app.data.import.RecipeSource

/**
 * A search result from an online provider (TheMealDB, GialloZafferano, Cookist, etc.).
 *
 * [id] is either an internal API identifier (e.g., TheMealDB meal ID) or the full recipe URL
 * for scraped portals (e.g. `https://ricette.giallozafferano.it/Spaghetti-alla-Carbonara.html`).
 */
data class OnlineSearchResult(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val source: RecipeSource,
)

/** Common interface for searching recipes across online APIs and portal scrapers. */
interface RecipeSearchProvider {
    val source: RecipeSource

    /** Search for recipes matching [query] by name. */
    suspend fun searchByName(query: String): List<OnlineSearchResult>

    /** Search for recipes containing [ingredient]. Returns empty list if unsupported. */
    suspend fun searchByIngredient(ingredient: String): List<OnlineSearchResult> = emptyList()
}
