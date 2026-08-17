package com.amazontracker.util

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object TrackerBlocker {

    private val blockedDomains = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "facebook.com/tr",
        "facebook.net",
        "analytics.twitter.com",
        "clarity.ms",
        "bat.bing.com",
        "hotjar.com",
        "mixpanel.com",
        "appsflyer.com",
        "adjust.com",
        "branch.io",
        "app-measurement.com"
    )

    fun shouldBlockRequest(request: WebResourceRequest): Boolean {
        if (request.isForMainFrame) return false
        val url = request.url.toString().lowercase()
        return blockedDomains.any { url.contains(it) }
    }

    fun getBlockingResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
    }

    fun getAntiDetectionJavaScript(): String = """
        (function() {
            try {
                Object.defineProperty(navigator, 'webdriver', {
                    get: function() { return false; },
                    configurable: true
                });
                delete navigator.__proto__.webdriver;
                if (!window.chrome) { window.chrome = {}; }
                if (!window.chrome.runtime) { window.chrome.runtime = {}; }
            } catch(e) {}
        })();
    """.trimIndent()

    fun getAppBannerRemovalJS(): String = """
        (function() {
            try {
                var sels = [
                    '#apb-begging-banner','#downloadAppBanner','.app-banner',
                    '#nav-mobile-app-banner','.nav-mobile-app-banner','#appBannerLink',
                    '[class*="appBanner"]','[id*="appBanner"]','#mobile-apps-banner',
                    '#bottomBanner','#nav-subnav-toaster',
                    '#navSwmHello',                    '[data-action="a-popover-smartappbanner"]',
                    '[data-cel-widget="sp_detailWHUB"]',
                    '.swm-desktop-wrapper','#swm-hellobar',
                    '#mw-notification-bar','.applicable-promotion-text'
                ];
                sels.forEach(function(sel) {
                    document.querySelectorAll(sel).forEach(function(el) { el.remove(); });
                });
                document.querySelectorAll('div,section,span,a,button,p').forEach(function(el) {
                    var t = (el.textContent||'').toLowerCase();
                    var s = window.getComputedStyle(el);
                    if (s.position === 'fixed' || s.position === 'sticky') {
                        if (s.top === '0px' || parseInt(s.top) <= 0) {
                            if (t.includes('app') || t.includes('download') || t.includes('open')) {
                                el.style.display = 'none';
                            }
                        }
                    }
                });
                document.querySelectorAll('div,section,span,a,button').forEach(function(el) {
                    var t = (el.textContent||'').toLowerCase();
                    if ((t.includes('open the app')||t.includes('continue in the app')||
                         t.includes('view in app')||t.includes('download the app')||
                         t.includes('get the app')||t.includes('swipe up')||
                         t.includes('apri l\\'app')||t.includes('continua nell\\'app')||
                         t.includes('scarica l\\'app')) && el.offsetHeight > 0 && el.offsetHeight < 200)
                        el.style.display = 'none';
                });
            } catch(e) {}
        })();
    """.trimIndent()
}
