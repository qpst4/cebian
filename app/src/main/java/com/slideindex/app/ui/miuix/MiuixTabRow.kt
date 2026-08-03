package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowWithContour

private fun Modifier.miuixTabRowPadding(): Modifier =
    fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)

@Composable
fun MiuixTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
) {
    TabRow(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        listState = listState,
        modifier = modifier.miuixTabRowPadding(),
    )
}

/** 带轮廓动效的 Tab 行，适合卡片内 Tab + Pager 等场景。 */
@Composable
fun MiuixTabRowWithContour(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
) {
    TabRowWithContour(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        listState = listState,
        modifier = modifier.miuixTabRowPadding(),
    )
}
