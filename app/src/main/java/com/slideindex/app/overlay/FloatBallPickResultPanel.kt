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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.perf.PickPerf
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.pickresult.PickResultInteractiveTextSection
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.overlay.pickresult.PickResultPanelMaxWidth
import com.slideindex.app.overlay.pickresult.pickResultPanelCard
import com.slideindex.app.overlay.pickresult.PickResultSectionHeader
import com.slideindex.app.overlay.pickresult.PickResultToolbarIcon
import com.slideindex.app.overlay.pickresult.PickResultTextMode
import com.slideindex.app.ui.theme.SlideIndexTheme
import kotlinx.coroutines.flow.collect

private val PANEL_HORIZONTAL_PADDING = 12.dp
private val PANEL_MAX_WIDTH = PickResultPanelMaxWidth
private val PANEL_MAX_HEIGHT_FRACTION = 0.85f
private val PANEL_MAX_IMAGE_HEIGHT = 140.dp

/**
 * FV-style centered pick-result window after float-ball text pick / regional screenshot.
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
    private var imageExpandedState: MutableState<Boolean>? = null
    private var textModeState: MutableState<PickResultTextMode>? = null
    private var a11yTextState: MutableState<String?>? = null
    private var ocrTextState: MutableState<String?>? = null
    private var textSourceState: MutableState<PickResultTextSource>? = null
    private var ocrAvailableState: MutableState<Boolean>? = null
    private var ocrLoadingState: MutableState<Boolean>? = null
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
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showResult(context, anchorX, anchorY, result) }
            return
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context.applicationContext
        ensureWindow(hostContext)
        captureSuppressed = false
        composeView?.visibility = View.VISIBLE
        a11yTextState?.value = result.a11yText
        ocrTextState?.value = result.ocrText
        textSourceState?.value = result.activeSource
        ocrAvailableState?.value = result.canToggleSource()
        ocrLoadingState?.value = result.ocrPending
        ocrSwitchOnComplete = result.ocrPreferSwitchOnComplete
        textState?.value = result.text
        screenshotState?.value?.recycle()
        screenshotState?.value = result.screenshot
        textExpandedState?.value = true
        imageExpandedState?.value = result.screenshot != null
        textModeState?.value = PickResultTextMode.WORD_TAP
        updateWindowFocusableForMode(PickResultTextMode.WORD_TAP)
        if (result.text.isNullOrBlank() && result.screenshot == null) {
            Toast.makeText(hostContext, R.string.float_ball_text_not_found, Toast.LENGTH_SHORT).show()
            dismiss()
        }
        PickPerf.mark("panel_showResult_done", "source=${result.activeSource}")
    }

    fun updateOcrText(ocrText: String, switchToOcr: Boolean = ocrSwitchOnComplete) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateOcrText(ocrText, switchToOcr) }
            return
        }
        ocrLoadingState?.value = false
        ocrTextState?.value = ocrText
        ocrAvailableState?.value = true
        if (switchToOcr || textSourceState?.value == PickResultTextSource.OCR) {
            textSourceState?.value = PickResultTextSource.OCR
            textState?.value = ocrText
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
        owner = null
        composeView = null
        layoutParams = null
        windowManager = null
        textState = null
        screenshotState = null
        textExpandedState = null
        imageExpandedState = null
        textModeState = null
        a11yTextState = null
        ocrTextState = null
        textSourceState = null
        ocrAvailableState = null
        ocrLoadingState = null
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
        val imageExpandedHolder = mutableStateOf(true)
        val textModeHolder = mutableStateOf(PickResultTextMode.WORD_TAP)
        val a11yTextHolder = mutableStateOf<String?>(null)
        val ocrTextHolder = mutableStateOf<String?>(null)
        val textSourceHolder = mutableStateOf(PickResultTextSource.A11Y)
        val ocrAvailableHolder = mutableStateOf(false)
        val ocrLoadingHolder = mutableStateOf(false)
        textState = textHolder
        screenshotState = screenshotHolder
        textExpandedState = textExpandedHolder
        imageExpandedState = imageExpandedHolder
        textModeState = textModeHolder
        a11yTextState = a11yTextHolder
        ocrTextState = ocrTextHolder
        textSourceState = textSourceHolder
        ocrAvailableState = ocrAvailableHolder
        ocrLoadingState = ocrLoadingHolder

        val dialogOwner = OverlayComposeOwner()
        val overlayContext = OverlayCompose.themedContext(context)
        val compose = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            setContent {
                val text by textHolder
                val screenshot by screenshotHolder
                val textExpanded by textExpandedHolder
                val imageExpanded by imageExpandedHolder
                val textMode by textModeHolder
                val a11yText by a11yTextHolder
                val ocrText by ocrTextHolder
                val textSource by textSourceHolder
                val ocrAvailable by ocrAvailableHolder
                val ocrLoading by ocrLoadingHolder
                val settingsHolder = remember { mutableStateOf(AppSettings()) }
                LaunchedEffect(overlayContext) {
                    val flow = OverlayDependencyAccess.overlayDependencies(overlayContext)
                        ?.settingsRepository
                        ?.settings
                        ?: return@LaunchedEffect
                    flow.collect { settingsHolder.value = it }
                }
                val settings by settingsHolder
                FloatBallPickResultContent(
                    text = text,
                    screenshot = screenshot,
                    textExpanded = textExpanded,
                    imageExpanded = imageExpanded,
                    textMode = textMode,
                    textSource = textSource,
                    ocrAvailable = ocrAvailable,
                    ocrLoading = ocrLoading,
                    translatePickPanelTransparency = settings.floatBallTranslatePickPanelTransparency,
                    textSizeSp = settings.floatBallPickTextSizeSp,
                    onTextSourceChange = { source ->
                        textSourceHolder.value = source
                        textHolder.value = when (source) {
                            PickResultTextSource.A11Y -> a11yTextHolder.value.orEmpty()
                            PickResultTextSource.OCR -> ocrTextHolder.value.orEmpty()
                        }
                    },
                    onTextExpandedChange = { textExpandedHolder.value = it },
                    onImageExpandedChange = { imageExpandedHolder.value = it },
                    onTextModeChange = { mode ->
                        textModeHolder.value = mode
                        updateWindowFocusableForMode(mode)
                    },
                    onDismiss = {
                        if (FloatBallTranslatePanel.isShowing) {
                            FloatBallTranslatePanel.dismiss()
                        } else {
                            dismiss()
                        }
                    },
                    onTextChange = { textHolder.value = it },
                    onCopy = { value ->
                        FloatBallTextPick.copyText(context, value)
                        Toast.makeText(context, R.string.float_ball_text_copied, Toast.LENGTH_SHORT).show()
                    },
                    onSearch = { FloatBallTextPick.searchText(context, it) },
                    onShareText = { FloatBallTextPick.shareText(context, it) },
                    onPaste = {
                        val pasted = FloatBallTextPick.readClipboardText(context)
                        if (pasted == null) {
                            Toast.makeText(context, R.string.float_ball_paste_empty, Toast.LENGTH_SHORT).show()
                        } else {
                            textHolder.value = pasted
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
    imageExpanded: Boolean,
    textMode: PickResultTextMode,
    textSource: PickResultTextSource,
    ocrAvailable: Boolean,
    ocrLoading: Boolean,
    translatePickPanelTransparency: Float,
    textSizeSp: Float,
    onTextSourceChange: (PickResultTextSource) -> Unit,
    onTextExpandedChange: (Boolean) -> Unit,
    onImageExpandedChange: (Boolean) -> Unit,
    onTextModeChange: (PickResultTextMode) -> Unit,
    onDismiss: () -> Unit,
    onTextChange: (String) -> Unit,
    onCopy: (String) -> Unit,
    onSearch: (String) -> Unit,
    onShareText: (String) -> Unit,
    onPaste: () -> Unit,
    onTranslate: (String) -> Unit,
    onRemoveSpaces: (String, removeAll: Boolean) -> Unit,
    onSaveScreenshot: () -> Unit,
    onShareScreenshot: () -> Unit,
) {
    val hasTextSection = ocrLoading || !text.isNullOrBlank() || screenshot != null || ocrAvailable
    val hasImageSection = screenshot != null
    val translateVisible by FloatBallTranslatePanel.panelVisible
    val pickPanelAlpha = if (translateVisible) {
        1f - translatePickPanelTransparency.coerceIn(0f, 1f)
    } else {
        1f
    }

    val maxPanelHeight = (LocalConfiguration.current.screenHeightDp * PANEL_MAX_HEIGHT_FRACTION).dp
    val bodyScrollState = rememberScrollState()

    val dismissInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }

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
                    .padding(PANEL_HORIZONTAL_PADDING)
                    .widthIn(max = PANEL_MAX_WIDTH)
                    .heightIn(max = maxPanelHeight)
                    .graphicsLayer { alpha = pickPanelAlpha }
                    .pickResultPanelCard()
                    .then(
                        if (translateVisible) {
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
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(
                            bodyScrollState,
                            enabled = textMode != PickResultTextMode.WORD_TAP,
                        ),
                ) {
                if (hasTextSection) {
                    PickResultSectionHeader(
                        title = stringResource(R.string.float_ball_pick_result_text_section),
                        expanded = textExpanded,
                        onToggle = { onTextExpandedChange(!textExpanded) },
                    )
                    if (textExpanded) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PickResultInteractiveTextSection(
                                text = text.orEmpty(),
                                textMode = textMode,
                                onTextModeChange = onTextModeChange,
                                onTextChange = onTextChange,
                                textSizeSp = textSizeSp,
                                textSource = textSource,
                                ocrAvailable = ocrAvailable,
                                ocrLoading = ocrLoading,
                                onTextSourceChange = onTextSourceChange,
                                onSearch = onSearch,
                                onShare = onShareText,
                                onCopy = onCopy,
                                onPaste = onPaste,
                                onTranslate = onTranslate,
                                onRemoveSpaces = onRemoveSpaces,
                            )
                        }
                    }
                }

                if (hasTextSection && hasImageSection) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }

                if (hasImageSection) {
                    PickResultSectionHeader(
                        title = stringResource(R.string.float_ball_pick_result_image_section),
                        expanded = imageExpanded,
                        onToggle = { onImageExpandedChange(!imageExpanded) },
                    )
                    if (imageExpanded) {
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
                                PickResultImageActionBar(
                                    onSave = onSaveScreenshot,
                                    onShare = onShareScreenshot,
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun PickResultImageActionBar(
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PickResultToolbarIcon(Icons.Default.Save, enabled = true, onClick = onSave)
        Spacer(modifier = Modifier.size(32.dp))
        PickResultToolbarIcon(Icons.Default.Share, enabled = true, onClick = onShare)
    }
}
