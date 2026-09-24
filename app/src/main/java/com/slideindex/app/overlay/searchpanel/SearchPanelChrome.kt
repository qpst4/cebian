package com.slideindex.app.overlay.searchpanel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.LocalFrostedGlassBackdrop
import com.slideindex.app.overlay.pickresult.PickResultPanelCardCorner
import com.slideindex.app.overlay.pickresult.PickResultPanelCardElevation
import com.slideindex.app.overlay.pickresult.PickResultPanelCardShape
import com.slideindex.app.ui.theme.LocalAppDarkTheme

internal val SearchPanelCardCorner = PickResultPanelCardCorner
internal val SearchPanelCardShape = PickResultPanelCardShape
internal val SearchPanelCardHorizontalPadding = 12.dp
internal val SearchPanelCardVerticalSpacing = 10.dp

/** 搜索引擎 dock 固定高度：网格 + 毛玻璃卡内边距 + 分页指示预留（与 [PickResultTextSearchGrid] 解耦布局）。 */
internal fun SearchPanelEngineDockHeight(
    rows: Int,
    showLabels: Boolean,
    columns: Int,
): Dp {
    val gridHeight = com.slideindex.app.overlay.pickresult.searchGridContentHeight(
        rows = rows,
        showLabels = showLabels,
        columns = columns,
    )
    return gridHeight + 10.dp + 4.dp + 10.dp
}

@Composable
fun SearchPanelEngineDockCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    SearchPanelFrostedCard(
        modifier = modifier,
        contentVerticalPadding = 0.dp,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                content = content,
            )
        },
    )
}

@Composable
fun SearchPanelFrostedCard(
    modifier: Modifier = Modifier,
    contentVerticalPadding: Dp = 4.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = LocalAppDarkTheme.current
    val density = LocalDensity.current
    val cornerPx = with(density) { SearchPanelCardCorner.toPx() }
    val blurRadiusPx = (57f * density.density).toInt()
    val frostedTint = if (isDark) 0x661C1C1E.toInt() else 0x66F5F5F7.toInt()
    val fallbackColor = if (isDark) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
    }
    val borderColor = if (isDark) Color(0x28FFFFFF) else Color(0x18000000)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = PickResultPanelCardElevation,
                shape = SearchPanelCardShape,
                clip = false,
            )
            .clip(SearchPanelCardShape)
            .border(width = 0.5.dp, color = borderColor, shape = SearchPanelCardShape),
    ) {
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(fallbackColor),
            )
            LocalFrostedGlassBackdrop(
                modifier = Modifier.matchParentSize(),
                cornerRadiusPx = cornerPx,
                blurRadiusPx = blurRadiusPx,
                tintColor = frostedTint,
                enabled = true,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = contentVerticalPadding),
            content = content,
        )
    }
}

@Composable
fun SearchPanelSectionCardHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    showExpandControl: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (count > 0) "$title $count" else title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (showExpandControl) {
            Row(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleExpand,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (expanded) {
                        stringResource(R.string.search_panel_collapse)
                    } else {
                        stringResource(R.string.search_panel_card_expand)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(20.dp),
                )
            }
        }
    }
}
