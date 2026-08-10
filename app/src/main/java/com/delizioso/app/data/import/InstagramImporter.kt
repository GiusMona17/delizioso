package com.delizioso.app.data.import

/**
 * Instagram — WebView-only caption extraction from the public embed page
 * `/p/{code}/embed/captioned/` (caption is JS-injected; see SPIKE.md).
 * Not available via any official API (Meta prohibits caption extraction).
 */
class InstagramImporter(
    private val extractor: WebViewCaptionExtractor,
) : RecipeImporter {

    override val platform: Platform = Platform.INSTAGRAM

    override suspend fun fetch(rawUrl: String): RawImport {
        val code = PlatformDetector.instagramCode(rawUrl)
            ?: throw ImportException("Not a valid Instagram link")
        val embedUrl = "https://www.instagram.com/p/$code/embed/captioned/"
        val caption = extractor.extract(embedUrl)
        return RawImport(
            platform = platform.key,
            url = rawUrl,
            author = null,
            content = ImportContent.RawText(text = caption),
        )
    }
}
