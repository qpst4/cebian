package com.slideindex.app.overlay;

import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** WebViewClient that handles renderer process termination without crashing the app. */
public class RenderProcessSafeWebViewClient extends WebViewClient {
    @Override
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        return true;
    }
}
