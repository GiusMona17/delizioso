package com.delizioso.app.data.import

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Cooking sites/blogs — fetch with a mobile browser fingerprint, then:
 *  1. schema.org Recipe JSON-LD (also nested in application/json hydration blocks);
 *  2. fallback: readability-style main-content text for the LLM to structure.
 * Site-level bot blocks are expected from datacenter IPs (spike); on-device fetching
 * (user ISP IP, WebView TLS) is the resilient path.
 */
class BlogImporter(
    private val client: OkHttpClient = ImportHttp.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : RecipeImporter {

    override val platform: Platform = Platform.BLOG

    override suspend fun fetch(rawUrl: String): RawImport {
        val request = Request.Builder().url(rawUrl).get().build()
        val response = client.newCallSuspend(request)
        if (!response.isSuccessful) {
            throw ImportException("The site returned HTTP ${response.code}", retryable = true)
        }
        val html = response.body?.string().orEmpty()
        if (html.isBlank()) throw ImportException("Empty page from $rawUrl", retryable = true)

        val structured = RecipeJsonLdParser.parse(html, json)
        if (structured != null) {
            return RawImport(
                platform = platform.key,
                url = rawUrl,
                author = siteName(html),
                content = ImportContent.Structured(structured),
                thumbnailUrl = structured.imageUrl ?: ogImage(html),
            )
        }

        val doc = Jsoup.parse(html)
        val text = readabilityText(doc)
        if (text.isBlank()) {
            throw ImportException("Could not read any recipe content from this page", retryable = true)
        }
        return RawImport(
            platform = platform.key,
            url = rawUrl,
            author = siteName(html),
            content = ImportContent.RawText(text = text, title = doc.title().takeIf { it.isNotBlank() }),
            thumbnailUrl = ogImage(html),
        )
    }

    /** og:image is the near-universal cover image on recipe sites. */
    private fun ogImage(html: String): String? = Jsoup.parse(html)
        .selectFirst("meta[property=og:image], meta[name=og:image]")
        ?.attr("content")
        ?.takeIf { it.isNotBlank() }

    /** Minimal readability: main/article/role=main content minus boilerplate. */
    private fun readabilityText(doc: Document): String {
        val main = listOfNotNull(
            doc.selectFirst("main"),
            doc.selectFirst("article"),
            doc.selectFirst("[role=main]"),
            doc.body(),
        ).firstOrNull() ?: return ""
        main.select("script, style, noscript, nav, header, footer, aside, form, [class*=advert], [class*=ad], [class*=share], [class*=newsletter], [class*=related]").remove()
        return main.text().trim().take(8000)
    }

    private fun siteName(html: String): String? {
        val og = Regex("""<meta[^>]+property=["']og:site_name["'][^>]+content=["']([^"']+)["']""", setOf(RegexOption.IGNORE_CASE))
            .find(html)?.groupValues?.get(1)
        if (!og.isNullOrBlank()) return og
        return Regex("""<meta[^>]+name=["']author["'][^>]+content=["']([^"']+)["']""", setOf(RegexOption.IGNORE_CASE))
            .find(html)?.groupValues?.get(1)
    }
}
