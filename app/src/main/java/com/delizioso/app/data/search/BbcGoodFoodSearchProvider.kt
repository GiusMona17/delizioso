package com.delizioso.app.data.search

import com.delizioso.app.data.import.ImportHttp
import com.delizioso.app.data.import.RecipeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * Searches the international recipe portal BBC Good Food (bbcgoodfood.com).
 */
class BbcGoodFoodSearchProvider(
    private val client: OkHttpClient = ImportHttp.client,
) : RecipeSearchProvider {

    override val source: RecipeSource = RecipeSource.BBC_GOOD_FOOD

    override suspend fun searchByName(query: String): List<OnlineSearchResult> = searchUrl(
        "https://www.bbcgoodfood.com/search?q=${encode(query.trim())}"
    )

    override suspend fun searchByIngredient(ingredient: String): List<OnlineSearchResult> = searchUrl(
        "https://www.bbcgoodfood.com/search?q=${encode(ingredient.trim())}"
    )

    private suspend fun searchUrl(targetUrl: String): List<OnlineSearchResult> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-GB,en-US;q=0.9,en;q=0.8")
            .build()

        val response = runCatching { client.newCall(request).execute() }.getOrNull() ?: return@withContext emptyList()
        val html = response.use { res ->
            if (!res.isSuccessful) return@withContext emptyList()
            res.body?.string() ?: return@withContext emptyList()
        }

        parseSearchResults(html)
    }

    fun parseSearchResults(html: String): List<OnlineSearchResult> {
        val doc = Jsoup.parse(html, "https://www.bbcgoodfood.com/")
        val results = mutableListOf<OnlineSearchResult>()
        val seenUrls = mutableSetOf<String>()

        // 1. Try Next.js __NEXT_DATA__ JSON
        val nextDataEl = doc.selectFirst("script#__NEXT_DATA__")
        if (nextDataEl != null) {
            runCatching {
                val root = Json.parseToJsonElement(nextDataEl.data()).jsonObject
                val pageProps = root["props"]?.jsonObject?.get("pageProps")?.jsonObject
                val items = pageProps?.get("items")?.jsonArray
                if (items != null) {
                    for (element in items) {
                        val item = element.jsonObject
                        val title = item["title"]?.jsonPrimitive?.content.orEmpty()
                        val url = item["url"]?.jsonPrimitive?.content.orEmpty()
                        val imageObj = item["image"]?.jsonObject
                        val imageUrl = imageObj?.get("url")?.jsonPrimitive?.content

                        if (url.isNotBlank() && url.contains("/recipes/") && !seenUrls.contains(url)) {
                            seenUrls.add(url)
                            results.add(
                                OnlineSearchResult(
                                    id = url,
                                    title = title.ifBlank { "Recipe" },
                                    thumbnailUrl = imageUrl?.takeIf { it.isNotBlank() },
                                    source = RecipeSource.BBC_GOOD_FOOD
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2. Fallback to HTML anchor tags if Next.js data was empty
        if (results.isEmpty()) {
            val recipeLinks = doc.select("a[href*=/recipes/]")
            for (link in recipeLinks) {
                val href = link.attr("abs:href").trim()
                if (href.isEmpty() || href == "https://www.bbcgoodfood.com/recipes" || href.endsWith("/recipes/") || seenUrls.contains(href)) {
                    continue
                }
                val title = link.selectFirst("h2, h3, h4, .heading")?.text()?.trim()
                    ?: link.text().trim().takeIf { it.isNotBlank() }
                    ?: continue

                if (title.length < 3 || title.equals("Recipes", ignoreCase = true)) continue
                seenUrls.add(href)

                val img = link.selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() }

                results.add(
                    OnlineSearchResult(
                        id = href,
                        title = title,
                        thumbnailUrl = img,
                        source = RecipeSource.BBC_GOOD_FOOD
                    )
                )
            }
        }

        return results
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
