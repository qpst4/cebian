package com.slideindex.app.overlay.pickresult

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
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

/** 点词 LazyColumn 底部 contentPadding，避免末行 chip 贴边被裁切。 */
internal val PickResultWordTapBottomContentPadding = 4.dp

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
    val contentHeight = rowHeightDp * visibleLines +
        lineSpacingDp * (visibleLines - 1) +
        PickResultWordTapBottomContentPadding
    val screenCap = pickResultWindowHeightDp(PickResultTextHeightScreenFractionCap)
    return minOf(contentHeight, screenCap)
}

/** 面板为正文区分配的高度：7 行内容 + 正文区上下 padding。 */
@Composable
internal fun pickResultTextBodyAllocatedHeight(textSizeSp: Float): Dp =
    pickResultMaxTextHeight(textSizeSp) + PickResultTextBodyVerticalPadding

@Composable
internal fun pickResultMinTextBodyAllocatedHeight(textSizeSp: Float, lines: Int = 6): Dp {
    val density = LocalDensity.current
    val lineHeightDp = with(density) {
        (textSizeSp * PickResultWordTapLineHeightRatio).sp.toDp()
    }
    val rowHeightDp = lineHeightDp + PickResultChipVerticalPaddingDp.dp
    val lineSpacingDp = PickResultFlowRowLineSpacingDp.dp
    val contentHeight = rowHeightDp * lines +
        lineSpacingDp * (lines - 1).coerceAtLeast(0) +
        PickResultWordTapBottomContentPadding
    return contentHeight + PickResultTextBodyVerticalPadding
}
/** 翻译面板等独立区块标题行（含上下 padding）。 */
internal val PickResultTextSectionHeaderReservedHeight = 46.dp

/** 取词面板：文本标题 + 来源切换 + 编辑工具栏合并行。 */
internal val PickResultTextSectionToolbarReservedHeight = 56.dp

/** 仅编辑工具栏行（翻译面板等无合并标题时使用）。 */
internal val PickResultTextToolbarReservedHeight = 36.dp

/** 底部操作栏（分享 / 复制 / 翻译等）。 */
internal val PickResultTextActionBarReservedHeight = 48.dp

/** 文本区内：工具栏与正文之间的垂直间距。 */
internal val PickResultTextToolbarBodySpacing = 12.dp

/** 文本区内：正文与操作栏之间的垂直间距（与操作栏下方分割区视觉平衡）。 */
internal val PickResultTextBodyActionBarSpacing = 12.dp

/** 工具栏 ↔ 正文、正文 ↔ 操作栏间距合计。 */
internal val PickResultTextSectionInnerSpacing =
    PickResultTextToolbarBodySpacing + PickResultTextBodyActionBarSpacing

/** 文本操作栏顶部留白（正文与操作栏之间）。 */
internal val PickResultTextActionBarTopPadding = PickResultTextBodyActionBarSpacing

/** 操作栏底部留白（与面板底边距同步插值；搜索区展开时为 0）。 */
internal val PickResultTextActionBarBottomPaddingWhenAlone = 12.dp

/** 正文区顶部 padding（底部不留白，避免操作栏上方空隙偏大）。 */
internal val PickResultTextBodyTopPadding = 4.dp

/** 正文区上下 padding 合计（与 [PickResultTextBody] paddedModifier 一致）。 */
internal val PickResultTextBodyVerticalPadding = 28.dp

internal fun pickResultTextSectionChromeReservedHeight(): Dp =
    PickResultTextSectionToolbarReservedHeight +
        PickResultTextActionBarReservedHeight +
        PickResultTextSectionInnerSpacing

/** 编辑工具栏 + 操作栏 + 其间距（不含合并标题行）。 */
internal fun pickResultInteractiveTextChromeReservedHeight(): Dp =
    PickResultTextToolbarReservedHeight +
        PickResultTextActionBarReservedHeight +
        PickResultTextSectionInnerSpacing

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
    .background(if (androidx.compose.foundation.isSystemInDarkTheme()) androidx.compose.ui.graphics.Color(0xFF202124) else androidx.compose.ui.graphics.Color(0xFFFFFFFF))

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
    bottomPadding: Dp = PickResultTextActionBarBottomPaddingWhenAlone,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = PickResultTextActionBarTopPadding,
                bottom = bottomPadding,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left side: Secondary actions
        Row(
            modifier = Modifier
                .background(
                    color = if (androidx.compose.foundation.isSystemInDarkTheme()) androidx.compose.ui.graphics.Color(0xFF3C4043) else androidx.compose.ui.graphics.Color(0xFFF1F2F6),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (showSearch) {
                PickResultToolbarIcon(Icons.Outlined.Search, enabled, onSearch)
            }
            if (showOpenLink) {
                Box {
                    PickResultToolbarIcon(
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
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
            onPinToScreen?.let { PickResultToolbarIcon(Icons.Outlined.PushPin, enabled, it) }
            onStash?.let { PickResultToolbarIcon(Icons.Outlined.Inventory2, enabled, it) }
            // Add a small divider before share
            Spacer(modifier = Modifier.size(4.dp))
            Box(
                modifier = Modifier
                    .size(width = 1.dp, height = 16.dp)
                    .align(Alignment.CenterVertically)
                    .background(if (androidx.compose.foundation.isSystemInDarkTheme()) androidx.compose.ui.graphics.Color(0xFF5F6368) else androidx.compose.ui.graphics.Color(0xFFCED6E0))
            )
            Spacer(modifier = Modifier.size(4.dp))
            PickResultToolbarIcon(Icons.Outlined.Share, enabled, onShare)
        }

        // Right side: Primary actions (Copy, Translate)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
            val defaultTranslateBg = if (isDark) androidx.compose.ui.graphics.Color(0xFF3C4043) else androidx.compose.ui.graphics.Color(0xFFF1F2F6)
            val translateBg = when {
                !enabled || !translateEnabled -> defaultTranslateBg.copy(alpha = 0.5f)
                translateSelected -> MaterialTheme.colorScheme.primaryContainer
                else -> defaultTranslateBg
            }
            val translateTint = when {
                !enabled || !translateEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                translateSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.primary
            }
            
            IconButton(
                onClick = onTranslate,
                enabled = enabled && translateEnabled,
                modifier = Modifier
                    .size(44.dp)
                    .then(
                        if (enabled && translateSelected) Modifier.shadow(8.dp, RoundedCornerShape(22.dp), spotColor = MaterialTheme.colorScheme.primary)
                        else Modifier
                    )
                    .background(translateBg, RoundedCornerShape(22.dp))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Translate,
                    contentDescription = null,
                    tint = translateTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            val copyBg = if (enabled) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color(0xFF8C7AE6).copy(alpha = 0.5f)
            val copyTint = if (enabled) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f)
            
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .then(
                        if (enabled) Modifier.shadow(8.dp, RoundedCornerShape(22.dp), spotColor = MaterialTheme.colorScheme.primary)
                        else Modifier
                    )
                    .background(copyBg, RoundedCornerShape(22.dp))
                    .clickable(enabled = enabled, onClick = onCopy)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    tint = copyTint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "复制选中", // Hardcoding based on mockup
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = copyTint
                )
            }
        }
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
