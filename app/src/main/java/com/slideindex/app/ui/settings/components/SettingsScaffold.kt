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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ScrollState
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixListScaffold
import com.slideindex.app.ui.miuix.MiuixSectionTitle
import com.slideindex.app.ui.miuix.MiuixBackNavigationIcon
import com.slideindex.app.ui.miuix.MiuixSettingsScreenScaffold
import com.slideindex.app.ui.mainAppPrefersWideContentLayout
import androidx.compose.foundation.layout.widthIn

private val LandscapeSettingsContentMaxWidth = 720.dp

@Composable
private fun Modifier.settingsWideContentWidth(): Modifier {
    if (!mainAppPrefersWideContentLayout()) return this
    return widthIn(max = LandscapeSettingsContentMaxWidth)
}

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
    Column(
        modifier = modifier
            .settingsWideContentWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp)
            .padding(top = 8.dp, bottom = 8.dp + bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
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
    content: @Composable ColumnScope.() -> Unit,
) {
    if (embedded) {
        SettingsEmbeddedContent(
            modifier = modifier,
            contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 12.dp),
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
    content: LazyListScope.() -> Unit,
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }
    MiuixListScaffold(
        title = title,
        navigationIcon = onBack?.let { { MiuixBackNavigationIcon(it) } },
        actions = actions,
        floatingActionButton = floatingActionButton,
    ) {
        if (subtitle != null) {
            item(key = "subtitle") {
                MiuixHintText(subtitle)
            }
        }
        content()
    }
}

@Composable
fun SettingsSectionTitle(title: String, modifier: Modifier = Modifier) {
    MiuixSectionTitle(title, modifier)
}

@Composable
fun SettingsHintText(text: String, modifier: Modifier = Modifier) {
    MiuixHintText(text, modifier)
}

@Composable
fun SettingsCardScope.SettingsHintText(text: String, modifier: Modifier = Modifier) {
    MiuixHintText(text, modifier)
}
