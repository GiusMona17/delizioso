package com.delizioso.app.data.search

import com.delizioso.app.data.import.ImportHttp
import com.delizioso.app.data.import.RecipeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * Searches the Italian recipe portal GialloZafferano via HTML scraping.
 */
class GialloZafferanoSearchProvider(
    private val client: OkHttpClient = ImportHttp.client,
) : RecipeSearchProvider {

    override val source: RecipeSource = RecipeSource.GIALLO_ZAFFERANO

    override suspend fun searchByName(query: String): List<OnlineSearchResult> = searchUrl(
        "https://www.giallozafferano.it/ricerca-ricette/${encode(query.trim())}/"
    )

    override suspend fun searchByIngredient(ingredient: String): List<OnlineSearchResult> = searchUrl(
        "https://www.giallozafferano.it/ricerca-ricette/${encode(ingredient.trim())}/"
    )

    private suspend fun searchUrl(targetUrl: String): List<OnlineSearchResult> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()

        val response = runCatching { client.newCall(request).execute() }.getOrNull() ?: return@withContext emptyList()
        val html = response.use { res ->
            if (!res.isSuccessful) return@withContext emptyList()
            res.body?.string() ?: return@withContext emptyList()
        }

        parseSearchResults(html)
    }

    fun parseSearchResults(html: String): List<OnlineSearchResult> {
        val doc = Jsoup.parse(html, "https://ricette.giallozafferano.it/")
        val articles = doc.select("article.gz-card, .gz-card-vertical, article.card")
        val results = mutableListOf<OnlineSearchResult>()
        val seenUrls = mutableSetOf<String>()

        for (article in articles) {
            val linkEl = article.selectFirst("a[href]") ?: continue
            val href = linkEl.attr("abs:href").trim()
            if (href.isEmpty() || !href.contains("giallozafferano.it") || !href.endsWith(".html")) {
                continue
            }
            if (seenUrls.contains(href)) continue
            seenUrls.add(href)

            val titleEl = article.selectFirst(".gz-title, h4, h2, .card-title") ?: continue
            val title = titleEl.text().trim()
            if (title.isEmpty()) continue

            val imgEl = article.selectFirst("img")
            val imgUrl = imgEl?.attr("src")?.takeIf { it.isNotBlank() && !it.contains("data:image") }
                ?: imgEl?.attr("data-src")?.takeIf { it.isNotBlank() }

            results.add(
                OnlineSearchResult(
                    id = href,
                    title = title,
                    thumbnailUrl = imgUrl,
                    source = RecipeSource.GIALLO_ZAFFERANO
                )
            )
        }

        return results
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
