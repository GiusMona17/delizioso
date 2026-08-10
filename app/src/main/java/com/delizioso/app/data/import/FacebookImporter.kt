package com.delizioso.app.data.import

import java.net.URLEncoder

/**
 * Facebook — best-effort via the official plugin/video embed (renders the post
 * including its caption). Lower confidence than Instagram; graceful degradation.
 */
class FacebookImporter(
    private val extractor: WebViewCaptionExtractor,
) : RecipeImporter {

    override val platform: Platform = Platform.FACEBOOK

    override suspend fun fetch(rawUrl: String): RawImport {
        val embedUrl = "https://www.facebook.com/plugins/video.php?href=" +
            URLEncoder.encode(rawUrl, "UTF-8")
        val caption = extractor.extract(embedUrl)
        return RawImport(
            platform = platform.key,
            url = rawUrl,
            author = null,
            content = ImportContent.RawText(text = caption),
        )
    }
}
