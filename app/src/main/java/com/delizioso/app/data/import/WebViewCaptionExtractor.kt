package com.delizioso.app.data.import

import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONTokener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Renders a social embed page in a (hidden) WebView and extracts the caption text.
 *
 * Spike finding: Instagram's `/embed/captioned/` page HTTP-200s but injects the
 * caption via JavaScript — plain HTTP cannot see it. A WebView (real browser TLS,
 * device IP, user's cookies) is the resilient no-login path. Runs on the main thread
 * because WebView requires it.
 */
class WebViewCaptionExtractor(
    private val context: Context,
) {

    suspend fun extract(url: String, timeoutMillis: Long = 30_000L): String =
        withContext(Dispatchers.Main) {
            withTimeout(timeoutMillis) {
                suspendCancellableCoroutine { cont ->
                    val webView = WebView(context.applicationContext)
                    webView.settings.javaScriptEnabled = true
                    webView.settings.domStorageEnabled = true
                    webView.settings.loadWithOverviewMode = true
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, pageUrl: String?) {
                            view?.evaluateJavascript(CAPTION_JS) { result ->
                                val caption = unescapeJsString(result).trim()
                                if (caption.isNotBlank()) {
                                    if (!cont.isCompleted) cont.resume(caption)
                                } else if (!cont.isCompleted) {
                                    cont.resumeWithException(ImportException("Caption did not render (page may be blocked)", retryable = true))
                                }
                                webView.destroy()
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            if (!cont.isCompleted) {
                                cont.resumeWithException(ImportException("WebView error ${error?.errorCode}", retryable = true))
                            }
                            webView.destroy()
                        }
                    }
                    cont.invokeOnCancellation { webView.destroy() }
                    webView.loadUrl(url)
                }
            }
        }

    private companion object {
        /** Prefer the caption element; fall back to the article, then the whole body. */
        const val CAPTION_JS = """
            (function() {
              var cap = document.querySelector('.Caption')
                   || document.querySelector('[class*="Caption"]')
                   || document.querySelector('article')
                   || document.body;
              return cap ? cap.innerText : '';
            })()
        """

        fun unescapeJsString(jsResult: String): String {
            val trimmed = jsResult.trim()
            if (trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"')) {
                return runCatching { JSONTokener(trimmed).nextValue() as String }
                    .getOrDefault(trimmed)
            }
            return trimmed
        }
    }
}
