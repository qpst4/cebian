package com.slideindex.app.search

import android.content.Context
import android.webkit.WebSettings
import java.net.URLEncoder

object ImageSearchUrlBuilder {
    fun build(engine: ImageSearchEngine, hostedImageUrl: String): String {
        val encoded = URLEncoder.encode(hostedImageUrl, Charsets.UTF_8.name())
        return when (engine) {
            ImageSearchEngine.Google ->
                "https://lens.google.com/uploadbyurl?url=$encoded"
            ImageSearchEngine.Yandex ->
                "https://yandex.com/images/search?rpt=imageview&url=$encoded"
            ImageSearchEngine.TinEye -> "https://tineye.com/search?url=$encoded"
        }
    }

    /** System default mobile Chrome UA for in-panel WebView engines. */
    fun userAgent(context: Context, engine: ImageSearchEngine): String {
        return if (engine == ImageSearchEngine.Google) {
            // Hardcoded Mobile Chrome UA (without '; wv').
            // By NOT having '; wv', we bypass Catbox's WAF block so the image thumbnail loads.
            // By natively stripping X-Requested-With (in FloatBallImageSearchPanel), we perfectly mimic a mobile Chrome browser.
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        } else {
            WebSettings.getDefaultUserAgent(context)
        }
    }
}
