package com.delizioso.app.data.search

import com.delizioso.app.data.import.ImportException
import com.delizioso.app.data.import.ImportHttp
import com.delizioso.app.data.import.newCallSuspend
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
    }
}
