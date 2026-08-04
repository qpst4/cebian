@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.settings.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.MiuixExpandableSearchBottomContent
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ScrollState
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixListScaffold
import com.slideindex.app.ui.miuix.MiuixBackNavigationIcon
import com.slideindex.app.ui.miuix.MiuixSettingsScreenScaffold
import com.slideindex.app.ui.miuix.SettingsListHorizontalPadding
import com.slideindex.app.ui.miuix.WideContentBox

@Composable
fun SettingsEmbeddedContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun HubScrollColumn(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    bottomContentPadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    WideContentBox(modifier = modifier.fillMaxSize()) { sidePadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = sidePadding + SettingsListHorizontalPadding)
                .padding(top = 8.dp, bottom = 8.dp + bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun HubTopAppBar(
    title: String,
    subtitle: String,
) {
    // 保留 API 兼容；MainScreen 已改用 MiuixHubScaffold。
}

@Composable
fun SettingsScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    embedded: Boolean = false,
    scrollContent: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    embeddedContentPadding: PaddingValues = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (embedded) {
        SettingsEmbeddedContent(
            modifier = modifier,
            contentPadding = embeddedContentPadding,
            content = content,
        )
        return
    }
    MiuixSettingsScreenScaffold(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        onBack = onBack,
        scrollContent = scrollContent,
        actions = actions,
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        content = content,
    )
}

@Composable
fun SettingsLazyScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }
    MiuixListScaffold(
        title = title,
        modifier = modifier,
        navigationIcon = onBack?.let { { MiuixBackNavigationIcon(it) } },
        actions = actions,
        floatingActionButton = floatingActionButton,
        bottomContent = bottomContent,
        userScrollEnabled = userScrollEnabled,
    ) {
        if (subtitle != null) {
            item(key = "subtitle") {
                MiuixHintText(subtitle)
            }
        }
        content()
    }
}

/** Lazy 设置页：顶栏 expandable 搜索（默认收起），返回键优先收起搜索。 */
@Composable
fun SettingsLazyScreenScaffoldWithExpandableSearch(
    title: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    hintResId: Int = R.string.search_hint,
    extraActions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val handleBack: () -> Unit = {
        if (
            !consumeExpandableSearchBack(
                expanded = searchExpanded,
                query = searchQuery,
                onExpandedChange = { searchExpanded = it },
                onQueryChange = onSearchQueryChange,
            )
        ) {
            onBack()
        }
    }
    SettingsLazyScreenScaffold(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        onBack = handleBack,
        actions = {
            MiuixExpandableSearchIconAction(
                expanded = searchExpanded,
                query = searchQuery,
                onExpandedChange = { searchExpanded = it },
                onQueryChange = onSearchQueryChange,
            )
            extraActions()
        },
        bottomContent = {
            MiuixExpandableSearchBottomContent(
                searchExpanded = searchExpanded,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                focusRequester = searchFocusRequester,
                hintResId = hintResId,
            )
        },
        content = content,
    )
}

@Composable
fun SettingsHintText(text: String, modifier: Modifier = Modifier) {
    MiuixHintText(text, modifier)
}

@Composable
fun SettingsCardScope.SettingsHintText(text: String, modifier: Modifier = Modifier) {
    SettingsCardRow(key = text) { _ ->
        MiuixHintText(text, modifier)
    }
}
