package com.delizioso.app.ui.screens.profile

import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.delizioso.app.R
import com.delizioso.app.data.import.InstagramSessionManager
import com.delizioso.app.ui.components.ClayTopBar
import kotlinx.coroutines.delay

/**
 * Full-screen WebView that opens instagram.com/accounts/login/ so the user can
 * log in with their own credentials. Once the WebView navigates away from the
 * `/accounts/` path (i.e. Instagram redirects to the home feed or profile),
 * the session cookies have been written to [CookieManager] and [onLoginSuccess] is called.
 */
@Composable
fun InstagramLoginScreen(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            if (InstagramSessionManager.isLoggedIn()) {
                android.util.Log.d("InstagramLogin", "session detected via polling, calling onLoginSuccess")
                onLoginSuccess()
                break
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ClayTopBar(
            title = stringResource(R.string.instagram_login_title),
            onMenu = {
                if (InstagramSessionManager.isLoggedIn()) {
                    android.util.Log.d("InstagramLogin", "session detected on back, calling onLoginSuccess")
                    onLoginSuccess()
                } else {
                    onBack()
                }
            },
            menuIcon = Icons.AutoMirrored.Filled.ArrowBack,
            menuDescription = stringResource(R.string.topbar_back),
        )
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )

                    val webViewInstance = this
                    // Enable cookies including cross-origin (essential for Instagram auth frames)
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(webViewInstance, true)
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        javaScriptCanOpenWindowsAutomatically = true

                        // Strip "wv" and "Version/4.0" so Instagram doesn't detect it as a restricted in-app browser
                        val rawUa = userAgentString ?: ""
                        userAgentString = rawUa
                            .replace(Regex("""\s*Version/\d+\.\d+"""), "")
                            .replace(Regex("""\s*Build/[^;)]+"""), "")
                            .replace(Regex("""\s*wv\s*"""), " ")
                            .replace(Regex("""\s+"""), " ")
                            .trim()
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                            android.util.Log.d(
                                "InstagramLogin",
                                "console: [${message?.messageLevel()}] ${message?.message()} (${message?.sourceId()}:${message?.lineNumber()})"
                            )
                            return super.onConsoleMessage(message)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val scheme = request?.url?.scheme
                            // Keep http and https inside the webview; block intent:// and instagram:// app deep links
                            return scheme != null && scheme != "http" && scheme != "https"
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            android.util.Log.d("InstagramLogin", "onPageStarted url=$url")
                            view?.evaluateJavascript(InstagramSessionManager.CHROME_STUB_JS, null)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            android.util.Log.d("InstagramLogin", "onPageFinished url=$url")
                            if (url != null &&
                                !url.contains("/accounts/login") &&
                                !url.contains("/accounts/emailsignup") &&
                                InstagramSessionManager.isLoggedIn()
                            ) {
                                android.util.Log.d("InstagramLogin", "login success detected, url=$url")
                                onLoginSuccess()
                            }
                        }
                    }

                    loadUrl("https://www.instagram.com/accounts/login/")
                }
            },
        )
    }
}
