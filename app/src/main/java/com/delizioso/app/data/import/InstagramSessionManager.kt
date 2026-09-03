package com.delizioso.app.data.import

import android.webkit.CookieManager

/**
 * Checks and clears the Instagram session stored in the system [CookieManager].
 *
 * No DataStore entry is needed: Android's CookieManager is backed by the same
 * on-disk store that the system WebView uses, so cookies survive process restarts
 * and are shared by every WebView in the app automatically.
 */
object InstagramSessionManager {

    private const val INSTAGRAM_URL = "https://www.instagram.com"

    /**
     * Mimics the `window.chrome` object that Chrome browser exposes but
     * Android WebView does not. Its absence is a reliable WebView signal
     * that Instagram (and other Meta properties) check to decide whether
     * to serve a login wall instead of the public embed or block React boot.
     */
    const val CHROME_STUB_JS = """
        (function() {
          if (window.chrome) return;
          window.chrome = {
            app: { isInstalled: false },
            runtime: {
              connect: function() {},
              sendMessage: function() {},
              onMessage: { addListener: function() {} }
            },
            loadTimes: function() { return {}; },
            csi: function() { return {}; }
          };
        })();
    """

    /**
     * True when a `sessionid` cookie exists for instagram.com, which means the
     * user previously completed the login flow and the session is (likely) still
     * valid. We do not verify liveness against the API — a stale session will
     * simply cause Pass 2 to hit the wall again, and the Import screen will
     * surface the auth card once more.
     */
    fun isLoggedIn(): Boolean {
        val cookies = CookieManager.getInstance().getCookie(INSTAGRAM_URL) ?: return false
        return cookies.split(";").any { it.trim().startsWith("sessionid=") }
    }

    /**
     * Removes all cookies for instagram.com, effectively logging the user out
     * of the extraction WebView. Does not affect any other domain.
     */
    fun clearSession() {
        val cm = CookieManager.getInstance()
        val cookies = cm.getCookie(INSTAGRAM_URL) ?: return
        // Delete each cookie by name, setting Max-Age=0 for the instagram.com domain.
        cookies.split(";").forEach { pair ->
            val name = pair.trim().substringBefore("=")
            if (name.isNotBlank()) {
                cm.setCookie(INSTAGRAM_URL, "$name=; Max-Age=0; Path=/; Domain=.instagram.com")
            }
        }
        cm.flush()
    }
}
