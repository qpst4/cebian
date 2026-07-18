package com.slideindex.app.overlay.pickresult

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.HapticHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    showEditingToolbar: Boolean = true,
    showActionBar: Boolean = true,
    pinActionBarOutside: Boolean = false,
    embedInParentScroll: Boolean = false,
    showSearch: Boolean = false,
    translateEnabled: Boolean = true,
    onActiveTextChange: (String) -> Unit = {},
    onSearch: (String) -> Unit = {},
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit,
    onPaste: () -> Unit,
    onTranslate: (String) -> Unit,
    onRemoveSpaces: (String, removeAll: Boolean) -> Unit,
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
    var appSettings by remember { mutableStateOf(AppSettings()) }
    LaunchedEffect(appContext) {
        OverlayDependencyAccess.overlayDependencies(appContext)
            ?.settingsRepository
            ?.settings
            ?.collect { appSettings = it }
    }
    var wordTokens by remember(text) { mutableStateOf<List<String>>(emptyList()) }
    var wordTokenOverride by remember(text) { mutableStateOf<List<String>?>(null) }
    val effectiveWordTokens = wordTokenOverride ?: wordTokens

    fun currentWordTokens(): List<String> = wordTokenOverride ?: wordTokens

    LaunchedEffect(text) {
        wordTokenOverride = null
        wordTokens = if (text.isBlank()) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) {
                PickResultWordTokenizer.tokenizeSelectableWords(text, appContext)
            }
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

    val bodyScrollState = rememberScrollState()
    val showOcrLoading = ocrLoading &&
        textSource == PickResultTextSource.OCR &&
        text.isBlank()
    val actionBar: @Composable () -> Unit = {
        if (showActionBar) {
            PickResultTextActionBar(
                enabled = text.isNotBlank(),
                translateEnabled = translateEnabled,
                showSearch = showSearch,
                splitSelectedEnabled = textMode == PickResultTextMode.WORD_TAP &&
                    selectedWordIndices.isNotEmpty(),
                onSearch = { runOnActiveText(onSearch) },
                onShare = { runOnActiveText(onShare) },
                onCopy = { runOnActiveText(onCopy) },
                onPaste = onPaste,
                onTranslate = { runOnActiveText(onTranslate) },
                onRemoveSpaces = { runOnActiveText { onRemoveSpaces(it, true) } },
                onSplitSelectedWords = {
                    val split = PickResultWordTokenizer.splitSelectedTokensToChars(
                        tokens = currentWordTokens(),
                        selectedIndices = selectedWordIndices,
                    )
                    if (split != null) {
                        wordTokenOverride = split.tokens
                        selectedWordIndices = split.selectedIndices
                    }
                },
            )
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showSourceChips || showEditingToolbar) {
            PickResultTextToolbar(
                textMode = textMode,
                allSelected = allSelected,
                activeSource = textSource,
                ocrAvailable = ocrAvailable,
                a11yAvailable = a11yAvailable,
                showSourceChips = showSourceChips,
                showEditingToolbar = showEditingToolbar,
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
        if (pinActionBarOutside) {
            val bodyModifier = if (embedInParentScroll) {
                Modifier
            } else {
                Modifier.verticalScroll(
                    bodyScrollState,
                    enabled = textMode != PickResultTextMode.WORD_TAP,
                )
            }
            Column(modifier = bodyModifier) {
                if (showOcrLoading) {
                    PickResultOcrLoadingBody(
                        showBackgroundAction = showBackgroundOcrAction,
                        onBackgroundProcess = onBackgroundOcr,
                    )
                } else {
                    PickResultTextBody(
                        textMode = textMode,
                        textFieldValue = textFieldValue,
                        wordTokens = effectiveWordTokens,
                        selectedWordIndices = selectedWordIndices,
                        selectAllRequest = selectAllRequest,
                        deselectAllRequest = deselectAllRequest,
                        textSizeSp = textSizeSp,
                        useInternalScroll = false,
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
                    )
                }
            }
            actionBar()
        } else {
            if (showOcrLoading) {
                PickResultOcrLoadingBody(
                    showBackgroundAction = showBackgroundOcrAction,
                    onBackgroundProcess = onBackgroundOcr,
                )
            } else {
                PickResultTextBody(
                    textMode = textMode,
                    textFieldValue = textFieldValue,
                    wordTokens = effectiveWordTokens,
                    selectedWordIndices = selectedWordIndices,
                    selectAllRequest = selectAllRequest,
                    deselectAllRequest = deselectAllRequest,
                    textSizeSp = textSizeSp,
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
                )
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
    a11yAvailable: Boolean = true,
    showSourceChips: Boolean = true,
    showEditingToolbar: Boolean = true,
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
        if (showSourceChips) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PickResultSourceChip(
                    label = stringResource(R.string.float_ball_pick_source_a11y),
                    selected = activeSource == PickResultTextSource.A11Y,
                    enabled = a11yAvailable,
                    compact = true,
                    onClick = {
                        if (a11yAvailable) onSourceChange(PickResultTextSource.A11Y)
                    },
                )
                PickResultSourceChip(
                    label = stringResource(R.string.float_ball_pick_source_ocr),
                    selected = activeSource == PickResultTextSource.OCR,
                    enabled = ocrAvailable,
                    compact = true,
                    onClick = {
                        if (ocrAvailable) onSourceChange(PickResultTextSource.OCR)
                    },
                )
            }
        }
        if (showSourceChips && showEditingToolbar) {
            Spacer(modifier = Modifier.weight(1f))
        } else if (showEditingToolbar) {
            Spacer(modifier = Modifier.weight(1f))
        }
        if (showEditingToolbar) {
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
    val background = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
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
        .clickable(enabled = enabled, onClick = onClick)
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
internal fun PickResultTextBody(
    textMode: PickResultTextMode,
    textFieldValue: TextFieldValue,
    wordTokens: List<String>,
    selectedWordIndices: Set<Int>,
    selectAllRequest: Int,
    deselectAllRequest: Int,
    textSizeSp: Float,
    useInternalScroll: Boolean = true,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    onSelectionChanged: (start: Int, end: Int) -> Unit,
    onWordSelectionChange: (Set<Int>) -> Unit,
    onWordLongPress: (Int) -> Unit,
) {
    val bodyTextSize = textSizeSp.sp
    val editLineHeight = (textSizeSp * 22f / 15f).sp
    val maxTextHeight = pickResultMaxTextHeight(textSizeSp)
    val scrollState = rememberScrollState()
    val paddedModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(4.dp))

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
            val editModifier = if (useInternalScroll) {
                paddedModifier
                    .heightIn(max = maxTextHeight)
                    .verticalScroll(scrollState)
            } else {
                paddedModifier
            }
            val placeholderStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = bodyTextSize,
                lineHeight = editLineHeight,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(
                value = textFieldValue,
                onValueChange = onTextFieldValueChange,
                modifier = editModifier,
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
        PickResultTextMode.WORD_TAP -> {
            if (wordTokens.isEmpty() && textFieldValue.text.isNotBlank()) {
                Text(
                    text = textFieldValue.text,
                    modifier = paddedModifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = bodyTextSize),
                )
            } else {
                PickResultWordTapBody(
                    wordTokens = wordTokens,
                    selectedWordIndices = selectedWordIndices,
                    onSelectionChange = onWordSelectionChange,
                    onWordLongPress = onWordLongPress,
                    maxHeight = maxTextHeight,
                    textSizeSp = textSizeSp,
                    modifier = paddedModifier,
                )
            }
        }
        PickResultTextMode.SELECT -> {
            PickResultSelectableText(
                text = textFieldValue.text,
                maxHeight = maxTextHeight,
                textSizeSp = textSizeSp,
                modifier = paddedModifier,
                selectAllRequest = selectAllRequest,
                deselectAllRequest = deselectAllRequest,
                onSelectionChanged = onSelectionChanged,
            )
        }
    }
}
