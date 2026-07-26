package com.slideindex.app.overlay.pickresult

import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.barcode.BarcodeScanResult
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.FloatBallTextPick
import com.slideindex.app.overlay.OverlaySelectionToolbarActions
import com.slideindex.app.overlay.OverlaySelectionToolbarOverlay
import com.slideindex.app.overlay.cutTextFieldValue
import com.slideindex.app.overlay.pasteIntoTextFieldValue
import com.slideindex.app.overlay.rememberOverlaySelectionToolbarState
import com.slideindex.app.overlay.suppressSystemTextContextMenu
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.HapticHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

private val pickResultTokenCache = android.util.LruCache<String, List<String>>(20)

@Composable
private fun PickResultEditModeBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    val view = LocalView.current
    val currentOnBack by rememberUpdatedState(onBack)
    DisposableEffect(enabled, view) {
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return@DisposableEffect onDispose {}
        }
        val callback = OnBackInvokedCallback { currentOnBack() }
        view.findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback,
        )
        onDispose {
            view.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(callback)
        }
    }
}

@Composable
internal fun PickResultInteractiveTextSection(
    text: String,
    textMode: PickResultTextMode,
    onTextModeChange: (PickResultTextMode) -> Unit,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 15f,
    textSource: PickResultTextSource = PickResultTextSource.A11Y,
    ocrAvailable: Boolean = false,
    ocrLoading: Boolean = false,
    showBackgroundOcrAction: Boolean = false,
    onBackgroundOcr: () -> Unit = {},
    onTextSourceChange: (PickResultTextSource) -> Unit = {},
    showSourceChips: Boolean = true,
    a11yAvailable: Boolean = true,
    barcodeResults: List<BarcodeScanResult> = emptyList(),
    showingTranslation: Boolean = false,
    translateLoading: Boolean = false,
    showEditingToolbar: Boolean = true,
    showActionBar: Boolean = true,
    sectionTitle: String? = null,
    pinActionBarOutside: Boolean = false,
    expandTextBlock: Boolean = false,
    auxiliaryDragEnabled: Boolean = false,
    bodyMaxHeight: Dp? = null,
    showSearch: Boolean = false,
    translateEnabled: Boolean = true,
    onActiveTextChange: (String) -> Unit = {},
    onSearch: (String) -> Unit = {},
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onRemoveSpaces: (String, removeAll: Boolean) -> Unit,
    onZoomText: ((Boolean) -> Unit)? = null,
    onToolbarDragDelta: (dragAmount: Float) -> Unit = {},
    onActionBarDragDelta: (dragAmount: Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onSearchDragEnd: () -> Unit = onDragEnd,
    onPinToScreen: (() -> Unit)? = null,
    onStash: (() -> Unit)? = null,
    actionBarBottomPadding: Dp = PickResultTextActionBarBottomPaddingWhenAlone,
    actionBarDragActive: Boolean = false,
) {
    // 勿用 remember(text)：编辑时 onTextChange 会回写 text，key 变化会把光标重置到 0。
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text)) }
    var selectedWordIndices by remember { mutableStateOf(setOf<Int>()) }
    var selectionStart by remember { mutableIntStateOf(0) }
    var selectionEnd by remember { mutableIntStateOf(0) }
    var selectAllRequest by remember { mutableIntStateOf(0) }
    var deselectAllRequest by remember { mutableIntStateOf(0) }
    val appContext = LocalContext.current.applicationContext
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var appSettings by remember { mutableStateOf(AppSettings()) }
    LaunchedEffect(appContext) {
        OverlayDependencyAccess.overlayDependencies(appContext)
            ?.settingsRepository
            ?.settings
            ?.collect { appSettings = it }
    }
    var wordTokens by remember(text) { 
        mutableStateOf<List<String>>(pickResultTokenCache.get(text) ?: emptyList()) 
    }
    var wordTokenOverride by remember(text) { mutableStateOf<List<String>?>(null) }
    val effectiveWordTokens = wordTokenOverride ?: wordTokens

    fun currentWordTokens(): List<String> = wordTokenOverride ?: wordTokens

    LaunchedEffect(text) {
        wordTokenOverride = null
        if (text.isNotBlank() && pickResultTokenCache.get(text) == null) {
            val newTokens = withContext(Dispatchers.Default) {
                PickResultWordTokenizer.tokenizeSelectableWords(text, appContext)
            }
            pickResultTokenCache.put(text, newTokens)
            wordTokens = newTokens
        }
    }

    LaunchedEffect(text, textMode) {
        if (textMode != PickResultTextMode.EDIT) {
            textFieldValue = TextFieldValue(text)
            selectedWordIndices = emptySet()
            selectionStart = 0
            selectionEnd = 0
        } else if (text != textFieldValue.text) {
            textFieldValue = TextFieldValue(text)
        }
    }

    val allSelected = when (textMode) {
        PickResultTextMode.WORD_TAP -> {
            effectiveWordTokens.isNotEmpty() && selectedWordIndices.size == effectiveWordTokens.size
        }
        PickResultTextMode.SELECT -> {
            val length = text.length
            length > 0 && selectionStart == 0 && selectionEnd == length
        }
        PickResultTextMode.EDIT -> {
            val length = textFieldValue.text.length
            length > 0 &&
                textFieldValue.selection.min == 0 &&
                textFieldValue.selection.max == length
        }
    }

    fun activeText(): String {
        return when (textMode) {
            PickResultTextMode.WORD_TAP -> {
                if (selectedWordIndices.isEmpty()) text
                else selectedWordIndices.sorted().joinToString(separator = "") { index ->
                    effectiveWordTokens.getOrElse(index) { "" }.trim()
                }.ifBlank { text }
            }
            PickResultTextMode.SELECT -> {
                if (selectionEnd > selectionStart) {
                    text.substring(
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
        activeText().takeIf { it.isNotBlank() }?.let(action)
    }

    fun hasActiveSelection(): Boolean = when (textMode) {
        PickResultTextMode.WORD_TAP -> selectedWordIndices.isNotEmpty()
        PickResultTextMode.SELECT -> selectionEnd > selectionStart
        PickResultTextMode.EDIT -> !textFieldValue.selection.collapsed
    }

    LaunchedEffect(
        text,
        textMode,
        selectedWordIndices,
        selectionStart,
        selectionEnd,
        textFieldValue,
        effectiveWordTokens,
    ) {
        onActiveTextChange(activeText())
    }

    fun splitWordAt(index: Int) {
        val tokens = currentWordTokens()
        val split = PickResultWordTokenizer.splitTokenAtIndex(
            tokens = tokens,
            index = index,
        )
        if (split != null) {
            HapticHelper.appTick(view, appSettings)
            wordTokenOverride = split.tokens
            selectedWordIndices = PickResultWordTokenizer.mergeSelectionAfterSplitAt(
                splitIndex = index,
                expandedTokenCount = split.tokens.size - tokens.size + 1,
                oldSelected = selectedWordIndices,
                splitCharSelected = split.selectedIndices,
            )
        }
    }

    fun exitEditMode() {
        keyboardController?.hide()
        focusManager.clearFocus()
        selectedWordIndices = emptySet()
        onTextModeChange(PickResultTextMode.WORD_TAP)
        view.post { view.requestFocus() }
    }

    PickResultEditModeBackHandler(
        enabled = textMode == PickResultTextMode.EDIT,
        onBack = ::exitEditMode,
    )

    val isEditMode = textMode == PickResultTextMode.EDIT
    val showTopToolbar = showSourceChips || showEditingToolbar || sectionTitle != null

    val bodyScrollState = rememberScrollState()
    val showOcrLoading = ocrLoading &&
        textSource == PickResultTextSource.OCR &&
        text.isBlank() &&
        !showingTranslation
    var openLinkChooserExpanded by remember { mutableStateOf(false) }
    val selectionToolbarActions = remember(
        textMode,
        showSearch,
        translateEnabled,
    ) {
        OverlaySelectionToolbarActions(
            editable = textMode == PickResultTextMode.EDIT,
            showSearch = showSearch,
            showShare = true,
            showTranslate = translateEnabled,
            onCopy = { copied -> onCopy(copied) },
            onSearch = { query -> onSearch(query) },
            onShare = { value -> onShare(value) },
            onTranslate = { value -> onTranslate(value) },
        )
    }
    val openLinkAction = remember(
        text,
        textMode,
        selectedWordIndices,
        selectionStart,
        selectionEnd,
        textFieldValue,
    ) {
        PickResultUrl.resolveOpenLinkAction(
            fullText = text,
            activeText = activeText(),
            hasSelection = hasActiveSelection(),
        )
    }
    val openLinkChoices = remember(openLinkAction) {
        when (openLinkAction) {
            is PickResultOpenLinkAction.Choose -> openLinkAction.urls
            else -> emptyList()
        }
    }
    LaunchedEffect(openLinkAction) {
        openLinkChooserExpanded = false
    }
    val actionBar: @Composable () -> Unit = {
        if (showActionBar) {
            val actionBarContent: @Composable () -> Unit = {
                PickResultTextActionBar(
                    enabled = text.isNotBlank() || barcodeResults.isNotEmpty(),
                    translateEnabled = translateEnabled,
                    translateSelected = showingTranslation,
                    showSearch = showSearch,
                    showOpenLink = openLinkAction != null,
                    openLinkChooserExpanded = openLinkChooserExpanded,
                    openLinkChoices = openLinkChoices,
                    onSearch = { runOnActiveText(onSearch) },
                    onOpenLink = {
                        when (val action = openLinkAction) {
                            is PickResultOpenLinkAction.Open -> {
                                FloatBallTextPick.openUrl(appContext, action.url, appSettings)
                            }
                            is PickResultOpenLinkAction.Choose -> {
                                openLinkChooserExpanded = true
                            }
                            null -> Unit
                        }
                    },
                    onOpenLinkChoice = { url ->
                        FloatBallTextPick.openUrl(appContext, url, appSettings)
                    },
                    onDismissOpenLinkChooser = { openLinkChooserExpanded = false },
                    onShare = { runOnActiveText(onShare) },
                    onCopy = { runOnActiveText(onCopy) },
                    onTranslate = { runOnActiveText(onTranslate) },
                    onPinToScreen = onPinToScreen,
                    onStash = onStash,
                    bottomPadding = actionBarBottomPadding,
                    lightweightDrag = actionBarDragActive,
                )
            }
            if (pinActionBarOutside) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = PickResultTextActionBarReservedHeight +
                                PickResultTextBodyActionBarSpacing,
                        )
                        .pickResultLinkedVerticalDrag(
                            onDragDelta = onActionBarDragDelta,
                            onDragEnd = onSearchDragEnd,
                        ),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    actionBarContent()
                }
            } else {
                actionBarContent()
            }
        }
    }

    Column(
        modifier = if (expandTextBlock && pinActionBarOutside) {
            modifier.fillMaxSize()
        } else {
            modifier
        },
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (showTopToolbar) {
            if (auxiliaryDragEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = PickResultTextSectionToolbarReservedHeight +
                                PickResultTextToolbarBodySpacing,
                        )
                        .pickResultLinkedVerticalDrag(
                            onDragDelta = onToolbarDragDelta,
                            onDragEnd = onDragEnd,
                        ),
                    verticalArrangement = Arrangement.Center,
                ) {
                    PickResultTextToolbar(
                        textMode = textMode,
                        allSelected = allSelected,
                        activeSource = textSource,
                        ocrAvailable = ocrAvailable,
                        ocrLoading = ocrLoading,
                        a11yAvailable = a11yAvailable,
                        barcodeResults = barcodeResults,
                        showSourceChips = showSourceChips,
                        showEditingToolbar = showEditingToolbar,
                        sectionTitle = sectionTitle,
                        onSourceChange = onTextSourceChange,
                        onEditToggle = {
                            if (textMode == PickResultTextMode.EDIT) {
                                exitEditMode()
                            } else {
                                selectedWordIndices = emptySet()
                                onTextModeChange(PickResultTextMode.EDIT)
                            }
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
                        onTrimSpaces = { onRemoveSpaces(text, false) },
                        onRemoveAllSpaces = { onRemoveSpaces(text, true) },
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
                                    val length = text.length
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
                    Spacer(modifier = Modifier.height(PickResultTextToolbarBodySpacing))
                }
            } else {
                Box(
                    modifier = Modifier.padding(bottom = PickResultTextToolbarBodySpacing),
                ) {
                    PickResultTextToolbar(
                        textMode = textMode,
                        allSelected = allSelected,
                        activeSource = textSource,
                        ocrAvailable = ocrAvailable,
                        ocrLoading = ocrLoading,
                        a11yAvailable = a11yAvailable,
                        barcodeResults = barcodeResults,
                        showSourceChips = showSourceChips,
                        showEditingToolbar = showEditingToolbar,
                        sectionTitle = sectionTitle,
                        onSourceChange = onTextSourceChange,
                        onEditToggle = {
                            if (textMode == PickResultTextMode.EDIT) {
                                exitEditMode()
                            } else {
                                selectedWordIndices = emptySet()
                                onTextModeChange(PickResultTextMode.EDIT)
                            }
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
                        onTrimSpaces = { onRemoveSpaces(text, false) },
                        onRemoveAllSpaces = { onRemoveSpaces(text, true) },
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
                                    val length = text.length
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
                }
            }
        }
        if (pinActionBarOutside) {
            val maxBodyHeight = bodyMaxHeight ?: pickResultMaxTextHeight(textSizeSp)
            val bodyScrollEnabled = textMode != PickResultTextMode.WORD_TAP
            val fillTextBlock = expandTextBlock
            val bodyContent: @Composable (expandToFill: Boolean, constrainedHeight: Dp) -> Unit =
                { expandToFill, constrainedHeight ->
                when {
                    showOcrLoading -> {
                        PickResultOcrLoadingBody(
                            showBackgroundAction = showBackgroundOcrAction,
                            onBackgroundProcess = onBackgroundOcr,
                        )
                    }
                    translateLoading -> {
                        PickResultTranslateLoadingBody()
                    }
                    else -> {
                        PickResultTextBody(
                            textMode = textMode,
                            textFieldValue = textFieldValue,
                            wordTokens = effectiveWordTokens,
                            selectedWordIndices = selectedWordIndices,
                            selectAllRequest = selectAllRequest,
                            deselectAllRequest = deselectAllRequest,
                            textSizeSp = textSizeSp,
                            bodyMaxHeight = if (fillTextBlock) constrainedHeight else maxBodyHeight,
                            expandToFill = expandToFill,
                            useInternalScroll = true,
                            onTextFieldValueChange = { updated ->
                                textFieldValue = updated
                                onTextChange(updated.text)
                            },
                            onSelectionChanged = { start, end ->
                                selectionStart = start
                                selectionEnd = end
                            },
                            onWordSelectionChange = { selectedWordIndices = it },
                            onWordLongPress = ::splitWordAt,
                            onZoomText = onZoomText,
                            onExitEditMode = ::exitEditMode,
                            selectionToolbarActions = selectionToolbarActions,
                        )
                    }
                }
            }
            if (fillTextBlock) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    val availableHeight = maxHeight
                    if (bodyScrollEnabled) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(bodyScrollState),
                        ) {
                            bodyContent(false, availableHeight)
                        }
                    } else {
                        bodyContent(true, availableHeight)
                    }
                }
                actionBar()
            } else if (bodyScrollEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxBodyHeight)
                        .verticalScroll(bodyScrollState),
                ) {
                    bodyContent(false, maxBodyHeight)
                }
                actionBar()
            } else {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxBodyHeight),
                ) {
                    bodyContent(false, maxHeight)
                }
                actionBar()
            }
        } else {
            when {
                showOcrLoading -> {
                    PickResultOcrLoadingBody(
                        showBackgroundAction = showBackgroundOcrAction,
                        onBackgroundProcess = onBackgroundOcr,
                    )
                }
                translateLoading -> {
                    PickResultTranslateLoadingBody()
                }
                else -> {
                    PickResultTextBody(
                    textMode = textMode,
                    textFieldValue = textFieldValue,
                    wordTokens = effectiveWordTokens,
                    selectedWordIndices = selectedWordIndices,
                    selectAllRequest = selectAllRequest,
                    deselectAllRequest = deselectAllRequest,
                    textSizeSp = textSizeSp,
                    bodyMaxHeight = bodyMaxHeight,
                    useInternalScroll = true,
                    onTextFieldValueChange = { updated ->
                        textFieldValue = updated
                        onTextChange(updated.text)
                    },
                    onSelectionChanged = { start, end ->
                        selectionStart = start
                        selectionEnd = end
                    },
                    onWordSelectionChange = { selectedWordIndices = it },
                    onWordLongPress = ::splitWordAt,
                    onZoomText = onZoomText,
                    onExitEditMode = ::exitEditMode,
                    selectionToolbarActions = selectionToolbarActions,
                )
                }
            }
            actionBar()
        }
    }
}

@Composable
internal fun PickResultTextToolbar(
    textMode: PickResultTextMode,
    allSelected: Boolean,
    activeSource: PickResultTextSource,
    ocrAvailable: Boolean,
    ocrLoading: Boolean = false,
    a11yAvailable: Boolean = true,
    barcodeResults: List<BarcodeScanResult> = emptyList(),
    showSourceChips: Boolean = true,
    showEditingToolbar: Boolean = true,
    sectionTitle: String? = null,
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
    val selectAllIcon = if (allSelected) Icons.Outlined.Deselect else Icons.Outlined.SelectAll
    val horizontalPadding = if (sectionTitle != null) 8.dp else 4.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (sectionTitle != null) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val showA11yChip = a11yAvailable
        val showOcrChip = ocrAvailable || ocrLoading
        val showBarcodeChip = barcodeResults.isNotEmpty()
        val hasSourceChips = showA11yChip || showOcrChip || showBarcodeChip
        if (showSourceChips && hasSourceChips) {
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(22.dp),
                        spotColor = if (androidx.compose.foundation.isSystemInDarkTheme()) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
                    )
                    .background(
                        color = if (androidx.compose.foundation.isSystemInDarkTheme()) androidx.compose.ui.graphics.Color(0xFF2A2A2C) else androidx.compose.ui.graphics.Color(0xFFF2F3F5),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showA11yChip) {
                    PickResultSourceChip(
                        label = stringResource(R.string.float_ball_pick_source_a11y),
                        selected = activeSource == PickResultTextSource.A11Y,
                        enabled = true,
                        compact = true,
                        onClick = { onSourceChange(PickResultTextSource.A11Y) },
                    )
                }
                if (showOcrChip) {
                    PickResultSourceChip(
                        label = stringResource(R.string.float_ball_pick_source_ocr),
                        selected = activeSource == PickResultTextSource.OCR,
                        enabled = ocrAvailable || ocrLoading,
                        compact = true,
                        onClick = {
                            if (ocrAvailable || ocrLoading) onSourceChange(PickResultTextSource.OCR)
                        },
                    )
                }
                if (showBarcodeChip) {
                    PickResultSourceChip(
                        label = stringResource(R.string.float_ball_pick_source_barcode),
                        selected = activeSource == PickResultTextSource.BARCODE,
                        enabled = true,
                        compact = true,
                        onClick = { onSourceChange(PickResultTextSource.BARCODE) },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        if (showEditingToolbar) {
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .background(
                        color = if (androidx.compose.foundation.isSystemInDarkTheme()) androidx.compose.ui.graphics.Color(0xFF3C4043) else androidx.compose.ui.graphics.Color(0xFFE4E5E8),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PickResultTitleIcon(
                    icon = Icons.Outlined.Edit,
                    selected = textMode == PickResultTextMode.EDIT,
                    contentDescription = stringResource(R.string.float_ball_action_edit),
                    onClick = onEditToggle,
                )
                PickResultTitleIcon(
                    icon = Icons.Outlined.ViewModule,
                    selected = textMode == PickResultTextMode.WORD_TAP,
                    contentDescription = stringResource(R.string.float_ball_action_word_select),
                    onClick = onWordSelectToggle,
                )
                Spacer(modifier = Modifier.size(2.dp))
                Box(
                    modifier = Modifier
                        .size(width = 1.dp, height = 14.dp)
                        .align(Alignment.CenterVertically)
                        .background(if (androidx.compose.foundation.isSystemInDarkTheme()) androidx.compose.ui.graphics.Color(0xFF5F6368) else androidx.compose.ui.graphics.Color(0xFFCED6E0))
                )
                Spacer(modifier = Modifier.size(2.dp))
                PickResultTitleIcon(
                    icon = Icons.Outlined.UnfoldLess,
                    selected = false,
                    contentDescription = stringResource(R.string.float_ball_action_trim_spaces),
                    modifier = Modifier.rotate(90f),
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
}

@Composable
private fun PickResultSourceChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val background = when {
        !enabled -> androidx.compose.ui.graphics.Color.Transparent
        selected -> if (isDark) androidx.compose.ui.graphics.Color(0xFF9BA8E6) else androidx.compose.ui.graphics.Color(0xFF8C7AE6)
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val contentColor = when {
        !enabled -> (if (isDark) androidx.compose.ui.graphics.Color(0xFF9AA0A6) else androidx.compose.ui.graphics.Color(0xFF747D8C)).copy(alpha = 0.38f)
        selected -> androidx.compose.ui.graphics.Color.White
        else -> if (isDark) androidx.compose.ui.graphics.Color(0xFFD1D1D6) else androidx.compose.ui.graphics.Color(0xFF2F3542)
    }
    val chipStyle = if (compact) {
        MaterialTheme.typography.labelMedium.copy(
            fontSize = 15.sp,
            lineHeight = 16.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
    } else {
        MaterialTheme.typography.labelLarge
    }
    val shape = RoundedCornerShape(18.dp)
    val chipModifier = Modifier
        .then(
            if (selected) Modifier.shadow(4.dp, shape, spotColor = background)
            else Modifier
        )
        .clip(shape)
        .background(background)
        .clickable(enabled = enabled, onClick = onClick)
    if (compact) {
        Box(
            modifier = chipModifier
                .height(32.dp)
                .padding(horizontal = 16.dp),
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
            modifier = chipModifier
                .fillMaxHeight()
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(horizontal = 12.dp),
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
    modifier: Modifier = Modifier,
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
            modifier = modifier.size(18.dp),
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
internal fun PickResultOcrLoadingBody(
    modifier: Modifier = Modifier,
    showBackgroundAction: Boolean = false,
    onBackgroundProcess: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        if (showBackgroundAction) {
            TextButton(
                onClick = onBackgroundProcess,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.share_image_ocr_background_action))
            }
        }
    }
}

@Composable
internal fun PickResultTranslateLoadingBody(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = stringResource(R.string.float_ball_translating),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun PickResultTextBody(
    textMode: PickResultTextMode,
    textFieldValue: TextFieldValue,
    wordTokens: List<String>,
    selectedWordIndices: Set<Int>,
    selectAllRequest: Int,
    deselectAllRequest: Int,
    textSizeSp: Float,
    bodyMaxHeight: Dp? = null,
    expandToFill: Boolean = false,
    useInternalScroll: Boolean = true,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    onSelectionChanged: (start: Int, end: Int) -> Unit,
    onWordSelectionChange: (Set<Int>) -> Unit,
    onWordLongPress: (Int) -> Unit,
    onZoomText: ((Boolean) -> Unit)? = null,
    onExitEditMode: (() -> Unit)? = null,
    selectionToolbarActions: OverlaySelectionToolbarActions? = null,
) {
    val bodyTextSize = textSizeSp.sp
    val editLineHeight = (textSizeSp * 22f / 15f).sp
    val defaultMaxHeight = pickResultMaxTextHeight(textSizeSp)
    val effectiveMaxHeight = bodyMaxHeight?.let { allocated ->
        (allocated - PickResultTextBodyVerticalPadding).coerceAtLeast(0.dp)
    } ?: defaultMaxHeight
    val scrollState = rememberScrollState()
    val currentOnZoomText by rememberUpdatedState(onZoomText)
    val currentOnExitEditMode by rememberUpdatedState(onExitEditMode)
    val appContext = LocalContext.current
    val density = LocalDensity.current
    val viewportHeightPx = with(density) { effectiveMaxHeight.roundToPx().toFloat() }
    val paddedModifier = Modifier
        .fillMaxWidth()
        .then(
            when {
                expandToFill -> Modifier.fillMaxHeight()
                bodyMaxHeight != null -> Modifier.heightIn(max = bodyMaxHeight)
                else -> Modifier
            },
        )
        .background(
            color = if (androidx.compose.foundation.isSystemInDarkTheme()) androidx.compose.ui.graphics.Color(0xFF171717) else androidx.compose.ui.graphics.Color(0xFFF8F9FA),
            shape = RoundedCornerShape(16.dp)
        )
        .padding(
            start = 14.dp,
            end = 14.dp,
            top = 14.dp,
            bottom = 14.dp,
        )
        .pointerInput(textMode) {
            if (textMode == PickResultTextMode.EDIT || textMode == PickResultTextMode.SELECT) {
                return@pointerInput
            }
            var cumulativeZoom = 1f
            detectTransformGestures { _, _, zoom, _ ->
                cumulativeZoom *= zoom
                when {
                    cumulativeZoom > 1.2f -> {
                        currentOnZoomText?.invoke(true)
                        cumulativeZoom = 1f
                    }
                    cumulativeZoom < 0.8f -> {
                        currentOnZoomText?.invoke(false)
                        cumulativeZoom = 1f
                    }
                }
            }
        }

    if (textFieldValue.text.isBlank() && textMode != PickResultTextMode.EDIT) {
        Text(
            text = stringResource(R.string.float_ball_text_not_found),
            modifier = paddedModifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    when (textMode) {
        PickResultTextMode.EDIT -> {
            val editOuterModifier = when {
                expandToFill -> paddedModifier.fillMaxHeight()
                useInternalScroll -> paddedModifier.heightIn(
                    min = effectiveMaxHeight,
                    max = effectiveMaxHeight,
                )
                else -> paddedModifier
            }.onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyUp &&
                    event.key == Key.Back
                ) {
                    currentOnExitEditMode?.invoke()
                    true
                } else {
                    false
                }
            }
            val editScrollEnabled = expandToFill || useInternalScroll
            val placeholderStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = bodyTextSize,
                lineHeight = editLineHeight,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val toolbarState = rememberOverlaySelectionToolbarState()
            val editToolbarActions = selectionToolbarActions?.copy(
                editable = true,
                onCut = {
                    val updated = cutTextFieldValue(textFieldValue) { copied ->
                        FloatBallTextPick.copyText(appContext, copied)
                    }
                    onTextFieldValueChange(updated)
                },
                onPaste = {
                    val updated = pasteIntoTextFieldValue(appContext, textFieldValue)
                    onTextFieldValueChange(updated)
                },
                onSelectAll = {
                    onTextFieldValueChange(
                        textFieldValue.copy(
                            selection = TextRange(0, textFieldValue.text.length),
                        ),
                    )
                },
            )
            Box(
                modifier = editOuterModifier
                    .clip(RoundedCornerShape(0.dp))
                    .then(toolbarState.viewportModifier()),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (editScrollEnabled) {
                                Modifier.verticalScroll(scrollState)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = onTextFieldValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .suppressSystemTextContextMenu()
                            .then(toolbarState.fieldModifier()),
                        onTextLayout = { toolbarState.textLayoutResult = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = bodyTextSize,
                            lineHeight = editLineHeight,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.padding(vertical = 8.dp)) {
                                if (textFieldValue.text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.float_ball_text_not_found),
                                        style = placeholderStyle,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }
                if (editToolbarActions != null) {
                    OverlaySelectionToolbarOverlay(
                        visible = !textFieldValue.selection.collapsed,
                        selection = textFieldValue.selection,
                        text = textFieldValue.text,
                        textLayoutResult = toolbarState.textLayoutResult,
                        fieldCoordinates = toolbarState.fieldCoordinates,
                        viewportCoordinates = toolbarState.viewportCoordinates,
                        scrollOffsetY = if (editScrollEnabled) scrollState.value else 0,
                        viewportHeightPx = viewportHeightPx,
                        actions = editToolbarActions,
                    )
                }
            }
        }
        PickResultTextMode.WORD_TAP -> {
            if (wordTokens.isEmpty() && textFieldValue.text.isNotBlank()) {
                Box(
                    modifier = paddedModifier.padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
            } else {
                PickResultWordTapBody(
                    wordTokens = wordTokens,
                    selectedWordIndices = selectedWordIndices,
                    onSelectionChange = onWordSelectionChange,
                    onWordLongPress = onWordLongPress,
                    maxHeight = effectiveMaxHeight,
                    fillAvailableHeight = expandToFill,
                    textSizeSp = textSizeSp,
                    modifier = paddedModifier,
                )
            }
        }
        PickResultTextMode.SELECT -> {
            var selection by remember(textFieldValue.text) {
                mutableStateOf(TextRange.Zero)
            }
            var lastSelectAllRequest by remember { mutableIntStateOf(0) }
            var lastDeselectAllRequest by remember { mutableIntStateOf(0) }
            LaunchedEffect(textFieldValue.text) {
                selection = TextRange.Zero
            }
            LaunchedEffect(selectAllRequest) {
                if (selectAllRequest > lastSelectAllRequest) {
                    lastSelectAllRequest = selectAllRequest
                    selection = TextRange(0, textFieldValue.text.length)
                    onSelectionChanged(0, textFieldValue.text.length)
                }
            }
            LaunchedEffect(deselectAllRequest) {
                if (deselectAllRequest > lastDeselectAllRequest) {
                    lastDeselectAllRequest = deselectAllRequest
                    selection = TextRange.Zero
                    onSelectionChanged(0, 0)
                }
            }
            val selectScrollState = rememberScrollState()
            val toolbarState = rememberOverlaySelectionToolbarState()
            val selectToolbarActions = remember(selectionToolbarActions) {
                selectionToolbarActions?.copy(
                    editable = false,
                    onSelectAll = null,
                )
            }
            Box(
                modifier = paddedModifier
                    .heightIn(
                        min = effectiveMaxHeight,
                        max = effectiveMaxHeight,
                    )
                    .clip(RoundedCornerShape(0.dp))
                    .then(toolbarState.viewportModifier()),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(selectScrollState),
                ) {
                    BasicTextField(
                        value = TextFieldValue(textFieldValue.text, selection),
                        onValueChange = { updated ->
                            if (updated.text != textFieldValue.text) return@BasicTextField
                            selection = updated.selection
                            onSelectionChanged(updated.selection.start, updated.selection.end)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .suppressSystemTextContextMenu()
                            .then(toolbarState.fieldModifier()),
                        onTextLayout = { toolbarState.textLayoutResult = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = bodyTextSize,
                            lineHeight = editLineHeight,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        readOnly = true,
                    )
                }
                if (selectToolbarActions != null) {
                    OverlaySelectionToolbarOverlay(
                        visible = !selection.collapsed,
                        selection = selection,
                        text = textFieldValue.text,
                        textLayoutResult = toolbarState.textLayoutResult,
                        fieldCoordinates = toolbarState.fieldCoordinates,
                        viewportCoordinates = toolbarState.viewportCoordinates,
                        scrollOffsetY = selectScrollState.value,
                        viewportHeightPx = viewportHeightPx,
                        actions = selectToolbarActions.copy(
                            onSelectAll = {
                                selection = TextRange(0, textFieldValue.text.length)
                                onSelectionChanged(0, textFieldValue.text.length)
                            },
                        ),
                    )
                }
            }
        }
    }
}

/** 使用屏幕空间 dragAmount 驱动折叠，避免拖动时行随布局移动导致 local Y 失真。 */
private fun Modifier.pickResultLinkedVerticalDrag(
    onDragDelta: (dragAmount: Float) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = composed {
    val onDragDeltaState = rememberUpdatedState(onDragDelta)
    val onDragEndState = rememberUpdatedState(onDragEnd)
    this.then(
        Modifier.pointerInput(Unit) {
            detectPickResultLinkedVerticalDragGestures(
                onDragDelta = { onDragDeltaState.value(it) },
                onDragEnd = { onDragEndState.value() },
            )
        },
    )
}

/**
 * 垂直拖动优先于子级 clickable：未超过 slop 时不消费事件，短按仍可点击；
 * 判定为垂直拖动后再消费并上报增量。
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope
    .detectPickResultLinkedVerticalDragGestures(
    onDragDelta: (dragAmount: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val pointerId = down.id
        val touchSlop = viewConfiguration.touchSlop
        var dragging = false
        var totalX = 0f
        var totalY = 0f
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            if (!change.pressed) {
                if (dragging) {
                    onDragEnd()
                }
                break
            }
            val delta = change.positionChange()
            if (!dragging) {
                if (delta != Offset.Zero) {
                    totalX += delta.x
                    totalY += delta.y
                }
                if (abs(totalY) > touchSlop && abs(totalY) > abs(totalX)) {
                    dragging = true
                }
            } else if (delta.y != 0f) {
                change.consume()
                onDragDelta(delta.y)
            }
        }
    }
}
