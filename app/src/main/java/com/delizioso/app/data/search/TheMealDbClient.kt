package com.delizioso.app.data.search

import com.delizioso.app.data.import.ImportException
import com.delizioso.app.data.import.ImportHttp
import com.delizioso.app.data.import.newCallSuspend
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * TheMealDB, the app's one online recipe source.
 *
 * Free, no signup, and the `1` in the path is its public test key. Chosen over
 * Spoonacular and Edamam because it is the only one with no daily quota and no
 * terms limiting how long a recipe may be kept — which matters for a library the
 * user keeps forever. See BACKLOG.md for the full comparison.
 *
 * [baseUrl] is a parameter so MockWebServer can stand in for it in tests.
 */
class TheMealDbClient(
    private val client: OkHttpClient = ImportHttp.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    /** What an ingredient filter gives back: enough for a tile, not for a recipe. */
    data class SearchResult(val id: String, val title: String, val thumbnailUrl: String?)

    /** Full meals — `search.php` returns every field, so no second call is needed. */
    suspend fun searchByName(query: String): List<JsonObject> =
        meals("search.php?s=${encode(query.trim())}")

    /** One full meal, for a result that arrived from an ingredient filter. */
    suspend fun lookup(id: String): JsonObject? = meals("lookup.php?i=${encode(id)}").firstOrNull()

    /**
     * Every ingredient the catalogue knows — 992 of them.
     *
     * Fetched so the search screen can offer them as suggestions: the catalogue is
     * English-only, and choosing from a list beats guessing that "cipollotto" is
     * "spring onions". Never cached to disk, because ingredient search needs the
     * network anyway.
     */
    suspend fun ingredientNames(): List<String> = meals("list.php?i=list")
        .mapNotNull { (it["strIngredient"] as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }
        .sorted()

    /** Meals containing one ingredient. Returns tiles only — no ingredients, no steps. */
    suspend fun mealsWithIngredient(ingredient: String): List<SearchResult> {
        val slug = ingredient.trim().replace(' ', '_')
        return meals("filter.php?i=${encode(slug)}").mapNotNull { meal ->
            val id = MealDbMapper.mealId(meal) ?: return@mapNotNull null
            val title = (meal["strMeal"] as? JsonPrimitive)?.content?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            SearchResult(
                id = id,
                title = title,
                thumbnailUrl = (meal["strMealThumb"] as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty),
            )
        }
    }

    private suspend fun meals(path: String): List<JsonObject> {
        val request = Request.Builder().url(baseUrl.trimEnd('/') + "/" + path).get().build()
        val response = client.newCallSuspend(request)
        if (!response.isSuccessful) {
            throw ImportException("TheMealDB returned HTTP ${response.code}", retryable = true)
        }
        val body = response.body?.string().orEmpty()
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: throw ImportException("TheMealDB sent something this app could not read", retryable = true)
        // A miss is {"meals":null}, which is not an error.
        val array = root["meals"] as? JsonArray ?: return emptyList()
        return array.filterIsInstance<JsonObject>()
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val DEFAULT_BASE_URL = "https://www.themealdb.com/api/json/v1/1/"

        /**
         * Meals present in every list.
         *
         * TheMealDB's own multi-ingredient filter is behind a paid tier, so one
         * call per ingredient and an intersection here buys the same answer.
         */
        fun intersect(perIngredient: List<List<SearchResult>>): List<SearchResult> {
            if (perIngredient.isEmpty()) return emptyList()
            val shared = perIngredient
                .map { list -> list.mapTo(mutableSetOf()) { it.id } }
                .reduce { acc, ids -> (acc intersect ids).toMutableSet() }
            return perIngredient.first().filter { it.id in shared }
        }
    }
}
