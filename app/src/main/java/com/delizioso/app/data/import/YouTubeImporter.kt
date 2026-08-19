package com.delizioso.app.data.import

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * YouTube — Data API v3 `videos.list?part=snippet` for the full description
 * (oEmbed has no description, verified in the spike). 1 unit/call, default 10k/day.
 */
class YouTubeImporter(
    private val apiKeyProvider: suspend () -> String,
    private val client: OkHttpClient = ImportHttp.client,
    private val json: Json = Json { ignoreUnknownKeys = true },
    /** Injectable for tests (MockWebServer); production default is the YouTube Data API endpoint. */
    private val apiBaseUrl: String = "https://www.googleapis.com/youtube/v3/videos",
) : RecipeImporter {

    override val platform: Platform = Platform.YOUTUBE

    override suspend fun fetch(rawUrl: String): RawImport {
        val videoId = PlatformDetector.youtubeId(rawUrl)
            ?: throw ImportException("Not a valid YouTube link")
        val apiKey = apiKeyProvider()
        if (apiKey.isBlank()) {
            throw ImportException("YouTube API key is not configured (add one in Settings)", retryable = false)
        }
        val url = "$apiBaseUrl?part=snippet&id=$videoId&key=$apiKey"
        val request = Request.Builder().url(url).get().build()
        val body = client.executeSuspend(request) { response ->
            val content = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ImportException("YouTube API error ${response.code}: ${content.take(200)}", retryable = true)
            }
            content
        }
        val snippet = json.decodeFromString<YouTubeApiResponse>(body).items.firstOrNull()?.snippet
            ?: throw ImportException("Video not found or unavailable", retryable = true)
        val description = snippet.description?.trim().orEmpty().takeIf { it.isNotBlank() }
            ?: throw ImportException("This video has no description text", retryable = false)
        return RawImport(
            platform = platform.key,
            url = rawUrl,
            author = snippet.channel,
            content = ImportContent.RawText(text = description, title = snippet.title),
            thumbnailUrl = snippet.thumbnails?.best(),
        )
    }
}
