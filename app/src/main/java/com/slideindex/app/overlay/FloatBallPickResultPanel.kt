package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.perf.PickPerf
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.pickresult.PickResultTextSearchGrid
import com.slideindex.app.overlay.pickresult.pickResultSearchGridReservedHeight
import com.slideindex.app.overlay.pickresult.pickResultImageSectionReservedHeight
import com.slideindex.app.search.SearchEngineLauncher
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.overlay.pickresult.PickResultImageSearchBar
import com.slideindex.app.overlay.pickresult.PickResultInteractiveTextSection
import com.slideindex.app.overlay.pickresult.pickResultBottomPanelCard
import com.slideindex.app.overlay.pickresult.pickResultWindowHeightDp
import com.slideindex.app.overlay.pickresult.PickResultSectionHeader
import com.slideindex.app.overlay.pickresult.PickResultTextMode
import com.slideindex.app.service.ShareImageOcrCoordinator
import com.slideindex.app.ui.theme.SlideIndexTheme
import kotlinx.coroutines.flow.collect

private val PANEL_MAX_HEIGHT_FRACTION = 0.85f
private val PANEL_MAX_IMAGE_HEIGHT = 160.dp

/**
 * Bottom-anchored overlay pick-result panel after float-ball text pick / regional screenshot.
 */
object FloatBallPickResultPanel {
    private const val TAG = "FloatBallPickPanel"

    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var owner: OverlayComposeOwner? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var appContext: Context? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var textState: MutableState<String?>? = null
    private var screenshotState: MutableState<Bitmap?>? = null
    private var textExpandedState: MutableState<Boolean>? = null
    private var activeTextState: MutableState<String>? = null
    private var textModeState: MutableState<PickResultTextMode>? = null
    private var a11yTextState: MutableState<String?>? = null
    private var ocrTextState: MutableState<String?>? = null
    private var textSourceState: MutableState<PickResultTextSource>? = null
    private var ocrAvailableState: MutableState<Boolean>? = null
    private var ocrLoadingState: MutableState<Boolean>? = null
    private var a11ySourceEnabledState: MutableState<Boolean>? = null
    private var isShareImageOcrState: MutableState<Boolean>? = null
    private var ocrSwitchOnComplete = false
    private var captureSuppressed = false

    val isShowing: Boolean get() = composeView != null

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
        composeView?.visibility = View.VISIBLE
    }

    private fun updateWindowFocusableForMode(mode: PickResultTextMode) {
        updateWindowFocusable(
            focusable = mode == PickResultTextMode.SELECT || mode == PickResultTextMode.EDIT,
        )
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
        composeView?.visibility = View.VISIBLE
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
        textExpandedState?.value = true
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
        screenshotState?.value?.recycle()
        screenshotState?.value = null
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
        if (FloatBallTranslatePanel.isShowing) {
            FloatBallTranslatePanel.dismiss()
        }
        if (FloatBallImageSearchPanel.isShowing) {
            FloatBallImageSearchPanel.dismiss()
        }
        owner = null
        composeView = null
        layoutParams = null
        windowManager = null
        textState = null
        screenshotState = null
        textExpandedState = null
        activeTextState = null
        textModeState = null
        a11yTextState = null
        ocrTextState = null
        textSourceState = null
        ocrAvailableState = null
        ocrLoadingState = null
        a11ySourceEnabledState = null
        isShareImageOcrState = null
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
        val textExpandedHolder = mutableStateOf(true)
        val activeTextHolder = mutableStateOf("")
        val textModeHolder = mutableStateOf(PickResultTextMode.WORD_TAP)
        val a11yTextHolder = mutableStateOf<String?>(null)
        val ocrTextHolder = mutableStateOf<String?>(null)
        val textSourceHolder = mutableStateOf(PickResultTextSource.A11Y)
        val ocrAvailableHolder = mutableStateOf(false)
        val ocrLoadingHolder = mutableStateOf(false)
        val a11ySourceEnabledHolder = mutableStateOf(true)
        val isShareImageOcrHolder = mutableStateOf(false)
        textState = textHolder
        screenshotState = screenshotHolder
        textExpandedState = textExpandedHolder
        activeTextState = activeTextHolder
        textModeState = textModeHolder
        a11yTextState = a11yTextHolder
        ocrTextState = ocrTextHolder
        textSourceState = textSourceHolder
        ocrAvailableState = ocrAvailableHolder
        ocrLoadingState = ocrLoadingHolder
        a11ySourceEnabledState = a11ySourceEnabledHolder
        isShareImageOcrState = isShareImageOcrHolder

        val dialogOwner = OverlayComposeOwner()
        val overlayContext = OverlayCompose.themedContext(context)
        val compose = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            setContent {
                val text by textHolder
                val screenshot by screenshotHolder
                val textExpanded by textExpandedHolder
                val activeText by activeTextHolder
                val textMode by textModeHolder
                val ocrText by ocrTextHolder
                val textSource by textSourceHolder
                val ocrAvailable by ocrAvailableHolder
                val ocrLoading by ocrLoadingHolder
                val a11ySourceEnabled by a11ySourceEnabledHolder
                val isShareImageOcr by isShareImageOcrHolder
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
                    textExpanded = textExpanded,
                    activeText = activeText,
                    textMode = textMode,
                    textSource = textSource,
                    ocrAvailable = ocrAvailable,
                    a11yAvailable = a11ySourceEnabled,
                    ocrLoading = ocrLoading,
                    isShareImageOcr = isShareImageOcr,
                    onBackgroundOcr = {
                        ShareImageOcrCoordinator.moveToBackground(overlayContext)
                    },
                    translatePickPanelTransparency = settings.floatBallTranslatePickPanelTransparency,
                    textSizeSp = settings.floatBallPickTextSizeSp,
                    searchEngines = settings.searchEngines,
                    searchEngineGridColumns = settings.searchEngineGridColumns,
                    searchEngineGridRows = settings.searchEngineGridRows,
                    searchEngineShowLabels = settings.searchEngineShowLabels,
                    onTextSourceChange = { source ->
                        if (source == PickResultTextSource.A11Y && !a11ySourceEnabledHolder.value) {
                            return@FloatBallPickResultContent
                        }
                        if (source == PickResultTextSource.OCR && !ocrAvailable) {
                            return@FloatBallPickResultContent
                        }
                        textSourceHolder.value = source
                        val switched = when (source) {
                            PickResultTextSource.A11Y -> a11yTextHolder.value.orEmpty()
                            PickResultTextSource.OCR -> ocrTextHolder.value.orEmpty()
                        }
                        textHolder.value = switched
                        activeTextHolder.value = switched
                    },
                    onTextExpandedChange = { textExpandedHolder.value = it },
                    onActiveTextChange = { activeTextHolder.value = it },
                    onTextModeChange = { mode ->
                        textModeHolder.value = mode
                        updateWindowFocusableForMode(mode)
                    },
                    onDismiss = {
                        when {
                            FloatBallImageSearchPanel.isShowing -> FloatBallImageSearchPanel.dismiss()
                            FloatBallTranslatePanel.isShowing -> FloatBallTranslatePanel.dismiss()
                            else -> dismiss()
                        }
                    },
                    onTextChange = { textHolder.value = it },
                    onCopy = { value ->
                        FloatBallTextPick.copyText(context, value)
                        Toast.makeText(context, R.string.float_ball_text_copied, Toast.LENGTH_SHORT).show()
                    },
                    onShareText = { FloatBallTextPick.shareText(context, it) },
                    onPaste = {
                        val pasted = FloatBallTextPick.readClipboardText(context)
                        if (pasted == null) {
                            Toast.makeText(context, R.string.float_ball_paste_empty, Toast.LENGTH_SHORT).show()
                        } else {
                            textHolder.value = pasted
                            activeTextHolder.value = pasted
                        }
                    },
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
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun defaultTextModeFor(@Suppress("UNUSED_PARAMETER") text: String?): PickResultTextMode {
        return PickResultTextMode.WORD_TAP
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
private fun FloatBallPickResultContent(
    text: String?,
    screenshot: Bitmap?,
    textExpanded: Boolean,
    activeText: String,
    textMode: PickResultTextMode,
    textSource: PickResultTextSource,
    ocrAvailable: Boolean,
    a11yAvailable: Boolean,
    ocrLoading: Boolean,
    isShareImageOcr: Boolean,
    onBackgroundOcr: () -> Unit,
    translatePickPanelTransparency: Float,
    textSizeSp: Float,
    searchEngines: List<com.slideindex.app.settings.SearchEngineConfig>,
    searchEngineGridColumns: Int,
    searchEngineGridRows: Int,
    searchEngineShowLabels: Boolean,
    onTextSourceChange: (PickResultTextSource) -> Unit,
    onTextExpandedChange: (Boolean) -> Unit,
    onActiveTextChange: (String) -> Unit,
    onTextModeChange: (PickResultTextMode) -> Unit,
    onDismiss: () -> Unit,
    onTextChange: (String) -> Unit,
    onCopy: (String) -> Unit,
    onShareText: (String) -> Unit,
    onPaste: () -> Unit,
    onTranslate: (String) -> Unit,
    onRemoveSpaces: (String, removeAll: Boolean) -> Unit,
    onSaveScreenshot: () -> Unit,
    onShareScreenshot: () -> Unit,
    onImageShareEngineClick: (com.slideindex.app.settings.SearchEngineConfig) -> Unit,
    onImageSearch: () -> Unit,
    onSearchEngineClick: (com.slideindex.app.settings.SearchEngineConfig) -> Unit,
) {
    val hasTextSection = ocrLoading || !text.isNullOrBlank() || screenshot != null || ocrAvailable
    val showTextSection = hasTextSection || textMode == PickResultTextMode.EDIT
    val hasImageSection = screenshot != null
    val translateVisible by FloatBallTranslatePanel.panelVisible
    val imageSearchVisible by FloatBallImageSearchPanel.panelVisible
    val subPanelVisible = translateVisible || imageSearchVisible
    val pickPanelAlpha = if (subPanelVisible) {
        1f - translatePickPanelTransparency.coerceIn(0f, 1f)
    } else {
        1f
    }

    val maxPanelHeight = pickResultWindowHeightDp(PANEL_MAX_HEIGHT_FRACTION)

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
    val imageSectionReservedHeight = if (hasImageSection) {
        pickResultImageSectionReservedHeight(PANEL_MAX_IMAGE_HEIGHT)
    } else {
        0.dp
    }
    val shouldScrollText = showTextSection && (hasSearchGrid || hasImageSection)
    val textScrollMaxHeight = when {
        hasSearchGrid -> maxPanelHeight - searchGridReservedHeight - imageSectionReservedHeight
        hasImageSection -> maxPanelHeight - imageSectionReservedHeight
        else -> maxPanelHeight
    }
    val textScrollHeight = textScrollMaxHeight.coerceAtLeast(0.dp)
    val scrollState = rememberScrollState()

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
                        if (subPanelVisible) {
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
                    .padding(top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (hasImageSection) {
                    PickResultImageSection(
                        screenshot = screenshot,
                        searchEngines = searchEngines,
                        onSave = onSaveScreenshot,
                        onShare = onShareScreenshot,
                        onImageSearch = onImageSearch,
                        onShareEngineClick = onImageShareEngineClick,
                    )
                    if (showTextSection) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }

                if (showTextSection) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (shouldScrollText) {
                                    Modifier
                                        .heightIn(max = textScrollHeight)
                                        .verticalScroll(scrollState)
                                } else {
                                    Modifier
                                },
                            ),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        PickResultSectionHeader(
                            title = stringResource(R.string.float_ball_pick_result_text_section),
                            expanded = textExpanded,
                            onToggle = { onTextExpandedChange(!textExpanded) },
                        )
                        if (textExpanded) {
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
                                showBackgroundOcrAction = isShareImageOcr && ocrLoading,
                                onBackgroundOcr = onBackgroundOcr,
                                onTextSourceChange = onTextSourceChange,
                                pinActionBarOutside = true,
                                embedInParentScroll = shouldScrollText,
                                showSearch = false,
                                onActiveTextChange = onActiveTextChange,
                                onShare = onShareText,
                                onCopy = onCopy,
                                onPaste = onPaste,
                                onTranslate = onTranslate,
                                onRemoveSpaces = onRemoveSpaces,
                            )
                        }
                    }
                }

                if (hasSearchGrid) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
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
    searchEngines: List<com.slideindex.app.settings.SearchEngineConfig>,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onImageSearch: () -> Unit,
    onShareEngineClick: (com.slideindex.app.settings.SearchEngineConfig) -> Unit,
) {
    PickResultSectionHeader(
        title = stringResource(R.string.float_ball_pick_result_image_section),
        expanded = true,
        onToggle = {},
        collapsible = false,
    )
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val image = screenshot
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = PANEL_MAX_IMAGE_HEIGHT)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
            )
            PickResultImageSearchBar(
                engines = searchEngines,
                onShareEngineClick = onShareEngineClick,
                onShare = onShare,
                onImageSearch = onImageSearch,
                onSave = onSave,
            )
        }
    }
}
