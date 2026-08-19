package com.delizioso.app.data.import

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * TikTok — oEmbed first (no auth, verified in the spike: `title` = full caption).
 * The watch-page `__UNIVERSAL_DATA_FOR_REHYDRATION__` fallback is delegated to the
 * WebView extractor for login-walled videos.
 */
class TikTokImporter(
    private val client: OkHttpClient = ImportHttp.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    /** Injectable for tests (MockWebServer); production default is the public endpoint. */
    private val oEmbedBase: String = "https://www.tiktok.com",
) : RecipeImporter {

    override val platform: Platform = Platform.TIKTOK

    override suspend fun fetch(rawUrl: String): RawImport {
        val encoded = URLEncoder.encode(rawUrl, "UTF-8")
        val url = "$oEmbedBase/oembed?url=$encoded"
        val request = Request.Builder().url(url).get().build()
        val body = client.executeSuspend(request) { response ->
            if (!response.isSuccessful) {
                throw ImportException("TikTok oEmbed returned HTTP ${response.code}", retryable = true)
            }
            response.body?.string().orEmpty()
        }
        val data = json.decodeFromString<TikTokOEmbed>(body)
        val caption = data.title?.trim().orEmpty().takeIf { it.isNotBlank() }
            ?: throw ImportException("No caption found — video may require login", retryable = true)
        return RawImport(
            platform = platform.key,
            url = rawUrl,
            author = data.authorUniqueId ?: data.authorName,
            content = ImportContent.RawText(text = caption, title = null),
            thumbnailUrl = data.thumbnailUrl,
        )
    }
}
