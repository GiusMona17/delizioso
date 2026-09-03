package com.delizioso.app.data.import

/**
 * Instagram — WebView-only caption extraction from the public embed page
 * `/p/{code}/embed/captioned/` (caption is JS-injected; see SPIKE.md).
 * Not available via any official API (Meta prohibits caption extraction).
 *
 * Two-pass strategy:
 *   Pass 1 — always unauthenticated (cookies blocked). Covers all public posts.
 *   Pass 2 — authenticated (saved session cookies), only when Pass 1 hits a
 *             login wall AND [InstagramSessionManager.isLoggedIn] is true.
 *
 * Credentials are never sent to posts that do not need them, and are never
 * deleted just because a post is public.
 */
class InstagramImporter(
    private val extractor: WebViewCaptionExtractor,
) : RecipeImporter {

    override val platform: Platform = Platform.INSTAGRAM

    override suspend fun fetch(rawUrl: String): RawImport {
        val code = PlatformDetector.instagramCode(rawUrl)
            ?: throw ImportException("Not a valid Instagram link")
        val embedUrl = "https://www.instagram.com/p/$code/embed/captioned/"

        // Pass 1 — unauthenticated (cookies disabled for instagram.com).
        val first = extractor.extract(embedUrl, useSession = false)
        if (!first.wallDetected) return first.toRawImport(rawUrl)

        // Wall hit. Retry with session only if one is saved.
        // We load the actual post URL (rawUrl) because Instagram delivers full post metadata
        // to logged-in sessions on the post/reel page, whereas /embed/captioned/ does not.
        if (InstagramSessionManager.isLoggedIn()) {
            val second = extractor.extract(rawUrl, useSession = true)
            return second.toRawImport(rawUrl)
        }

        // No session available — let the UI offer the Connect flow.
        throw InstagramAuthRequiredException()
    }

    private fun WebViewCaptionExtractor.Extracted.toRawImport(rawUrl: String) = RawImport(
        platform = platform.key,
        url = rawUrl,
        author = author,
        content = ImportContent.RawText(text = caption, title = title),
        thumbnailUrl = imageUrl,
    )
}
