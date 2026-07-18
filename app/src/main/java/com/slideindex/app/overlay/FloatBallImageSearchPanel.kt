package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.search.ImageHostUploader
import com.slideindex.app.search.ImageSearchEngine
import com.slideindex.app.search.ImageSearchUrlBuilder
import com.slideindex.app.overlay.pickresult.PickResultPanelMaxWidth
import com.slideindex.app.overlay.pickresult.pickResultPanelCard
import com.slideindex.app.overlay.pickresult.pickResultWindowHeightDp
import com.slideindex.app.ui.theme.SlideIndexTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val PANEL_HORIZONTAL_PADDING = 12.dp
private val PANEL_MAX_HEIGHT_FRACTION = 0.78f
private const val WEBVIEW_LOG_TAG = "FloatBallImageSearchPanel"
private const val PRELOAD_STAGGER_MS = 300L
private val tagLoadGeneration: Int = "image_search_load_gen".hashCode()
private val tagExpectedEngine: Int = "image_search_expected_engine".hashCode()
private val tagLoadedKey: Int = "image_search_loaded_key".hashCode()

private enum class ImageSearchPanelPhase {
    UPLOADING,
    READY,
    ERROR,
}

/**
 * Independent image-search overlay with one WebView per engine, preloaded in the background.
 */
object FloatBallImageSearchPanel {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val webViewsToDestroy = mutableListOf<WebView>()
    private val releasedWebViews = mutableSetOf<WebView>()

    internal fun releaseSearchWebView(webView: WebView) {
        if (!releasedWebViews.add(webView)) return
        webViewsToDestroy.remove(webView)
        safeReleaseWebView(webView)
    }

    private val destroyedWebViewTag: Int = "image_search_destroyed".hashCode()

    private fun safeReleaseWebView(webView: WebView) {
        if (webView.getTag(destroyedWebViewTag) == true) return
        webView.setTag(destroyedWebViewTag, true)
        runCatching { (webView.parent as? ViewGroup)?.removeView(webView) }
        runCatching {
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
    }

    private fun destroyWebViews() {
        webViewsToDestroy.toList().forEach(::releaseSearchWebView)
        webViewsToDestroy.clear()
    }

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var owner: OverlayComposeOwner? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var bitmapState: MutableState<Bitmap?>? = null
    private var ownedBitmap: Bitmap? = null
    private var phaseState: MutableState<ImageSearchPanelPhase>? = null
    private var retryTokenState: MutableState<Int>? = null

    private var cachedSourceBitmap: Bitmap? = null
    private var cachedHostedUrl: String? = null

    internal val panelVisible = mutableStateOf(false)
    val isShowing: Boolean get() = composeView != null

    fun show(context: Context, bitmap: Bitmap) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(context, bitmap) }
            return
        }
        if (FloatBallTranslatePanel.isShowing) {
            FloatBallTranslatePanel.dismiss()
        }
        
        if (cachedSourceBitmap !== bitmap) {
            cachedSourceBitmap = bitmap
            cachedHostedUrl = null
        }

        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context.applicationContext
        ensureWindow(hostContext)
        ownedBitmap?.recycle()
        val copied = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        ownedBitmap = copied
        bitmapState?.value = copied
        phaseState?.value = ImageSearchPanelPhase.UPLOADING
        retryTokenState?.value = (retryTokenState?.value ?: 0) + 1
        updateWindowFocusable(focusable = false)
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        destroyWebViews()
        ownedBitmap?.recycle()
        ownedBitmap = null
        val view = composeView
        val wm = windowManager
        composeView = null
        if (view != null && wm != null) {
            runCatching { wm.removeView(view) }
        }
        destroyWebViews()
        screenOffReceiver?.let { receiver ->
            appContext?.let { ctx -> runCatching { ctx.unregisterReceiver(receiver) } }
        }
        val currentOwner = owner
        if (currentOwner != null) {
            view?.post { currentOwner.destroy() } ?: currentOwner.destroy()
        }
        owner = null
        composeView = null
        layoutParams = null
        windowManager = null
        bitmapState = null
        phaseState = null
        retryTokenState = null
        panelVisible.value = false
        screenOffReceiver = null
        appContext = null
    }

    private fun updateWindowFocusable(focusable: Boolean) {
        val wm = windowManager ?: return
        val view = composeView ?: return
        val params = layoutParams ?: return
        params.flags = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun ensureWindow(context: Context) {
        if (composeView != null) return

        val bitmapHolder = mutableStateOf<Bitmap?>(null)
        val phaseHolder = mutableStateOf(ImageSearchPanelPhase.UPLOADING)
        val retryTokenHolder = mutableStateOf(0)
        bitmapState = bitmapHolder
        phaseState = phaseHolder
        retryTokenState = retryTokenHolder

        val cachedUrlHolder = mutableStateOf(cachedHostedUrl)
        val dialogOwner = OverlayComposeOwner()
        val overlayContext = OverlayCompose.themedContext(context)
        val compose = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            setContent {
                val bitmap by bitmapHolder
                val phase by phaseHolder
                val retryToken by retryTokenHolder
                val cachedUrl by cachedUrlHolder
                FloatBallImageSearchPanelContent(
                    webViewContext = context,
                    bitmap = bitmap,
                    phase = phase,
                    retryToken = retryToken,
                    cachedHostedUrl = cachedUrl,
                    onUrlUploaded = { url ->
                        cachedUrlHolder.value = url
                        cachedHostedUrl = url
                    },
                    onDismiss = { dismiss() },
                    onUploadFailed = {
                        phaseHolder.value = ImageSearchPanelPhase.ERROR
                        updateWindowFocusable(focusable = false)
                    },
                    onUploadSuccess = {
                        phaseHolder.value = ImageSearchPanelPhase.READY
                        updateWindowFocusable(focusable = true)
                    },
                    onRetry = {
                        phaseHolder.value = ImageSearchPanelPhase.UPLOADING
                        retryTokenHolder.value = retryTokenHolder.value + 1
                    },
                    onRegisterWebView = { webView ->
                        if (!webViewsToDestroy.contains(webView)) {
                            webViewsToDestroy.add(webView)
                        }
                    },
                    onOpenExternal = { url ->
                        runCatching {
                            overlayContext.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }.onFailure {
                            Toast.makeText(
                                overlayContext,
                                R.string.float_ball_action_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: run {
                dialogOwner.destroy()
                return
            }
        val params = buildLayoutParams(context)
        val added = runCatching { wm.addView(compose, params) }.isSuccess
        if (!added) {
            dialogOwner.destroy()
            Log.e(WEBVIEW_LOG_TAG, "failed to add image search panel")
            return
        }
        windowManager = wm
        composeView = compose
        owner = dialogOwner
        layoutParams = params
        appContext = context
        panelVisible.value = true
        registerScreenOffReceiver(context)
    }

    private fun buildLayoutParams(context: Context): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun registerScreenOffReceiver(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) dismiss()
            }
        }
        screenOffReceiver = receiver
        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
    }
}

@Composable
private fun FloatBallImageSearchPanelContent(
    webViewContext: Context,
    bitmap: Bitmap?,
    phase: ImageSearchPanelPhase,
    retryToken: Int,
    cachedHostedUrl: String?,
    onUrlUploaded: (String) -> Unit,
    onDismiss: () -> Unit,
    onUploadFailed: () -> Unit,
    onUploadSuccess: () -> Unit,
    onRetry: () -> Unit,
    onRegisterWebView: (WebView) -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val engines = remember { ImageSearchEngine.entries }
    var selectedEngine by remember { mutableStateOf(engines.first()) }
    val preloadedUrls = remember { mutableStateMapOf<ImageSearchEngine, String>() }
    val engineWebViews = remember { mutableStateMapOf<ImageSearchEngine, WebView>() }
    val loadingByEngine = remember { mutableStateMapOf<ImageSearchEngine, Boolean>() }
    var mountedEngines by remember { mutableStateOf(setOf<ImageSearchEngine>()) }
    var webViewSession by remember { mutableIntStateOf(0) }
    var googleFirstLoadDone by remember { mutableStateOf(false) }
    var isPageLoading by remember { mutableStateOf(false) }
    val maxPanelHeight = pickResultWindowHeightDp(PANEL_MAX_HEIGHT_FRACTION)
    val dismissInteraction = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(bitmap, retryToken) {
        val source = bitmap ?: return@LaunchedEffect
        webViewSession++
        mountedEngines = emptySet()
        engineWebViews.clear()
        loadingByEngine.clear()
        preloadedUrls.clear()
        googleFirstLoadDone = false
        isPageLoading = false

        val hostedUrl = cachedHostedUrl ?: ImageHostUploader.upload(source)
        if (hostedUrl.isNullOrBlank()) {
            onUploadFailed()
            return@LaunchedEffect
        }
        if (cachedHostedUrl == null) {
            onUrlUploaded(hostedUrl)
        }
        engines.forEach { engine ->
            preloadedUrls[engine] = ImageSearchUrlBuilder.build(engine, hostedUrl)
        }
        Log.d(
            WEBVIEW_LOG_TAG,
            "google uploadbyurl url=${preloadedUrls[ImageSearchEngine.Google]}",
        )
        onUploadSuccess()
    }

    LaunchedEffect(phase, webViewSession, preloadedUrls.size) {
        if (phase != ImageSearchPanelPhase.READY || preloadedUrls.size != engines.size) return@LaunchedEffect
        val session = webViewSession
        mountedEngines = setOf(ImageSearchEngine.Google)

        var sessionValid = true
        withTimeoutOrNull(20_000) {
            while (!googleFirstLoadDone) {
                delay(100)
                if (session != webViewSession) {
                    sessionValid = false
                    return@withTimeoutOrNull
                }
            }
        }
        if (!sessionValid || session != webViewSession) return@LaunchedEffect

        val otherEngines = engines.filter { it != ImageSearchEngine.Google }
        otherEngines.forEachIndexed { index, engine ->
            if (index > 0) delay(PRELOAD_STAGGER_MS)
            if (session != webViewSession) return@LaunchedEffect
            mountedEngines = mountedEngines + engine
        }
    }

    LaunchedEffect(selectedEngine, engineWebViews.size) {
        isPageLoading = loadingByEngine[selectedEngine] == true
        engineWebViews.forEach { (engine, webView) ->
            if (engine == selectedEngine) {
                webView.onResume()
            } else if (loadingByEngine[engine] != true) {
                webView.onPause()
            }
        }
    }

    mountedEngines.forEach { engine ->
        val webView = engineWebViews[engine]
        LaunchedEffect(engine, webView, preloadedUrls[engine], phase, webViewSession) {
            if (phase != ImageSearchPanelPhase.READY) return@LaunchedEffect
            val view = engineWebViews[engine] ?: return@LaunchedEffect
            val url = preloadedUrls[engine] ?: return@LaunchedEffect
            val expectedKey = "$webViewSession|${engine.name}|$url"
            if (view.getTag(tagLoadedKey) == expectedKey) return@LaunchedEffect
            view.setTag(tagLoadedKey, expectedKey)
            view.loadSearchUrlAfterLayout(
                url = url,
                engine = engine,
                context = webViewContext,
                loadId = webViewSession,
            )
        }
    }

    SlideIndexTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = dismissInteraction,
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = PANEL_HORIZONTAL_PADDING)
                    .widthIn(max = PickResultPanelMaxWidth)
                    .heightIn(max = maxPanelHeight)
                    .pickResultPanelCard()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.float_ball_image_search_panel_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.shell_panel_close))
                    }
                }

                EngineTabRow(
                    engines = engines,
                    selected = selectedEngine,
                    onSelected = { selectedEngine = it },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .padding(horizontal = 12.dp),
                ) {
                    engines.filter { it in mountedEngines }.forEach { engine ->
                        key(webViewSession, engine) {
                            val isSelected = selectedEngine == engine
                            AndroidView(
                                factory = {
                                    createSearchWebView(
                                        context = webViewContext,
                                        engine = engine,
                                        isFirstEngine = engine == engines.first(),
                                        onRegister = onRegisterWebView,
                                        onPageLoadingChanged = { loading ->
                                            loadingByEngine[engine] = loading
                                            if (!loading && engine == ImageSearchEngine.Google) {
                                                googleFirstLoadDone = true
                                            }
                                            if (selectedEngine == engine) {
                                                isPageLoading = loading
                                            }
                                        },
                                    ).also { engineWebViews[engine] = it }
                                },
                                update = { webView ->
                                    val interactive =
                                        isSelected && phase == ImageSearchPanelPhase.READY
                                    webView.isClickable = interactive
                                    webView.isFocusable = interactive
                                    webView.isFocusableInTouchMode = interactive
                                },
                                onRelease = { webView ->
                                    if (engineWebViews[engine] == webView) {
                                        engineWebViews.remove(engine)
                                        loadingByEngine.remove(engine)
                                    }
                                    FloatBallImageSearchPanel.releaseSearchWebView(webView)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(if (isSelected) 1f else 0f)
                                    .alpha(
                                        if (isSelected && phase == ImageSearchPanelPhase.READY) {
                                            1f
                                        } else {
                                            0f
                                        },
                                    ),
                            )
                        }
                    }

                    when (phase) {
                        ImageSearchPanelPhase.UPLOADING -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                                Text(
                                    text = stringResource(R.string.float_ball_image_search_uploading),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        ImageSearchPanelPhase.ERROR -> {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.float_ball_image_search_upload_failed),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = stringResource(R.string.float_ball_image_search_retry),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable(onClick = onRetry),
                                )
                            }
                        }

                        ImageSearchPanelPhase.READY -> {
                            if (isPageLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(28.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.float_ball_image_search_privacy_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val currentUrl = preloadedUrls[selectedEngine]
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                if (selectedEngine == ImageSearchEngine.Google) {
                                    clearSearchSessionCookies()
                                }
                                engineWebViews[selectedEngine]?.reload()
                            }
                        },
                        enabled = phase == ImageSearchPanelPhase.READY,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.float_ball_image_search_refresh))
                    }
                    IconButton(
                        onClick = {
                            currentUrl?.let(onOpenExternal)
                        },
                        enabled = phase == ImageSearchPanelPhase.READY && currentUrl != null,
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = stringResource(R.string.float_ball_image_search_open_browser))
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineTabRow(
    engines: List<ImageSearchEngine>,
    selected: ImageSearchEngine,
    onSelected: (ImageSearchEngine) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        engines.forEach { engine ->
            val isSelected = engine == selected
            Surface(
                onClick = { onSelected(engine) },
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                },
            ) {
                Text(
                    text = engine.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

private suspend fun clearSearchSessionCookies() = kotlin.coroutines.suspendCoroutine<Unit> { continuation ->
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().removeAllCookies {
        CookieManager.getInstance().flush()
        continuation.resumeWith(Result.success(Unit))
    }
}

private fun createSearchWebView(
    context: Context,
    engine: ImageSearchEngine,
    isFirstEngine: Boolean,
    onRegister: (WebView) -> Unit,
    onPageLoadingChanged: (Boolean) -> Unit,
): WebView {
    return WebView(context).apply {
        if (isFirstEngine) {
            clearCache(true)
        }
        syncSearchUserAgent(context, engine)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            androidx.webkit.WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
        }
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (!isActiveEngineCallback(view, url)) return
                onPageLoadingChanged(true)
            }

            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                if (!isActiveEngineCallback(view, finishedUrl)) return
                onPageLoadingChanged(false)
                Log.d(WEBVIEW_LOG_TAG, "page finished: $finishedUrl")
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame != true) return
                if (!isActiveEngineCallback(view, request.url?.toString())) return
                Log.w(
                    WEBVIEW_LOG_TAG,
                    "main frame error: ${error?.description} url=${request.url}",
                )
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                response: WebResourceResponse?,
            ) {
                val host = request?.url?.host.orEmpty()
                if (host.contains("google") || host.contains("catbox")) {
                    Log.w(
                        WEBVIEW_LOG_TAG,
                        "http ${response?.statusCode} ${request?.url}",
                    )
                }
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request == null || !request.isForMainFrame) return false
                if (!isActiveEngineCallback(view, request.url?.toString())) return true
                val targetUrl = request.url?.toString() ?: return false
                return when {
                    targetUrl.startsWith("http://") || targetUrl.startsWith("https://") -> false
                    targetUrl.startsWith("intent:") -> {
                        runCatching {
                            context.startActivity(Intent.parseUri(targetUrl, Intent.URI_INTENT_SCHEME))
                        }
                        true
                    }
                    else -> false
                }
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val message = consoleMessage ?: return false
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    Log.w(
                        WEBVIEW_LOG_TAG,
                        "console: ${message.message()} @ ${message.sourceId()}:${message.lineNumber()}",
                    )
                }
                return false
            }
        }
        onRegister(this)
    }
}

private fun isActiveEngineCallback(view: WebView?, url: String?): Boolean {
    val webView = view ?: return false
    val expectedEngine = webView.getTag(tagExpectedEngine) as? ImageSearchEngine ?: return true
    return urlMatchesEngine(url, expectedEngine)
}

private fun urlMatchesEngine(url: String?, engine: ImageSearchEngine): Boolean {
    if (url.isNullOrBlank()) return true
    return when (engine) {
        ImageSearchEngine.Google -> url.contains("google") || url.contains("lens.google")
        ImageSearchEngine.Yandex -> url.contains("yandex")
        ImageSearchEngine.TinEye -> url.contains("tineye")
    }
}

private fun WebView.loadSearchUrlAfterLayout(
    url: String,
    engine: ImageSearchEngine,
    context: Context,
    loadId: Int,
) {
    fun doLoad() {
        setTag(tagLoadGeneration, loadId)
        setTag(tagExpectedEngine, engine)
        stopLoading()
        syncSearchUserAgent(context, engine)
        onResume()
        CookieManager.getInstance().flush()
        Log.d(
            WEBVIEW_LOG_TAG,
            "loadUrl: $url engine=$engine id=$loadId " +
                "(size=${width}x$height, ua=${settings.userAgentString})",
        )
        loadUrl(url)
    }
    post {
        if (width > 0 && height > 0) {
            doLoad()
        } else {
            viewTreeObserver.addOnGlobalLayoutListener(
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        if (width <= 0 || height <= 0) return
                        viewTreeObserver.removeOnGlobalLayoutListener(this)
                        doLoad()
                    }
                },
            )
        }
    }
}

private fun WebView.syncSearchUserAgent(context: Context, engine: ImageSearchEngine) {
    settings.userAgentString = ImageSearchUrlBuilder.userAgent(context, engine)
}
