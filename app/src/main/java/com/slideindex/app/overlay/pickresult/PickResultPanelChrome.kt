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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    splitSelectedEnabled: Boolean = false,
    showMoreMenu: Boolean = true,
    showSearch: Boolean = true,
    onSearch: () -> Unit = {},
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onTranslate: () -> Unit,
    onRemoveSpaces: () -> Unit = {},
    onSplitSelectedWords: () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
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
        PickResultToolbarIcon(Icons.Default.Share, enabled, onShare)
        PickResultToolbarIcon(Icons.Default.ContentCopy, enabled, onCopy)
        PickResultToolbarIcon(Icons.Default.ContentPaste, enabled, onPaste)
        PickResultToolbarIcon(
            icon = Icons.Default.Translate,
            enabled = enabled && translateEnabled,
            onClick = onTranslate,
        )
        if (showMoreMenu) {
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
}

@Composable
internal fun PickResultToolbarIcon(
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
