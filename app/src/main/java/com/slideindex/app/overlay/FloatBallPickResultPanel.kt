package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.barcode.BarcodeScanResult
import com.slideindex.app.barcode.joinDisplayText
import com.slideindex.app.perf.PickPerf
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.pickresult.PickResultTextSearchGrid
import com.slideindex.app.overlay.pickresult.PickResultTextSearchGridTopSpacing
import com.slideindex.app.overlay.pickresult.pickResultImageContentWidth
import com.slideindex.app.overlay.pickresult.PickResultImageDisplaySize
import com.slideindex.app.overlay.pickresult.pickResultImageDisplaySize
import com.slideindex.app.overlay.pickresult.pickResultImageMaxHeightDp
import com.slideindex.app.overlay.pickresult.pickResultImageSectionReservedHeight
import com.slideindex.app.overlay.pickresult.pickResultSearchGridReservedHeight
import com.slideindex.app.overlay.pickresult.pickResultMinTextBodyAllocatedHeight
import com.slideindex.app.overlay.pickresult.pickResultTextBodyAllocatedHeight
import com.slideindex.app.overlay.pickresult.pickResultTextSectionChromeReservedHeight
import com.slideindex.app.search.SearchEngineLauncher
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.overlay.pickresult.PickResultImageSearchBar
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.slideindex.app.overlay.pickresult.PickResultInteractiveTextSection
import com.slideindex.app.overlay.pickresult.pickResultBottomPanelCard
import com.slideindex.app.overlay.pickresult.pickResultWindowHeightDp
import com.slideindex.app.overlay.pickresult.PickResultSectionHeader
import com.slideindex.app.overlay.pickresult.PickResultTextMode
import com.slideindex.app.service.ShareImageOcrCoordinator
import com.slideindex.app.stash.StashCoordinator
import com.slideindex.app.ui.theme.SlideIndexTheme
import kotlinx.coroutines.flow.collect

private val PANEL_MAX_HEIGHT_FRACTION = 0.85f
private val PANEL_MIN_IMAGE_HEIGHT = 48.dp
private val PANEL_VERTICAL_PADDING = 12.dp
private val TEXT_IMAGE_DIVIDER_HEIGHT = 25.dp
/**
 * Bottom-anchored overlay pick-result panel after float-ball text pick / regional screenshot.
 */
object FloatBallPickResultPanel {
    private const val TAG = "FloatBallPickPanel"

    private val mainHandler = Handler(Looper.getMainLooper())

    private var composeView: ComposeView? = null
    private var owner: OverlayComposeOwner? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var backHandler: OverlayViewBackHandler? = null
    private var appContext: Context? = null

    private var textState: MutableState<String?>? = null
    private var screenshotState: MutableState<Bitmap?>? = null
    private var activeTextState: MutableState<String>? = null
    private var textModeState: MutableState<PickResultTextMode>? = null
    private var a11yTextState: MutableState<String?>? = null
    private var ocrTextState: MutableState<String?>? = null
    private var textSourceState: MutableState<PickResultTextSource>? = null
    private var ocrAvailableState: MutableState<Boolean>? = null
    private var ocrLoadingState: MutableState<Boolean>? = null
    private var a11ySourceEnabledState: MutableState<Boolean>? = null
    private var isShareImageOcrState: MutableState<Boolean>? = null
    private var screenRectState: MutableState<Rect?>? = null
    private var layoutMetaState: MutableState<ScreenshotLayoutMeta?>? = null
    private var barcodeResultsState: MutableState<List<BarcodeScanResult>>? = null
    private var showingTranslationState: MutableState<Boolean>? = null
    private var translateLoadingState: MutableState<Boolean>? = null
    private var ocrSwitchOnComplete = false
    private var captureSuppressed = false
    private var pickPanelVisible = false

    val isShowing: Boolean get() = pickPanelVisible

    fun suppressForScreenshotCapture() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { suppressForScreenshotCapture() }
            return
        }
        if (composeView == null || captureSuppressed) return
        captureSuppressed = true
        composeView?.visibility = View.GONE
    }

    fun restoreAfterScreenshotCapture() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreAfterScreenshotCapture() }
            return
        }
        if (!captureSuppressed) return
        captureSuppressed = false
        if (pickPanelVisible) {
            composeView?.visibility = View.VISIBLE
        }
    }

    private fun updateWindowFocusableForMode(mode: PickResultTextMode) {
        updateWindowFocusable(focusable = true)
    }

    private fun handlePanelBack() {
        when {
            FloatBallImageSearchPanel.isShowing -> FloatBallImageSearchPanel.dismiss()
            else -> dismiss()
        }
    }

    fun showResult(
        context: Context,
        anchorX: Float = 0f,
        anchorY: Float = 0f,
        result: FloatBallPickResult,
        initialTextMode: PickResultTextMode? = null,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showResult(context, anchorX, anchorY, result, initialTextMode) }
            return
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context.applicationContext
        ensureWindow(hostContext)
        captureSuppressed = false
        pickPanelVisible = true
        composeView?.visibility = View.VISIBLE
        FloatBallOverlay.bringChromeAbovePanels()
        composeView?.requestFocus()
        a11yTextState?.value = result.a11yText
        ocrTextState?.value = result.ocrText
        textSourceState?.value = result.activeSource
        ocrAvailableState?.value = result.ocrAvailable
        ocrLoadingState?.value = result.ocrPending
        a11ySourceEnabledState?.value = result.a11ySourceEnabled
        isShareImageOcrState?.value = result.isShareImageOcr
        ocrSwitchOnComplete = result.ocrPreferSwitchOnComplete
        textState?.value = result.text
        activeTextState?.value = result.text.orEmpty()
        screenshotState?.value?.recycle()
        screenshotState?.value = result.screenshot
        screenRectState?.value = result.screenRect?.let { Rect(it) }
        layoutMetaState?.value = result.layoutMeta
        barcodeResultsState?.value = result.barcodeResults
        clearTranslateState()
        textModeState?.value = initialTextMode ?: defaultTextModeFor(result.text)
        updateWindowFocusableForMode(textModeState?.value ?: PickResultTextMode.WORD_TAP)
        if (result.text.isNullOrBlank() && result.screenshot == null) {
            Toast.makeText(hostContext, R.string.float_ball_text_not_found, Toast.LENGTH_SHORT).show()
            dismiss()
        }
        PickPerf.mark("panel_showResult_done", "source=${result.activeSource}")
    }

    fun updateOcrText(
        ocrText: String,
        switchToOcr: Boolean = ocrSwitchOnComplete,
        initialTextMode: PickResultTextMode? = null,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateOcrText(ocrText, switchToOcr, initialTextMode) }
            return
        }
        ocrLoadingState?.value = false
        ocrTextState?.value = ocrText
        ocrAvailableState?.value = true
        if (switchToOcr || textSourceState?.value == PickResultTextSource.OCR) {
            clearTranslateState()
            textSourceState?.value = PickResultTextSource.OCR
            textState?.value = ocrText
            activeTextState?.value = ocrText
        }
        initialTextMode?.let { mode ->
            textModeState?.value = mode
            updateWindowFocusableForMode(mode)
        }
        PickPerf.mark("panel_ocr_updated", "len=${ocrText.length}")
    }

    fun isShowingTranslation(): Boolean = showingTranslationState?.value == true

    fun showTranslateLoading() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showTranslateLoading() }
            return
        }
        if (!isShowing) return
        showingTranslationState?.value = false
        translateLoadingState?.value = true
    }

    fun showTranslateResult(translatedText: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showTranslateResult(translatedText) }
            return
        }
        if (!isShowing) return
        translateLoadingState?.value = false
        showingTranslationState?.value = true
        textState?.value = translatedText
        activeTextState?.value = translatedText
        updateWindowFocusableForMode(textModeState?.value ?: PickResultTextMode.WORD_TAP)
    }

    fun showTranslateError(context: Context, message: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showTranslateError(context, message) }
            return
        }
        if (!isShowing) return
        translateLoadingState?.value = false
        showingTranslationState?.value = false
        val hostContext = appContext ?: context.applicationContext
        Toast.makeText(
            hostContext,
            translateErrorMessage(hostContext, message),
            Toast.LENGTH_SHORT,
        ).show()
        updateWindowFocusableForMode(textModeState?.value ?: PickResultTextMode.WORD_TAP)
    }

    fun restoreFromTranslation() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreFromTranslation() }
            return
        }
        if (!isShowing) return
        showingTranslationState?.value = false
        translateLoadingState?.value = false
        applyTextForCurrentSource()
        updateWindowFocusableForMode(textModeState?.value ?: PickResultTextMode.WORD_TAP)
    }

    private fun applyTextForCurrentSource() {
        val source = textSourceState?.value ?: PickResultTextSource.A11Y
        val text = textForSource(
            source = source,
            a11yText = a11yTextState?.value,
            ocrText = ocrTextState?.value,
            barcodeResults = barcodeResultsState?.value.orEmpty(),
        )
        textState?.value = text
        activeTextState?.value = text
    }

    private fun textForSource(
        source: PickResultTextSource,
        a11yText: String?,
        ocrText: String?,
        barcodeResults: List<BarcodeScanResult>,
    ): String = when (source) {
        PickResultTextSource.A11Y -> a11yText.orEmpty()
        PickResultTextSource.OCR -> ocrText.orEmpty()
        PickResultTextSource.BARCODE -> barcodeResults.joinDisplayText()
    }

    private fun clearTranslateState() {
        showingTranslationState?.value = false
        translateLoadingState?.value = false
    }

    fun finishOcrPending() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { finishOcrPending() }
            return
        }
        ocrLoadingState?.value = false
        PickPerf.mark("panel_ocr_pending_done", "empty=true")
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        pickPanelVisible = false
        screenshotState?.value?.recycle()
        screenshotState?.value = null
        composeView?.visibility = View.GONE
        if (FloatBallImageSearchPanel.isShowing) {
            FloatBallImageSearchPanel.dismiss()
        }
        clearTranslateState()
        val view = composeView
        val wm = windowManager
        if (view != null && wm != null) {
            runCatching { wm.removeView(view) }
        }
        screenOffReceiver?.let { receiver ->
            appContext?.let { ctx -> runCatching { ctx.unregisterReceiver(receiver) } }
        }
        val currentOwner = owner
        if (currentOwner != null) {
            view?.post { currentOwner.destroy() } ?: currentOwner.destroy()
        }
        backHandler?.detach()
        backHandler = null
        owner = null
        composeView = null
        layoutParams = null
        windowManager = null
        textState = null
        screenshotState = null
        activeTextState = null
        textModeState = null
        a11yTextState = null
        ocrTextState = null
        textSourceState = null
        ocrAvailableState = null
        ocrLoadingState = null
        a11ySourceEnabledState = null
        isShareImageOcrState = null
        screenRectState = null
        layoutMetaState = null
        barcodeResultsState = null
        showingTranslationState = null
        translateLoadingState = null
        ocrSwitchOnComplete = false
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

        val textHolder = mutableStateOf<String?>(null)
        val screenshotHolder = mutableStateOf<Bitmap?>(null)
        val activeTextHolder = mutableStateOf("")
        val textModeHolder = mutableStateOf(PickResultTextMode.WORD_TAP)
        val a11yTextHolder = mutableStateOf<String?>(null)
        val ocrTextHolder = mutableStateOf<String?>(null)
        val textSourceHolder = mutableStateOf(PickResultTextSource.A11Y)
        val ocrAvailableHolder = mutableStateOf(false)
        val ocrLoadingHolder = mutableStateOf(false)
        val a11ySourceEnabledHolder = mutableStateOf(true)
        val isShareImageOcrHolder = mutableStateOf(false)
        val screenRectHolder = mutableStateOf<Rect?>(null)
        val layoutMetaHolder = mutableStateOf<ScreenshotLayoutMeta?>(null)
        val barcodeResultsHolder = mutableStateOf<List<BarcodeScanResult>>(emptyList())
        val showingTranslationHolder = mutableStateOf(false)
        val translateLoadingHolder = mutableStateOf(false)
        textState = textHolder
        screenshotState = screenshotHolder
        activeTextState = activeTextHolder
        textModeState = textModeHolder
        a11yTextState = a11yTextHolder
        ocrTextState = ocrTextHolder
        textSourceState = textSourceHolder
        ocrAvailableState = ocrAvailableHolder
        ocrLoadingState = ocrLoadingHolder
        a11ySourceEnabledState = a11ySourceEnabledHolder
        isShareImageOcrState = isShareImageOcrHolder
        screenRectState = screenRectHolder
        layoutMetaState = layoutMetaHolder
        barcodeResultsState = barcodeResultsHolder
        showingTranslationState = showingTranslationHolder
        translateLoadingState = translateLoadingHolder

        val dialogOwner = OverlayComposeOwner()
        val overlayContext = OverlayCompose.themedContext(context)
        val compose = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            setContent {
                val text by textHolder
                val screenshot by screenshotHolder
                val activeText by activeTextHolder
                val textMode by textModeHolder
                val ocrText by ocrTextHolder
                val textSource by textSourceHolder
                val ocrAvailable by ocrAvailableHolder
                val ocrLoading by ocrLoadingHolder
                val a11ySourceEnabled by a11ySourceEnabledHolder
                val isShareImageOcr by isShareImageOcrHolder
                val screenRect by screenRectHolder
                val layoutMeta by layoutMetaHolder
                val barcodeResults by barcodeResultsHolder
                val showingTranslation by showingTranslationHolder
                val translateLoading by translateLoadingHolder
                val settingsHolder = remember { mutableStateOf(AppSettings()) }
                LaunchedEffect(overlayContext) {
                    val flow = OverlayDependencyAccess.overlayDependencies(overlayContext)
                        ?.settingsRepository
                        ?.settings
                        ?: return@LaunchedEffect
                    flow.collect { settingsHolder.value = it }
                }
                val settings by settingsHolder
                LaunchedEffect(text) {
                    if (text != null) {
                        activeTextHolder.value = text.orEmpty()
                    }
                }
                FloatBallPickResultContent(
                    text = text,
                    screenshot = screenshot,
                    activeText = activeText,
                    textMode = textMode,
                    textSource = textSource,
                    ocrAvailable = ocrAvailable,
                    a11yAvailable = a11ySourceEnabled,
                    ocrLoading = ocrLoading,
                    isShareImageOcr = isShareImageOcr,
                    barcodeResults = barcodeResults,
                    showingTranslation = showingTranslation,
                    translateLoading = translateLoading,
                    onBackgroundOcr = {
                        ShareImageOcrCoordinator.moveToBackground(overlayContext)
                    },
                    imageSearchPickPanelTransparency = settings.floatBallImageSearchPickPanelTransparency,
                    textSizeSp = settings.floatBallPickTextSizeSp,
                    searchEngines = settings.searchEngines,
                    searchEngineGridColumns = settings.searchEngineGridColumns,
                    searchEngineGridRows = settings.searchEngineGridRows,
                    searchEngineShowLabels = settings.searchEngineShowLabels,
                    onImageClick = {
                        screenshot?.let {
                            val opened = FloatBallTextPick.viewScreenshot(
                                appContext ?: overlayContext,
                                it,
                                settings.defaultImageViewerPackage,
                            )
                            if (opened) {
                                dismiss()
                            }
                        }
                    },
                    onTextSourceChange = { source ->
                        if (source == PickResultTextSource.A11Y && !a11ySourceEnabledHolder.value) {
                            return@FloatBallPickResultContent
                        }
                        if (source == PickResultTextSource.OCR && !ocrAvailable) {
                            return@FloatBallPickResultContent
                        }
                        if (source == PickResultTextSource.BARCODE && barcodeResultsHolder.value.isEmpty()) {
                            return@FloatBallPickResultContent
                        }
                        clearTranslateState()
                        textSourceHolder.value = source
                        val switched = textForSource(
                            source = source,
                            a11yText = a11yTextHolder.value,
                            ocrText = ocrTextHolder.value,
                            barcodeResults = barcodeResultsHolder.value,
                        )
                        textHolder.value = switched
                        activeTextHolder.value = switched
                    },
                    onActiveTextChange = { activeTextHolder.value = it },
                    onTextModeChange = { mode ->
                        textModeHolder.value = mode
                        updateWindowFocusableForMode(mode)
                    },
                    onDismiss = {
                        when {
                            FloatBallImageSearchPanel.isShowing -> FloatBallImageSearchPanel.dismiss()
                            else -> dismiss()
                        }
                    },
                    onTextChange = { textHolder.value = it },
                    onCopy = { value ->
                        FloatBallTextPick.copyText(context, value)
                        Toast.makeText(context, R.string.float_ball_text_copied, Toast.LENGTH_SHORT).show()
                    },
                    onShareText = { FloatBallTextPick.shareText(context, it) },
                    onTranslate = { FloatBallTranslateCoordinator.translate(context, it) },
                    onRemoveSpaces = { value, removeAll ->
                        textHolder.value = if (removeAll) {
                            value.replace(Regex("\\s+"), "")
                        } else {
                            value.trim()
                        }
                    },
                    onSaveScreenshot = {
                        val bitmap = screenshotHolder.value ?: return@FloatBallPickResultContent
                        val saved = FloatBallTextPick.saveScreenshot(context, bitmap)
                        Toast.makeText(
                            context,
                            if (saved) R.string.float_ball_screenshot_saved else R.string.float_ball_action_failed,
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onShareScreenshot = {
                        val bitmap = screenshotHolder.value ?: return@FloatBallPickResultContent
                        FloatBallTextPick.shareScreenshot(context, bitmap)
                    },
                    onImageShareEngineClick = { engine ->
                        val bitmap = screenshotHolder.value ?: return@FloatBallPickResultContent
                        val launched = SearchEngineLauncher.launchImageShare(context, engine, bitmap)
                        if (launched) {
                            dismiss()
                        }
                    },
                    onImageSearch = {
                        val bitmap = screenshotHolder.value ?: return@FloatBallPickResultContent
                        FloatBallImageSearchPanel.show(context, bitmap)
                    },
                    onSearchEngineClick = { engine ->
                        val launched = SearchEngineLauncher.launch(
                            context,
                            engine,
                            activeTextHolder.value,
                        )
                        if (launched) {
                            dismiss()
                        }
                    },
                    onPinTextToScreen = { value ->
                        StashCoordinator.pinTextToScreen(overlayContext, value)
                        dismiss()
                    },
                    onStashText = { value ->
                        StashCoordinator.addText(value) { success ->
                            if (success) {
                                Toast.makeText(overlayContext, R.string.stash_saved, Toast.LENGTH_SHORT).show()
                                StashCoordinator.openStashPanel(overlayContext)
                                dismiss()
                            } else {
                                Toast.makeText(overlayContext, R.string.stash_save_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onPinImageToScreen = {
                        val bitmap = screenshotHolder.value ?: return@FloatBallPickResultContent
                        val meta = layoutMeta ?: buildScreenshotLayoutMeta(
                            bitmap = bitmap,
                            screenWidthPx = overlayContext.resources.displayMetrics.widthPixels,
                            screenHeightPx = overlayContext.resources.displayMetrics.heightPixels,
                        )
                        StashCoordinator.pinImageToScreen(
                            overlayContext,
                            bitmap,
                            screenRect,
                            meta,
                        )
                        dismiss()
                    },
                    onStashImage = {
                        val bitmap = screenshotHolder.value ?: return@FloatBallPickResultContent
                        val metrics = overlayContext.resources.displayMetrics
                        val (displayW, displayH) = resolvePinImageDisplaySizePx(
                            bitmap = bitmap,
                            screenRect = screenRect,
                            layoutMeta = layoutMeta ?: buildScreenshotLayoutMeta(
                                bitmap = bitmap,
                                screenWidthPx = metrics.widthPixels,
                                screenHeightPx = metrics.heightPixels,
                            ),
                            screenWidthPx = metrics.widthPixels,
                            screenHeightPx = metrics.heightPixels,
                        )
                        StashCoordinator.addImage(
                            bitmap = bitmap,
                            pinDisplayWidthPx = displayW,
                            pinDisplayHeightPx = displayH,
                        ) { success ->
                            if (success) {
                                Toast.makeText(overlayContext, R.string.stash_saved, Toast.LENGTH_SHORT).show()
                                StashCoordinator.openStashPanel(overlayContext)
                                dismiss()
                            } else {
                                Toast.makeText(overlayContext, R.string.stash_save_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    screenRect = screenRect,
                    layoutMeta = layoutMeta,
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
            Log.e(TAG, "failed to add pick result panel")
            return
        }

        windowManager = wm
        composeView = compose
        owner = dialogOwner
        layoutParams = params
        appContext = context
        backHandler = OverlayViewBackHandler(compose, ::handlePanelBack).also { it.attach() }
        registerScreenOffReceiver(context)
    }

    private fun buildLayoutParams(context: Context): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun registerScreenOffReceiver(context: Context) {
        if (screenOffReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) dismiss()
            }
        }
        screenOffReceiver = receiver
        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
    }

    private fun defaultTextModeFor(@Suppress("UNUSED_PARAMETER") text: String?): PickResultTextMode {
        return PickResultTextMode.WORD_TAP
    }
}

@Composable
private fun FloatBallPickResultContent(
    text: String?,
    screenshot: Bitmap?,
    activeText: String,
    textMode: PickResultTextMode,
    textSource: PickResultTextSource,
    ocrAvailable: Boolean,
    a11yAvailable: Boolean,
    ocrLoading: Boolean,
    isShareImageOcr: Boolean,
    barcodeResults: List<BarcodeScanResult>,
    showingTranslation: Boolean,
    translateLoading: Boolean,
    onBackgroundOcr: () -> Unit,
    imageSearchPickPanelTransparency: Float,
    textSizeSp: Float,
    searchEngines: List<com.slideindex.app.settings.SearchEngineConfig>,
    searchEngineGridColumns: Int,
    searchEngineGridRows: Int,
    searchEngineShowLabels: Boolean,
    onTextSourceChange: (PickResultTextSource) -> Unit,
    onActiveTextChange: (String) -> Unit,
    onTextModeChange: (PickResultTextMode) -> Unit,
    onDismiss: () -> Unit,
    onTextChange: (String) -> Unit,
    onCopy: (String) -> Unit,
    onShareText: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onRemoveSpaces: (String, removeAll: Boolean) -> Unit,
    onSaveScreenshot: () -> Unit,
    onShareScreenshot: () -> Unit,
    onImageShareEngineClick: (com.slideindex.app.settings.SearchEngineConfig) -> Unit,
    onImageSearch: () -> Unit,
    onSearchEngineClick: (com.slideindex.app.settings.SearchEngineConfig) -> Unit,
    onPinTextToScreen: (String) -> Unit,
    onStashText: (String) -> Unit,
    onPinImageToScreen: () -> Unit,
    onStashImage: () -> Unit,
    onImageClick: () -> Unit,
    screenRect: Rect?,
    layoutMeta: ScreenshotLayoutMeta?,
) {
    val hasTextSection = ocrLoading || !text.isNullOrBlank() || screenshot != null ||
        ocrAvailable || barcodeResults.isNotEmpty()
    val showTextSection = hasTextSection || textMode == PickResultTextMode.EDIT
    val hasImageSection = screenshot != null
    val imageSearchVisible by FloatBallImageSearchPanel.panelVisible
    val pickPanelAlpha = if (imageSearchVisible) {
        1f - imageSearchPickPanelTransparency.coerceIn(0f, 1f)
    } else {
        1f
    }

    val maxPanelHeight = pickResultWindowHeightDp(PANEL_MAX_HEIGHT_FRACTION)
    val density = LocalDensity.current
    val panelMaxImageHeight = pickResultImageMaxHeightDp()
    val imageContentWidth = pickResultImageContentWidth()
    val displayMetrics = LocalContext.current.resources.displayMetrics

    val dismissInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    val panelSearchEngines = remember(searchEngines) {
        SearchEngineStore.textPickPanelEngines(searchEngines)
    }
    val hasSearchGrid = showTextSection && panelSearchEngines.isNotEmpty()
    val searchGridReservedHeight = if (hasSearchGrid) {
        pickResultSearchGridReservedHeight(
            searchEngineGridRows,
            searchEngineShowLabels,
            searchEngineGridColumns,
        )
    } else {
        0.dp
    }
    val textImageDividerHeight = if (hasImageSection && showTextSection) {
        TEXT_IMAGE_DIVIDER_HEIGHT
    } else {
        0.dp
    }
    val textSectionChromeHeight = if (showTextSection) {
        pickResultTextSectionChromeReservedHeight()
    } else {
        0.dp
    }
    val idealTextBodyHeight = if (showTextSection) {
        pickResultTextBodyAllocatedHeight(textSizeSp)
    } else {
        0.dp
    }
    val minTextBodyHeight = if (showTextSection) {
        pickResultMinTextBodyAllocatedHeight(textSizeSp = textSizeSp, lines = 6)
    } else {
        0.dp
    }
    val panelVerticalPadding = PANEL_VERTICAL_PADDING * 2
    val isImageVisible = remember { mutableStateOf(true) }
    val dragOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    
    val imageSectionFixedChrome = if (hasImageSection) {
        pickResultImageSectionReservedHeight(0.dp, isImageVisible.value)
    } else {
        0.dp
    }
    val reservedForTextAndChrome = panelVerticalPadding +
        (if (showTextSection) {
            textSectionChromeHeight + textImageDividerHeight + minTextBodyHeight
        } else {
            0.dp
        }) +
        searchGridReservedHeight
    val affordableImageMaxHeight = if (hasImageSection) {
        (maxPanelHeight - reservedForTextAndChrome - imageSectionFixedChrome)
            .coerceAtLeast(PANEL_MIN_IMAGE_HEIGHT)
    } else {
        panelMaxImageHeight
    }
    val effectivePanelMaxImageHeight = minOf(panelMaxImageHeight, affordableImageMaxHeight)
    val panelImageDisplaySize = screenshot?.let { bitmap ->
        pickResultImageDisplaySize(
            bitmap = bitmap,
            contentWidth = imageContentWidth,
            maxHeight = effectivePanelMaxImageHeight,
            density = density,
            screenRect = screenRect,
            layoutMeta = layoutMeta,
            screenWidthPx = displayMetrics.widthPixels,
            screenHeightPx = displayMetrics.heightPixels,
        )
    } ?: PickResultImageDisplaySize(0.dp, 0.dp)
    
    val targetImageSectionReservedHeight = if (hasImageSection) {
        val actualImageHeight = if (isImageVisible.value) panelImageDisplaySize.height else 0.dp
        pickResultImageSectionReservedHeight(actualImageHeight, isImageVisible.value)
    } else {
        0.dp
    }
    
    val animatedImageSectionReservedHeight by androidx.compose.animation.core.animateDpAsState(
        targetValue = targetImageSectionReservedHeight,
        animationSpec = androidx.compose.animation.core.spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "imageSectionHeight"
    )
    
    val currentImageSectionReservedHeight = if (hasImageSection) {
        val maxH = pickResultImageSectionReservedHeight(panelImageDisplaySize.height, true)
        val minH = pickResultImageSectionReservedHeight(0.dp, false)
        (animatedImageSectionReservedHeight + with(density) { dragOffset.value.toDp() })
            .coerceIn(minH, maxH)
    } else {
        0.dp
    }
    
    val textBodyMaxHeight = if (showTextSection) {
        val affordable = maxPanelHeight -
            currentImageSectionReservedHeight -
            searchGridReservedHeight -
            textSectionChromeHeight -
            textImageDividerHeight -
            panelVerticalPadding
        // always let text area follow affordable height so it fluidly responds to the drag
        affordable
    } else {
        null
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
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxPanelHeight)
                    .graphicsLayer { alpha = pickPanelAlpha }
                    .pickResultBottomPanelCard()
                    .then(
                        if (imageSearchVisible) {
                            Modifier.clickable(
                                interactionSource = cardInteraction,
                                indication = null,
                                onClick = onDismiss,
                            )
                        } else {
                            Modifier.clickable(
                                interactionSource = cardInteraction,
                                indication = null,
                                onClick = {},
                            )
                        },
                    )
                    .padding(top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (hasImageSection) {
                    PickResultImageSection(
                        screenshot = screenshot,
                        imageDisplaySize = panelImageDisplaySize,
                        searchEngines = searchEngines,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(currentImageSectionReservedHeight)
                            .clipToBounds(),
                        onSave = onSaveScreenshot,
                        onShare = onShareScreenshot,
                        onImageSearch = onImageSearch,
                        onShareEngineClick = onImageShareEngineClick,
                        onPinToScreen = onPinImageToScreen,
                        onStash = onStashImage,
                        onImageClick = onImageClick,
                        isImageVisible = isImageVisible,
                    )
                }

                if (showTextSection) {
                    if (hasImageSection) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    PickResultInteractiveTextSection(
                        text = text.orEmpty(),
                        textMode = textMode,
                        onTextModeChange = onTextModeChange,
                        onTextChange = onTextChange,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        textSizeSp = textSizeSp,
                        textSource = textSource,
                        ocrAvailable = ocrAvailable,
                        a11yAvailable = a11yAvailable,
                        ocrLoading = ocrLoading,
                        barcodeResults = barcodeResults,
                        showingTranslation = showingTranslation,
                        translateLoading = translateLoading,
                        showBackgroundOcrAction = isShareImageOcr && ocrLoading,
                        onBackgroundOcr = onBackgroundOcr,
                        onTextSourceChange = onTextSourceChange,
                        pinActionBarOutside = true,
                        bodyMaxHeight = textBodyMaxHeight,
                        showSearch = false,
                        onActiveTextChange = onActiveTextChange,
                        onShare = onShareText,
                        onCopy = onCopy,
                        onTranslate = onTranslate,
                        onRemoveSpaces = onRemoveSpaces,
                        onZoomText = { expanded -> isImageVisible.value = !expanded },
                        onDragDelta = { delta ->
                            coroutineScope.launch {
                                dragOffset.snapTo(dragOffset.value + delta)
                            }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                if (dragOffset.value < -50f) {
                                    isImageVisible.value = false
                                } else if (dragOffset.value > 50f) {
                                    isImageVisible.value = true
                                }
                                dragOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        },
                        onPinToScreen = { onPinTextToScreen(activeText) },
                        onStash = { onStashText(activeText) },
                    )
                }

                if (hasSearchGrid) {
                    if (showTextSection || hasImageSection) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                        Spacer(modifier = Modifier.height(12.dp + PickResultTextSearchGridTopSpacing))
                    }
                    PickResultTextSearchGrid(
                        engines = searchEngines,
                        query = activeText,
                        columns = searchEngineGridColumns,
                        rows = searchEngineGridRows,
                        showLabels = searchEngineShowLabels,
                        onEngineClick = onSearchEngineClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun PickResultImageSection(
    screenshot: Bitmap?,
    imageDisplaySize: PickResultImageDisplaySize,
    searchEngines: List<com.slideindex.app.settings.SearchEngineConfig>,
    modifier: Modifier = Modifier,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onImageSearch: () -> Unit,
    onShareEngineClick: (com.slideindex.app.settings.SearchEngineConfig) -> Unit,
    onPinToScreen: () -> Unit,
    onStash: () -> Unit,
    onImageClick: () -> Unit,
    isImageVisible: MutableState<Boolean>,
) {
    val image = screenshot
    if (image != null) {
        Column(modifier = modifier) {
            PickResultSectionHeader(
                title = stringResource(R.string.float_ball_pick_result_image_section),
                expanded = isImageVisible.value,
                onToggle = { isImageVisible.value = !isImageVisible.value },
                collapsible = true,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .width(imageDisplaySize.width)
                            .height(imageDisplaySize.height)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onImageClick),
                        contentScale = ContentScale.Fit,
                    )
                }
                PickResultImageSearchBar(
                    engines = searchEngines,
                    onShareEngineClick = onShareEngineClick,
                    onShare = onShare,
                    onImageSearch = onImageSearch,
                    onSave = onSave,
                    onPinToScreen = onPinToScreen,
                    onStash = onStash,
                    onThumbnailClick = { isImageVisible.value = !isImageVisible.value }
                )
            }
        }
    }
}

private fun translateErrorMessage(context: Context, code: String): String = when (code) {
    "mlkit_model_not_installed" -> context.getString(R.string.float_ball_translate_error_model_missing)
    "wifi_required" -> context.getString(R.string.float_ball_translate_error_wifi_required)
    "unsupported_language" -> context.getString(R.string.float_ball_translate_error_unsupported_language)
    "translate_unavailable" -> context.getString(R.string.float_ball_translate_error_unavailable)
    "network_error", "http_403", "http_429", "http_500" ->
        context.getString(R.string.float_ball_translate_error_network)
    else -> code
}
