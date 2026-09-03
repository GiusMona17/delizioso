package com.delizioso.app.data.import

import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONTokener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Substrings that mark a login/consent wall instead of real recipe content. */
object LoginWall {
    private val MARKERS = listOf(
        "apri app", "accedi", "log in", "open app", "sign up", "sign in",
        "guarda cosa ha inviat", "see what", "create new account",
        "you must log in", "devi accedere", "ottieni l'esperienza",
        "cookie consent", "consent to", "gestisci opzioni",
        "è possibile che il",   // Italian Instagram login wall
        "it's possible that",   // English equivalent
    )

    fun matches(text: String): Boolean =
        MARKERS.any { text.contains(it, ignoreCase = true) }
}

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

    /** Caption plus the embed's cover image, when the page renders one. */
    data class Extracted(
        val caption: String,
        val imageUrl: String?,
        /** Page-level og:title (useful when the caption is behind a login wall). */
        val title: String? = null,
        /** The account that posted it, when the embed names one. */
        val author: String? = null,
        /** True when only a login/consent wall rendered — no recipe caption. */
        val wallDetected: Boolean = false,
    )

    suspend fun extract(url: String, timeoutMillis: Long = 60_000L, useSession: Boolean = false): Extracted =
        withContext(Dispatchers.Main) {
            val cm = android.webkit.CookieManager.getInstance()
            if (useSession) {
                cm.setAcceptCookie(true)
            } else {
                cm.setAcceptCookie(false)
            }
            try {
                withTimeout(timeoutMillis) {
                    suspendCancellableCoroutine { cont ->
                        val webView = WebView(context.applicationContext)
                        if (useSession) {
                            cm.setAcceptThirdPartyCookies(webView, true)
                        }
                        var reloads = 0
                        var consentClicks = 0
                        webView.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true

                            val rawUa = userAgentString ?: ""
                            userAgentString = rawUa
                                .replace(Regex("""\s*Version/\d+\.\d+"""), "")
                                .replace(Regex("""\s*Build/[^;)]+"""), "")
                                .replace(Regex("""\s*wv\s*"""), " ")
                                .replace(Regex("""\s+"""), " ")
                                .trim()
                        }
                        webView.webViewClient = object : WebViewClient() {
                        /**
                         * Social pages (Facebook especially) deep-link to their app via
                         * `fb://…` / `intent://…` once the document is up. In a WebView an
                         * unsupported scheme aborts the MAIN frame with
                         * ERROR_UNSUPPORTED_SCHEME (-10) — swallowing the navigation keeps
                         * the rendered page alive so the caption can be extracted.
                         */
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val scheme = request?.url?.scheme
                            return scheme != null && scheme != "http" && scheme != "https"
                        }

                        /**
                         * Inject `window.chrome` before any page script runs.
                         * Its absence is one of the JS signals Instagram uses to
                         * detect an embedded WebView and serve a login wall.
                         */
                        override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: android.graphics.Bitmap?) {
                            view?.evaluateJavascript(CHROME_STUB_JS, null)
                        }

                        override fun onPageFinished(view: WebView?, pageUrl: String?) {
                            android.util.Log.d("WebViewExtract", "onPageFinished url=$pageUrl")
                            view?.evaluateJavascript(CONSENT_JS) { consentResult ->
                                val consent = unescapeJsString(consentResult).trim()
                                if (consent.startsWith("CONSENT")) {
                                    android.util.Log.d("WebViewExtract", "consent accepted ($consent), waiting for reload")
                                    if (consentClicks >= MAX_CONSENT_CLICKS) {
                                        android.util.Log.d("WebViewExtract", "consent click cap reached — giving up on the wall")
                                        if (!cont.isCompleted) {
                                            cont.resumeWithException(ImportException("The site's cookie consent could not be accepted", retryable = true))
                                        }
                                        webView.destroy()
                                        return@evaluateJavascript
                                    }
                                    consentClicks++
                                    return@evaluateJavascript
                                }
                                // Extract immediately — a delayed evaluateJavascript can be
                                // dropped if the SPA navigates in the meantime.
                                view?.evaluateJavascript(CAPTION_JS) { result ->
                                    val payload = unescapeJsString(result).trim()
                                    val parts = payload.split(FIELD_SEPARATOR)
                                    val caption = parts.getOrNull(0)?.trim() ?: ""
                                    val image = parts.getOrNull(1)?.trim()?.ifBlank { null }
                                    val imageSource = parts.getOrNull(2)?.trim()
                                    val title = parts.getOrNull(3)?.trim()?.ifBlank { null }
                                    val author = parts.getOrNull(4)?.trim()?.ifBlank { null }
                                    android.util.Log.d(
                                        "WebViewExtract",
                                        "page=$pageUrl caption=${caption.take(40)} image=${image ?: "NULL"} src=${imageSource ?: "-"} title=${title?.take(40) ?: "-"}"
                                    )
                                    val looksLikeWall = caption.isBlank() || LoginWall.matches(caption)
                                    if (looksLikeWall && reloads < MAX_RELOADS) {
                                        reloads++
                                        android.util.Log.d("WebViewExtract", "caption is a wall/login page — reloading ($reloads)")
                                        view?.reload()
                                        return@evaluateJavascript
                                    }
                                    if (caption.isNotBlank()) {
                                        // Wall text still counts as a (poor) caption; flag it so the
                                        // caller can fall back to title+thumbnail instead of AI parsing.
                                        if (!cont.isCompleted) {
                                            cont.resume(
                                                Extracted(caption, image, title, author, wallDetected = looksLikeWall)
                                            )
                                        }
                                    } else if (!cont.isCompleted) {
                                        cont.resumeWithException(ImportException("Caption did not render (page may be blocked)", retryable = true))
                                    }
                                    webView.destroy()
                                }
                            }
                        }
                        /**
                         * Only a failed MAIN-frame load is fatal. Since API 23 this
                         * callback also fires for subresources (e.g. an `fb://` deep-link
                         * asset, a blocked ad, a CORS'd image) — aborting on those would
                         * kill extraction of an otherwise perfectly rendered page.
                         */
                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            if (request?.isForMainFrame != true) return
                            if (!cont.isCompleted) {
                                cont.resumeWithException(ImportException("WebView error ${error?.errorCode}", retryable = true))
                            }
                            webView.destroy()
                        }

                        /** HTTP 400/404/5xx on the main frame (e.g. a blocked page). */
                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: WebResourceResponse?,
                        ) {
                            if (request?.isForMainFrame != true) return
                            if (!cont.isCompleted) {
                                cont.resumeWithException(
                                    ImportException("The site returned HTTP ${errorResponse?.statusCode}", retryable = true)
                                )
                            }
                            webView.destroy()
                        }
                    }
                    cont.invokeOnCancellation { webView.destroy() }
                    webView.loadUrl(url)
                }
            }
            } catch (e: TimeoutCancellationException) {
                throw ImportException("Timed out loading the page", retryable = true)
            } finally {
                // Always re-enable cookies, even on timeout or cancellation.
                if (!useSession) android.webkit.CookieManager.getInstance().setAcceptCookie(true)
            }
        }

    private companion object {
        /** Separates the caption from the image URL in the single JS return value. */
        const val FIELD_SEPARATOR = "\u001f"

        /** At most this many reloads when the page renders a login/consent wall. */
        const val MAX_RELOADS = 1

        /** At most this many consent-wall clicks before giving up. */
        const val MAX_CONSENT_CLICKS = 2

        const val CHROME_STUB_JS = InstagramSessionManager.CHROME_STUB_JS

        /**
         * Facebook (and some other sites) put a cookie-consent wall in front of the
         * page for fresh WebViews. Only acts when the page really is a consent wall
         * (body text mentions cookies), then clicks "accept all" (preferred) or the
         * only consent button and lets the page reload before extracting.
         * Returns "CONSENT" when a button was clicked, "NO" otherwise.
         */
        const val CONSENT_JS = """
            (function() {
              var bodyText = (document.body && document.body.innerText || '').toLowerCase();
              if (bodyText.indexOf('cookie') === -1) return 'NO';
              var nodes = document.querySelectorAll('button, a, [role="button"]');
              var found = null, foundText = '';
              for (var i = 0; i < nodes.length; i++) {
                var t = (nodes[i].innerText || nodes[i].getAttribute('aria-label') || '').trim().toLowerCase();
                if (t.length === 0 || t.length > 60) continue;
                if (t.indexOf('consent') !== -1 || t.indexOf('cookie') !== -1 ||
                    t.indexOf('accett') !== -1 || t.indexOf('allow') !== -1 ||
                    t.indexOf('acept') !== -1 || t.indexOf('tutti') !== -1) {
                  // Prefer the "accept all" variant over "essential only".
                  var isAll = t.indexOf('tutti') !== -1 || t.indexOf('all') !== -1 || t.indexOf('tout') !== -1;
                  if (isAll) { found = nodes[i]; foundText = t; break; }
                  if (found === null) { found = nodes[i]; foundText = t; }
                }
              }
              if (found) { found.click(); return 'CONSENT:' + foundText; }
              return 'NO';
            })()
        """

        /**
         * Caption: prefer a cleaned og:title (Facebook's og:title reads like
         * "12M views · 3K reactions | caption | author" — drop the leading stats
         * segment and trailing hashtags, same as the reference downloader's
         * nameFromTitle); fall back to the .Caption element, then article, then body.
         * Cover image: video poster, then og:image, then the largest <img>.
         */
        const val CAPTION_JS = """
            (function() {
              function cleanCaption(t) {
                t = (t || '').trim();
                if (!t) return '';
                var igMatch = t.match(/^(.+?)\s+(?:on|su|sur|en)\s+Instagram\s*:\s*["“]([\s\S]+?)["”]?$/i);
                if (igMatch) {
                  t = igMatch[2].trim();
                }
                var parts = t.split(' | ');
                if (parts.length > 1) {
                  var first = parts[0].toLowerCase();
                  if (first.indexOf('views') !== -1 || first.indexOf('reaction') !== -1) parts = parts.slice(1);
                }
                var caption = (parts[0] || '').trim();
                var i = caption.indexOf(' #');
                if (i > 0) caption = caption.substring(0, i).trim();
                return caption;
              }
              var ogt = document.querySelector('meta[property="og:title"]');
              var ogTitleValue = (ogt && ogt.content) ? ogt.content : '';
              var author = '';
              if (ogTitleValue) {
                var igAuthorMatch = ogTitleValue.match(/^(.+?)\s+(?:on|su|sur|en)\s+Instagram\s*:/i);
                if (igAuthorMatch) author = igAuthorMatch[1].trim();
              }
              // Instagram's /embed/captioned/ page carries the real caption in
              // .Caption, prefixed by the account name in .CaptionUsername, while
              // its og:title is only the account. Reading og:title first therefore
              // put the author's name where the dish name belongs.
              var text = '';
              var captionEl = document.querySelector('.Caption')
                           || document.querySelector('[class*="Caption"]');
              if (captionEl) {
                var userEl = captionEl.querySelector('.CaptionUsername')
                          || captionEl.querySelector('[class*="Username"]');
                if (userEl) author = (userEl.innerText || '').trim();
                // Strip from the LIVE node, never a clone: innerText derives its line
                // breaks from layout, and a detached clone has none — cloning
                // returned the whole recipe as a single unbroken line, which left
                // the heading parser nothing to split on. The page is thrown away
                // with the WebView moments later, so mutating it costs nothing.
                var strip = captionEl.querySelectorAll('.CaptionUsername, [class*="Username"], .CaptionComments, [class*="Comments"]');
                for (var s = 0; s < strip.length; s++) {
                  if (strip[s].parentNode) strip[s].parentNode.removeChild(strip[s]);
                }
                text = (captionEl.innerText || '').trim();
              }
              // Facebook and authenticated Instagram post pages have no .Caption: there the recipe lives in og:title.
              if (!text) text = cleanCaption(ogTitleValue);
              if (!text) {
                var ogd = document.querySelector('meta[property="og:description"]');
                var ogDescValue = (ogd && ogd.content) ? ogd.content : '';
                var dMatch = ogDescValue.match(/:\s*["“]([\s\S]+?)["”]?$/);
                if (dMatch) text = cleanCaption(dMatch[1]);
              }
              if (!text) {
                var cap = document.querySelector('article') || document.body;
                text = cap ? cap.innerText.trim() : '';
              }
              var best = null, bestArea = 0, src = null;
              // 0) Instagram clean cover frame: look for image_versions2 in page data
              // to get the pure high-res frame without the play button overlay.
              try {
                var html = document.documentElement ? document.documentElement.innerHTML : '';
                var m = html.match(/"image_versions2":\s*\{\s*"candidates":\s*\[\s*\{\s*"url":\s*"([^"]+)"/);
                if (m && m[1]) {
                  best = m[1].replace(/\\u0026/g, '&').replace(/\\u0025/g, '%').replace(/\\\//g, '/');
                  src = 'image_versions2';
                  bestArea = Number.MAX_SAFE_INTEGER;
                }
              } catch (e) {}
              // 1) The media cover: a <video poster> beats any avatar-style <img>.
              if (!best) {
                var video = document.querySelector('video');
                if (video && video.poster) { best = video.poster; src = 'video'; bestArea = Number.MAX_SAFE_INTEGER; }
              }
              // 2) The page-declared share thumbnail (og:image) — for reels this is
              //    the cover frame itself.
              if (!best) {
                var og = document.querySelector('meta[property="og:image"]')
                      || document.querySelector('meta[property="og:image:url"]')
                      || document.querySelector('meta[name="twitter:image"]');
                if (og && og.content) { best = og.content; src = 'og'; bestArea = Number.MAX_SAFE_INTEGER; }
              }
              // 3) Instagram's embed names its cover image, and the src attribute is
              //    in the markup before the file has loaded — the size-based scan
              //    below cannot see it yet at onPageFinished, which is why reels
              //    were importing without a photo.
              if (!best) {
                var media = document.querySelector('img.EmbeddedMediaImage')
                         || document.querySelector('img[class*="EmbeddedMedia"]');
                var mediaSrc = media ? (media.currentSrc || media.src || media.getAttribute('data-src') || '') : '';
                if (mediaSrc && mediaSrc.indexOf('data:') !== 0) {
                  best = mediaSrc; src = 'embed'; bestArea = Number.MAX_SAFE_INTEGER;
                }
              }
              // 4) Largest rendered <img> (avatars are small and round). Lazy images
              //    may not be loaded yet, so fall back to their layout size.
              if (!best) {
                var imgs = document.querySelectorAll('img');
                for (var i = 0; i < imgs.length; i++) {
                  var el = imgs[i];
                  var u = el.currentSrc || el.src || el.getAttribute('data-src') || '';
                  var w = el.naturalWidth || el.width || 0;
                  var h = el.naturalHeight || el.height || 0;
                  var area = w * h;
                  if (area === 0) {
                    var r = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
                    if (r && r.width > 0 && r.height > 0) area = r.width * r.height;
                  }
                  if (area > bestArea && area > 40000 && u && u.indexOf('data:') !== 0) {
                    best = u; bestArea = area; src = 'img';
                  }
                }
              }
              var t = ogTitleValue || null;
              return text + '' + (best || '') + '' + (src || '') + '' + (t || '') + '' + (author || '');
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
