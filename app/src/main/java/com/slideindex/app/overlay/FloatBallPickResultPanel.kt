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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.pickresult.PickResultSelectableText
import com.slideindex.app.overlay.pickresult.PickResultTextMode
import com.slideindex.app.overlay.pickresult.PickResultWordTapBody
import com.slideindex.app.overlay.pickresult.PickResultWordTokenizer
import com.slideindex.app.ui.theme.SlideIndexTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PANEL_HORIZONTAL_PADDING = 12.dp
private val PANEL_MAX_WIDTH = 340.dp
private val PANEL_MAX_HEIGHT_FRACTION = 0.85f
private val PANEL_MAX_TEXT_HEIGHT = 180.dp
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

    private var loadingState: MutableState<Boolean>? = null
    private var textState: MutableState<String?>? = null
    private var screenshotState: MutableState<Bitmap?>? = null
    private var textExpandedState: MutableState<Boolean>? = null
    private var imageExpandedState: MutableState<Boolean>? = null
    private var textModeState: MutableState<PickResultTextMode>? = null
    private var a11yTextState: MutableState<String?>? = null
    private var ocrTextState: MutableState<String?>? = null
    private var textSourceState: MutableState<PickResultTextSource>? = null
    private var ocrAvailableState: MutableState<Boolean>? = null
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

    fun showLoading(context: Context, anchorX: Float = 0f, anchorY: Float = 0f) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showLoading(context, anchorX, anchorY) }
            return
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context.applicationContext
        ensureWindow(hostContext)
        loadingState?.value = true
        textState?.value = null
        screenshotState?.value?.recycle()
        screenshotState?.value = null
        textExpandedState?.value = true
        imageExpandedState?.value = true
        textModeState?.value = PickResultTextMode.WORD_TAP
        a11yTextState?.value = null
        ocrTextState?.value = null
        textSourceState?.value = PickResultTextSource.A11Y
        ocrAvailableState?.value = false
        updateWindowFocusable(focusable = false)
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
        loadingState?.value = false
        a11yTextState?.value = result.a11yText
        ocrTextState?.value = result.ocrText
        textSourceState?.value = result.activeSource
        ocrAvailableState?.value = result.canToggleSource()
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
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        screenshotState?.value?.recycle()
        screenshotState?.value = null
        val wm = windowManager
        composeView?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        screenOffReceiver?.let { receiver ->
            appContext?.let { ctx -> runCatching { ctx.unregisterReceiver(receiver) } }
        }
        OverlayCompose.disposeComposeView(composeView)
        owner?.destroy()
        owner = null
        composeView = null
        layoutParams = null
        windowManager = null
        loadingState = null
        textState = null
        screenshotState = null
        textExpandedState = null
        imageExpandedState = null
        textModeState = null
        a11yTextState = null
        ocrTextState = null
        textSourceState = null
        ocrAvailableState = null
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

        val loadingHolder = mutableStateOf(true)
        val textHolder = mutableStateOf<String?>(null)
        val screenshotHolder = mutableStateOf<Bitmap?>(null)
        val textExpandedHolder = mutableStateOf(true)
        val imageExpandedHolder = mutableStateOf(true)
        val textModeHolder = mutableStateOf(PickResultTextMode.WORD_TAP)
        val a11yTextHolder = mutableStateOf<String?>(null)
        val ocrTextHolder = mutableStateOf<String?>(null)
        val textSourceHolder = mutableStateOf(PickResultTextSource.A11Y)
        val ocrAvailableHolder = mutableStateOf(false)
        loadingState = loadingHolder
        textState = textHolder
        screenshotState = screenshotHolder
        textExpandedState = textExpandedHolder
        imageExpandedState = imageExpandedHolder
        textModeState = textModeHolder
        a11yTextState = a11yTextHolder
        ocrTextState = ocrTextHolder
        textSourceState = textSourceHolder
        ocrAvailableState = ocrAvailableHolder

        val dialogOwner = OverlayComposeOwner()
        val overlayContext = OverlayCompose.themedContext(context)
        val compose = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            setContent {
                val loading by loadingHolder
                val text by textHolder
                val screenshot by screenshotHolder
                val textExpanded by textExpandedHolder
                val imageExpanded by imageExpandedHolder
                val textMode by textModeHolder
                val a11yText by a11yTextHolder
                val ocrText by ocrTextHolder
                val textSource by textSourceHolder
                val ocrAvailable by ocrAvailableHolder
                FloatBallPickResultContent(
                    loading = loading,
                    text = text,
                    screenshot = screenshot,
                    textExpanded = textExpanded,
                    imageExpanded = imageExpanded,
                    textMode = textMode,
                    textSource = textSource,
                    ocrAvailable = ocrAvailable,
                    onTextSourceChange = { source ->
                        textSourceHolder.value = source
                        textHolder.value = when (source) {
                            PickResultTextSource.A11Y -> a11yTextHolder.value
                                ?.takeIf { it.isNotBlank() } ?: ocrTextHolder.value
                            PickResultTextSource.OCR -> ocrTextHolder.value
                                ?.takeIf { it.isNotBlank() } ?: a11yTextHolder.value
                        }
                    },
                    onTextExpandedChange = { textExpandedHolder.value = it },
                    onImageExpandedChange = { imageExpandedHolder.value = it },
                    onTextModeChange = { mode ->
                        textModeHolder.value = mode
                        updateWindowFocusableForMode(mode)
                    },
                    onDismiss = { dismiss() },
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
                    onTranslate = { FloatBallTextPick.translateText(context, it) },
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
    loading: Boolean,
    text: String?,
    screenshot: Bitmap?,
    textExpanded: Boolean,
    imageExpanded: Boolean,
    textMode: PickResultTextMode,
    textSource: PickResultTextSource,
    ocrAvailable: Boolean,
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
    val hasTextSection = loading || !text.isNullOrBlank() || screenshot != null
    val hasImageSection = screenshot != null
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text.orEmpty())) }
    var selectedWordIndices by remember(text) { mutableStateOf(setOf<Int>()) }
    var selectionStart by remember(text) { mutableStateOf(0) }
    var selectionEnd by remember(text) { mutableStateOf(0) }
    var selectAllRequest by remember { mutableStateOf(0) }
    var deselectAllRequest by remember { mutableStateOf(0) }
    val appContext = LocalContext.current.applicationContext
    var wordTokens by remember(text) { mutableStateOf<List<String>>(emptyList()) }
    var wordTokenOverride by remember(text) { mutableStateOf<List<String>?>(null) }
    val effectiveWordTokens = wordTokenOverride ?: wordTokens

    LaunchedEffect(text) {
        wordTokenOverride = null
        val source = text.orEmpty()
        wordTokens = if (source.isBlank()) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                PickResultWordTokenizer.tokenizeSelectableWords(source, appContext)
            }
        }
    }

    LaunchedEffect(text, textMode) {
        if (textMode != PickResultTextMode.EDIT) {
            textFieldValue = TextFieldValue(text.orEmpty())
            selectedWordIndices = emptySet()
            selectionStart = 0
            selectionEnd = 0
        } else if (text.orEmpty() != textFieldValue.text) {
            textFieldValue = TextFieldValue(text.orEmpty())
        }
    }

    val allSelected = when (textMode) {
        PickResultTextMode.WORD_TAP -> {
            effectiveWordTokens.isNotEmpty() && selectedWordIndices.size == effectiveWordTokens.size
        }
        PickResultTextMode.SELECT -> {
            val length = text.orEmpty().length
            length > 0 && selectionStart == 0 && selectionEnd == length
        }
        PickResultTextMode.EDIT -> {
            val length = textFieldValue.text.length
            length > 0 &&
                textFieldValue.selection.min == 0 &&
                textFieldValue.selection.max == length
        }
    }

    fun activeText(): String? {
        if (text.isNullOrBlank()) return null
        return when (textMode) {
            PickResultTextMode.WORD_TAP -> {
                if (selectedWordIndices.isEmpty()) text
                else selectedWordIndices.sorted().joinToString(separator = "") { index ->
                    effectiveWordTokens.getOrElse(index) { "" }.trim()
                }.ifBlank { text }
            }
            PickResultTextMode.SELECT -> {
                if (selectionEnd > selectionStart) {
                    text.orEmpty().substring(
                        selectionStart.coerceAtLeast(0),
                        selectionEnd.coerceAtMost(text.length),
                    )
                } else {
                    text
                }
            }
            PickResultTextMode.EDIT -> {
                val selection = textFieldValue.selection
                if (!selection.collapsed) {
                    textFieldValue.text.substring(
                        selection.min.coerceAtLeast(0),
                        selection.max.coerceAtMost(textFieldValue.text.length),
                    )
                } else {
                    textFieldValue.text.ifBlank { text }
                }
            }
        }
    }

    fun runOnActiveText(action: (String) -> Unit) {
        activeText()?.takeIf { it.isNotBlank() }?.let(action)
    }

    val maxPanelHeight = (LocalConfiguration.current.screenHeightDp * PANEL_MAX_HEIGHT_FRACTION).dp
    val bodyScrollState = rememberScrollState()

    SlideIndexTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(PANEL_HORIZONTAL_PADDING)
                    .widthIn(max = PANEL_MAX_WIDTH)
                    .heightIn(max = maxPanelHeight)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
                    .clickable(enabled = false) {}
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
                            if (loading) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Text(
                                        text = stringResource(R.string.float_ball_recognizing),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            } else if (!text.isNullOrBlank()) {
                                PickResultTextToolbar(
                                    textMode = textMode,
                                    allSelected = allSelected,
                                    activeSource = textSource,
                                    ocrAvailable = ocrAvailable,
                                    onSourceChange = onTextSourceChange,
                                    onEditToggle = {
                                        val next = if (textMode == PickResultTextMode.EDIT) {
                                            PickResultTextMode.SELECT
                                        } else {
                                            PickResultTextMode.EDIT
                                        }
                                        selectedWordIndices = emptySet()
                                        onTextModeChange(next)
                                    },
                                    onWordSelectToggle = {
                                        val next = if (textMode == PickResultTextMode.WORD_TAP) {
                                            PickResultTextMode.SELECT
                                        } else {
                                            PickResultTextMode.WORD_TAP
                                        }
                                        selectedWordIndices = emptySet()
                                        onTextModeChange(next)
                                    },
                                    onTrimSpaces = {
                                        if (!text.isNullOrBlank()) onRemoveSpaces(text, false)
                                    },
                                    onRemoveAllSpaces = {
                                        if (!text.isNullOrBlank()) onRemoveSpaces(text, true)
                                    },
                                    onSelectAll = {
                                        when (textMode) {
                                            PickResultTextMode.WORD_TAP -> {
                                                selectedWordIndices = if (allSelected) {
                                                    emptySet()
                                                } else {
                                                    effectiveWordTokens.indices.toSet()
                                                }
                                            }
                                            PickResultTextMode.SELECT -> {
                                                val length = text.orEmpty().length
                                                if (allSelected) {
                                                    deselectAllRequest++
                                                    selectionStart = 0
                                                    selectionEnd = 0
                                                } else {
                                                    selectAllRequest++
                                                    selectionStart = 0
                                                    selectionEnd = length
                                                }
                                            }
                                            PickResultTextMode.EDIT -> {
                                                textFieldValue = if (allSelected) {
                                                    textFieldValue.copy(
                                                        selection = TextRange(textFieldValue.text.length),
                                                    )
                                                } else {
                                                    textFieldValue.copy(
                                                        selection = TextRange(0, textFieldValue.text.length),
                                                    )
                                                }
                                            }
                                        }
                                    },
                                )
                                PickResultTextBody(
                                    textMode = textMode,
                                    textFieldValue = textFieldValue,
                                    wordTokens = effectiveWordTokens,
                                    selectedWordIndices = selectedWordIndices,
                                    selectAllRequest = selectAllRequest,
                                    onTextFieldValueChange = { updated ->
                                        textFieldValue = updated
                                        onTextChange(updated.text)
                                    },
                                    onSelectionChanged = { start, end ->
                                        selectionStart = start
                                        selectionEnd = end
                                    },
                                    onWordSelectionChange = { selectedWordIndices = it },
                                    deselectAllRequest = deselectAllRequest,
                                )
                                PickResultTextActionBar(
                                    enabled = true,
                                    splitSelectedEnabled = textMode == PickResultTextMode.WORD_TAP &&
                                        selectedWordIndices.isNotEmpty(),
                                    onSearch = { runOnActiveText(onSearch) },
                                    onShare = { runOnActiveText(onShareText) },
                                    onCopy = { runOnActiveText(onCopy) },
                                    onPaste = onPaste,
                                    onTranslate = { runOnActiveText(onTranslate) },
                                    onRemoveSpaces = { runOnActiveText { onRemoveSpaces(it, true) } },
                                    onSplitSelectedWords = {
                                        val split = PickResultWordTokenizer.splitSelectedTokensToChars(
                                            tokens = effectiveWordTokens,
                                            selectedIndices = selectedWordIndices,
                                        )
                                        if (split != null) {
                                            wordTokenOverride = split.tokens
                                            selectedWordIndices = split.selectedIndices
                                        }
                                    },
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.float_ball_text_not_found),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
private fun PickResultTextToolbar(
    textMode: PickResultTextMode,
    allSelected: Boolean,
    activeSource: PickResultTextSource,
    ocrAvailable: Boolean,
    onSourceChange: (PickResultTextSource) -> Unit,
    onEditToggle: () -> Unit,
    onWordSelectToggle: () -> Unit,
    onTrimSpaces: () -> Unit,
    onRemoveAllSpaces: () -> Unit,
    onSelectAll: () -> Unit,
) {
    val selectAllDescription = if (allSelected) {
        stringResource(R.string.float_ball_action_deselect_all)
    } else {
        stringResource(R.string.float_ball_action_select_all)
    }
    val selectAllIcon = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ocrAvailable) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PickResultSourceChip(
                    label = stringResource(R.string.float_ball_pick_source_a11y),
                    selected = activeSource == PickResultTextSource.A11Y,
                    compact = true,
                    onClick = { onSourceChange(PickResultTextSource.A11Y) },
                )
                PickResultSourceChip(
                    label = stringResource(R.string.float_ball_pick_source_ocr),
                    selected = activeSource == PickResultTextSource.OCR,
                    compact = true,
                    onClick = { onSourceChange(PickResultTextSource.OCR) },
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PickResultTitleIcon(
                icon = Icons.Default.Edit,
                selected = textMode == PickResultTextMode.EDIT,
                contentDescription = stringResource(R.string.float_ball_action_edit),
                onClick = onEditToggle,
            )
            PickResultTitleIcon(
                icon = Icons.Default.ViewModule,
                selected = textMode == PickResultTextMode.WORD_TAP,
                contentDescription = stringResource(R.string.float_ball_action_word_select),
                onClick = onWordSelectToggle,
            )
            PickResultTitleIcon(
                icon = Icons.Default.UnfoldLess,
                selected = false,
                contentDescription = stringResource(R.string.float_ball_action_trim_spaces),
                iconModifier = Modifier.rotate(90f),
                onClick = onTrimSpaces,
                onLongClick = onRemoveAllSpaces,
            )
            PickResultTitleIcon(
                icon = selectAllIcon,
                selected = allSelected,
                contentDescription = selectAllDescription,
                onClick = onSelectAll,
            )
        }
    }
}

@Composable
private fun PickResultSourceChip(
    label: String,
    selected: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val chipStyle = if (compact) {
        MaterialTheme.typography.labelMedium.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    } else {
        MaterialTheme.typography.labelLarge
    }
    val shape = RoundedCornerShape(if (compact) 6.dp else 8.dp)
    val chipModifier = Modifier
        .clip(shape)
        .background(background)
        .clickable(onClick = onClick)
    if (compact) {
        Box(
            modifier = chipModifier
                .height(28.dp)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = chipStyle,
                color = contentColor,
            )
        }
    } else {
        Text(
            text = label,
            modifier = chipModifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = chipStyle,
            color = contentColor,
        )
    }
}

@Composable
private fun PickResultTitleIcon(
    icon: ImageVector,
    selected: Boolean,
    contentDescription: String,
    iconModifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconContent: @Composable () -> Unit = {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = iconModifier.size(18.dp),
        )
    }
    if (onLongClick != null) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            iconContent()
        }
    } else {
        IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
            iconContent()
        }
    }
}

@Composable
private fun PickResultTextBody(
    textMode: PickResultTextMode,
    textFieldValue: TextFieldValue,
    wordTokens: List<String>,
    selectedWordIndices: Set<Int>,
    selectAllRequest: Int,
    deselectAllRequest: Int,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    onSelectionChanged: (start: Int, end: Int) -> Unit,
    onWordSelectionChange: (Set<Int>) -> Unit,
) {
    val scrollState = rememberScrollState()
    val paddedModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(4.dp))

    when (textMode) {
        PickResultTextMode.EDIT -> {
            BasicTextField(
                value = textFieldValue,
                onValueChange = onTextFieldValueChange,
                modifier = paddedModifier
                    .heightIn(max = PANEL_MAX_TEXT_HEIGHT)
                    .verticalScroll(scrollState),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
        }
        PickResultTextMode.WORD_TAP -> {
            PickResultWordTapBody(
                wordTokens = wordTokens,
                selectedWordIndices = selectedWordIndices,
                onSelectionChange = onWordSelectionChange,
                maxHeight = PANEL_MAX_TEXT_HEIGHT,
                modifier = paddedModifier,
            )
        }
        PickResultTextMode.SELECT -> {
            PickResultSelectableText(
                text = textFieldValue.text,
                maxHeight = PANEL_MAX_TEXT_HEIGHT,
                modifier = paddedModifier,
                selectAllRequest = selectAllRequest,
                deselectAllRequest = deselectAllRequest,
                onSelectionChanged = onSelectionChanged,
            )
        }
    }
}

@Composable
private fun PickResultSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun PickResultTextActionBar(
    enabled: Boolean,
    splitSelectedEnabled: Boolean,
    onSearch: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onTranslate: () -> Unit,
    onRemoveSpaces: () -> Unit,
    onSplitSelectedWords: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PickResultToolbarIcon(Icons.Default.Search, enabled, onSearch)
        PickResultToolbarIcon(Icons.Default.Share, enabled, onShare)
        PickResultToolbarIcon(Icons.Default.ContentCopy, enabled, onCopy)
        PickResultToolbarIcon(Icons.Default.ContentPaste, enabled, onPaste)
        PickResultToolbarIcon(Icons.Default.Translate, enabled, onTranslate)
        Box {
            PickResultToolbarIcon(Icons.Default.MoreVert, enabled) { menuExpanded = true }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.float_ball_menu_remove_spaces)) },
                    onClick = {
                        menuExpanded = false
                        onRemoveSpaces()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.float_ball_menu_split_selected)) },
                    onClick = {
                        menuExpanded = false
                        onSplitSelectedWords()
                    },
                    enabled = enabled && splitSelectedEnabled,
                )
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

@Composable
private fun PickResultToolbarIcon(
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}
