package com.slideindex.app.overlay.pickresult

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R

internal val PickResultPanelMaxWidth = 400.dp

internal const val PickResultMaxVisibleTextLines = 7

/** 分词 chip 正文行高比例，与 [PickResultWordTapBody] 一致。 */
private const val PickResultWordTapLineHeightRatio = 20f / 15f

/** chip 上下 padding 合计（各 4.dp）。 */
private const val PickResultChipVerticalPaddingDp = 8f

/** 分词 chip 行间距，与 [PickResultWordTapBody] 一致。 */
private const val PickResultFlowRowLineSpacingDp = 4f

/** 文本区高度上限：屏幕高度比例，避免大屏占满卡片。 */
private const val PickResultTextHeightScreenFractionCap = 0.50f

/**
 * 取词正文区最大高度：按字号估算可见 [PickResultMaxVisibleTextLines] 行分词 chip，
 * SELECT / EDIT 模式共用同一上限以保持一致体验。
 */
@Composable
internal fun pickResultWindowHeightDp(fraction: Float): Dp {
    val density = LocalDensity.current
    val containerHeight = with(density) {
        LocalWindowInfo.current.containerSize.height.toDp()
    }
    return containerHeight * fraction
}

@Composable
internal fun pickResultMaxTextHeight(textSizeSp: Float): Dp {
    val density = LocalDensity.current
    val lineHeightDp = with(density) {
        (textSizeSp * PickResultWordTapLineHeightRatio).sp.toDp()
    }
    val rowHeightDp = lineHeightDp + PickResultChipVerticalPaddingDp.dp
    val lineSpacingDp = PickResultFlowRowLineSpacingDp.dp
    val visibleLines = PickResultMaxVisibleTextLines
    val contentHeight = rowHeightDp * visibleLines + lineSpacingDp * (visibleLines - 1)
    val screenCap = pickResultWindowHeightDp(PickResultTextHeightScreenFractionCap)
    return minOf(contentHeight, screenCap)
}
internal val PickResultPanelCardCorner = 14.dp
internal val PickResultPanelCardShape = RoundedCornerShape(PickResultPanelCardCorner)
internal val PickResultBottomPanelShape = RoundedCornerShape(
    topStart = PickResultPanelCardCorner,
    topEnd = PickResultPanelCardCorner,
    bottomStart = 0.dp,
    bottomEnd = 0.dp,
)
internal val PickResultPanelCardElevation = 12.dp

@Composable
internal fun Modifier.pickResultPanelCard(): Modifier = this
    .shadow(
        elevation = PickResultPanelCardElevation,
        shape = PickResultPanelCardShape,
        clip = false,
    )
    .clip(PickResultPanelCardShape)
    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))

@Composable
internal fun Modifier.pickResultBottomPanelCard(): Modifier = this
    .shadow(
        elevation = PickResultPanelCardElevation,
        shape = PickResultBottomPanelShape,
        clip = false,
    )
    .clip(PickResultBottomPanelShape)
    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))

@Composable
internal fun PickResultSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    collapsible: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (collapsible) {
                    Modifier.clickable(onClick = onToggle)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (collapsible) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
internal fun PickResultTextActionBar(
    enabled: Boolean,
    translateEnabled: Boolean = true,
    translateSelected: Boolean = false,
    showSearch: Boolean = true,
    showOpenLink: Boolean = false,
    openLinkChooserExpanded: Boolean = false,
    openLinkChoices: List<String> = emptyList(),
    onSearch: () -> Unit = {},
    onOpenLink: () -> Unit = {},
    onOpenLinkChoice: (String) -> Unit = {},
    onDismissOpenLinkChooser: () -> Unit = {},
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onTranslate: () -> Unit,
    onPinToScreen: (() -> Unit)? = null,
    onStash: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showSearch) {
            PickResultToolbarIcon(Icons.Default.Search, enabled, onSearch)
        }
        if (showOpenLink) {
            Box {
                PickResultToolbarIcon(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    enabled = enabled,
                    onClick = onOpenLink,
                )
                if (openLinkChoices.isNotEmpty()) {
                    DropdownMenu(
                        expanded = openLinkChooserExpanded,
                        onDismissRequest = onDismissOpenLinkChooser,
                    ) {
                        openLinkChoices.forEach { url ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = url,
                                        maxLines = 2,
                                    )
                                },
                                onClick = {
                                    onDismissOpenLinkChooser()
                                    onOpenLinkChoice(url)
                                },
                            )
                        }
                    }
                }
            }
        }
        PickResultToolbarIcon(Icons.Default.Share, enabled, onShare)
        PickResultToolbarIcon(Icons.Default.ContentCopy, enabled, onCopy)
        onPinToScreen?.let { PickResultToolbarIcon(Icons.Default.PushPin, enabled, it) }
        onStash?.let { PickResultToolbarIcon(Icons.Default.Inventory2, enabled, it) }
        PickResultToolbarIcon(
            icon = Icons.Default.Translate,
            enabled = enabled && translateEnabled,
            onClick = onTranslate,
            selected = translateSelected,
        )
    }
}

@Composable
internal fun PickResultToolbarIcon(
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    selected: Boolean = false,
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
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
internal fun pickResultTranslateErrorLabel(code: String): String = when (code) {
    "mlkit_model_not_installed" -> stringResource(R.string.float_ball_translate_error_model_missing)
    "wifi_required" -> stringResource(R.string.float_ball_translate_error_wifi_required)
    "unsupported_language" -> stringResource(R.string.float_ball_translate_error_unsupported_language)
    "translate_unavailable" -> stringResource(R.string.float_ball_translate_error_unavailable)
    "network_error", "http_403", "http_429", "http_500" ->
        stringResource(R.string.float_ball_translate_error_network)
    else -> code
}
