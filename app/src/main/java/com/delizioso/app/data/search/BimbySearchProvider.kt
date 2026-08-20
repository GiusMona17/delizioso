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
 * Searches the Italian recipe portal Ricette per Bimby (ricetteperbimby.it).
 */
class BimbySearchProvider(
    private val client: OkHttpClient = ImportHttp.client,
) : RecipeSearchProvider {

    override val source: RecipeSource = RecipeSource.RICETTE_BIMBY

    override suspend fun searchByName(query: String): List<OnlineSearchResult> = searchUrl(
        "https://www.ricetteperbimby.it/?s=${encode(query.trim())}"
    )

    override suspend fun searchByIngredient(ingredient: String): List<OnlineSearchResult> = searchUrl(
        "https://www.ricetteperbimby.it/?s=${encode(ingredient.trim())}"
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
        val doc = Jsoup.parse(html, "https://www.ricetteperbimby.it/")
        val results = mutableListOf<OnlineSearchResult>()
        val seenUrls = mutableSetOf<String>()

        // 1. Try JSON-LD ItemList first
        for (script in doc.select("script[type=application/ld+json]")) {
            val jsonText = script.data()
            if (jsonText.contains("ItemList") && jsonText.contains("ListItem")) {
                val urlRegex = Regex(""""url"\s*:\s*"([^"]+)"""")
                val nameRegex = Regex(""""name"\s*:\s*"([^"]+)"""")
                val imageRegex = Regex(""""image"\s*:\s*"([^"]+)"""")

                val urls = urlRegex.findAll(jsonText).map { it.groupValues[1].replace("\\/", "/") }.toList()
                val names = nameRegex.findAll(jsonText).map { it.groupValues[1] }.toList()
                val images = imageRegex.findAll(jsonText).map { it.groupValues[1].replace("\\/", "/") }.toList()

                for (i in urls.indices) {
                    val url = urls[i]
                    if (url.contains("/ricette/") && !seenUrls.contains(url)) {
                        seenUrls.add(url)
                        val title = names.getOrNull(i)?.takeIf { it.isNotBlank() } ?: "Ricetta Bimby"
                        val image = images.getOrNull(i)
                        results.add(
                            OnlineSearchResult(
                                id = url,
                                title = title,
                                thumbnailUrl = image,
                                source = RecipeSource.RICETTE_BIMBY
                            )
                        )
                    }
                }
            }
        }

        // 2. Fallback to HTML anchor tags if JSON-LD was empty
        if (results.isEmpty()) {
            val links = doc.select("a[href*=/ricette/]")
            for (link in links) {
                val href = link.attr("abs:href").trim()
                if (href.isEmpty() || href == "https://www.ricetteperbimby.it/ricette/" || seenUrls.contains(href)) {
                    continue
                }
                val title = link.text().trim().takeIf { it.isNotBlank() }
                    ?: link.attr("title").trim().takeIf { it.isNotBlank() }
                    ?: continue

                if (title.length < 3) continue
                seenUrls.add(href)

                val img = link.selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() }

                results.add(
                    OnlineSearchResult(
                        id = href,
                        title = title,
                        thumbnailUrl = img,
                        source = RecipeSource.RICETTE_BIMBY
                    )
                )
            }
        }

        return results
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
