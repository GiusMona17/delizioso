package com.delizioso.app.data.import

import com.delizioso.app.data.local.IngredientEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** TikTok oEmbed response (title = caption). */
@Serializable
data class TikTokOEmbed(
    val title: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("author_unique_id") val authorUniqueId: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
)

/** YouTube Data API v3 `videos.list?part=snippet` response. */
@Serializable
data class YouTubeApiResponse(val items: List<YouTubeItem> = emptyList())

@Serializable
data class YouTubeItem(val snippet: YouTubeSnippet? = null)

@Serializable
data class YouTubeSnippet(
    val title: String? = null,
    val description: String? = null,
    @SerialName("channelTitle") val channel: String? = null,
    val thumbnails: YouTubeThumbnails? = null,
)

/** Thumbnail set from the Data API; `maxres` is absent for most videos. */
@Serializable
data class YouTubeThumbnails(
    val maxres: YouTubeThumbnail? = null,
    val standard: YouTubeThumbnail? = null,
    val high: YouTubeThumbnail? = null,
    val medium: YouTubeThumbnail? = null,
    val default: YouTubeThumbnail? = null,
) {
    /** Biggest available, so the hero image still looks sharp. */
    fun best(): String? =
        (maxres ?: standard ?: high ?: medium ?: default)?.url
}

@Serializable
data class YouTubeThumbnail(val url: String? = null)

/** Raw content fetched from a source — either plain text or already-structured. */
sealed interface ImportContent {
    /** Free text (caption/description/page text) that still needs LLM structuring. */
    data class RawText(val text: String, val title: String? = null) : ImportContent

    /** Already structured (e.g. schema.org Recipe JSON-LD). */
    data class Structured(val recipe: StructuredRecipe) : ImportContent
}

/** Everything a source fetch produced, before LLM structuring / preview. */
data class RawImport(
    val platform: String,
    val url: String?,
    val author: String?,
    val content: ImportContent,
    /** Cover image advertised by the source (oEmbed / API / og:image / embed page). */
    val thumbnailUrl: String? = null,
)

/** A structured recipe as extracted from a page or LLM (not yet persisted). */
data class StructuredRecipe(
    val title: String? = null,
    val description: String? = null,
    val servings: Int? = null,
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    val imageUrl: String? = null,
    val ingredients: List<IngredientEntity> = emptyList(),
    val steps: List<String> = emptyList(),
    /** Already validated against [com.delizioso.app.data.Categories]. */
    val categories: List<String> = emptyList(),
) {
    /** Plain-text rendering (used as fallback input for LLM structuring). */
    fun toPlainText(): String = buildString {
        title?.let { appendLine("Title: $it") }
        description?.let { appendLine("Description: $it") }
        servings?.let { appendLine("Servings: $it") }
        if (ingredients.isNotEmpty()) {
            appendLine("Ingredients:")
            ingredients.forEach { appendLine("- ${it.rawText ?: listOfNotNull(it.quantity, it.unit, it.name).joinToString(" ")}") }
        }
        if (steps.isNotEmpty()) {
            appendLine("Instructions:")
            steps.forEachIndexed { i, s -> appendLine("${i + 1}. $s") }
        }
    }
}

class ImportException(
    message: String,
    /** True if retrying later (or on-device/WebView) may help. */
    val retryable: Boolean = false,
) : Exception(message)
