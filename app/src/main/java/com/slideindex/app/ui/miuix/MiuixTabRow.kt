package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowColors
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** TabRowWithContour 所在容器的底色类型，用于自动选择可见的轮廓配色。 */
enum class MiuixTabRowContourHost {
    /** Miuix Scaffold 等 `surface` 底色页面（App 内默认）。 */
    AppScaffold,
    /** Overlay / 卡片等 `surfaceContainer` 底色容器。 */
    SurfaceContainer,
}

@Composable
fun miuixTabRowContourColors(host: MiuixTabRowContourHost): TabRowColors {
    val scheme = MiuixTheme.colorScheme
    return when (host) {
        MiuixTabRowContourHost.AppScaffold -> TabRowDefaults.tabRowColors(
            backgroundColor = scheme.surfaceContainer,
            selectedBackgroundColor = scheme.surfaceContainerHigh,
        )
        MiuixTabRowContourHost.SurfaceContainer -> TabRowDefaults.tabRowColors(
            backgroundColor = scheme.surface,
            selectedBackgroundColor = scheme.surfaceContainer,
        )
    }
}

private fun Modifier.miuixTabRowPadding(contentHorizontalPadding: Dp): Modifier =
    fillMaxWidth().padding(horizontal = contentHorizontalPadding, vertical = 4.dp)

@Composable
fun MiuixTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
    contentHorizontalPadding: Dp = 0.dp,
) {
    TabRow(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        listState = listState,
        modifier = modifier.miuixTabRowPadding(contentHorizontalPadding),
    )
}

/** 带轮廓动效的 Tab 行，适合 Tab + Pager 等场景。 */
@Composable
fun MiuixTabRowWithContour(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
    contentHorizontalPadding: Dp = 0.dp,
    contourHost: MiuixTabRowContourHost = MiuixTabRowContourHost.AppScaffold,
    colors: TabRowColors = miuixTabRowContourColors(contourHost),
) {
    TabRowWithContour(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        listState = listState,
        colors = colors,
        modifier = modifier.miuixTabRowPadding(contentHorizontalPadding),
    )
}
