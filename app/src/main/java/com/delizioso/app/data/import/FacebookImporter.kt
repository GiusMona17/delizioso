package com.delizioso.app.data.import

/**
 * Facebook — loads the original post/reel URL in the WebView and reads the caption.
 *
 * Two reasons the legacy `plugins/video.php` embed is not used:
 *  1. It returns HTTP 400 for reel URLs and for `/share/` redirect links.
 *  2. `/share/r|v|p/<id>` links only work in a real browser context — the WebView
 *     follows the redirect to the actual post page, where `.Caption`/article text
 *     is rendered. Plain HTTP (OkHttp) gets 400 from these endpoints.
 * Best-effort: a login wall yields a blank caption and a retryable error.
 */
class FacebookImporter(
    private val extractor: WebViewCaptionExtractor,
) : RecipeImporter {

    override val platform: Platform = Platform.FACEBOOK

    override suspend fun fetch(rawUrl: String): RawImport {
        val extracted = extractor.extract(rawUrl)
        return RawImport(
            platform = platform.key,
            url = rawUrl,
            author = null,
            content = ImportContent.RawText(text = extracted.caption, title = extracted.title),
            thumbnailUrl = extracted.imageUrl,
        )
    }
}
