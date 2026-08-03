package com.slideindex.app.ui.miuix

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import com.slideindex.app.ui.mainAppPrefersWideContentLayout
import androidx.compose.foundation.layout.widthIn

private val SettingsContentMaxWidth = 720.dp

@Composable
private fun Modifier.miuixSettingsContentWidth(): Modifier {
    if (!mainAppPrefersWideContentLayout()) return this
    return widthIn(max = SettingsContentMaxWidth)
}

@Composable
fun MiuixListScaffold(
    title: String,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val barBackdrop = rememberMiuixBlurBackdrop()
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixAppBarBlur(barBackdrop),
                color = barBackdrop.miuixAppBarColor(),
                title = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = { navigationIcon?.invoke() },
                actions = actions,
            )
        },
        floatingActionButton = floatingActionButton,
        popupHost = { },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .miuixSettingsContentWidth()
                .then(barBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
            overscrollEffect = null,
            content = content,
        )
    }
}

/** 子页设置：Miuix 顶栏 + 纵向滚动 Column（非 Lazy）。 */
@Composable
fun MiuixSettingsScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    scrollContent: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }
    val scrollBehavior = MiuixScrollBehavior()
    val barBackdrop = rememberMiuixBlurBackdrop()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixAppBarBlur(barBackdrop),
                color = barBackdrop.miuixAppBarColor(),
                title = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (onBack != null) {
                        MiuixBackNavigationIcon(onBack)
                    }
                },
                actions = actions,
            )
        },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        popupHost = { },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .miuixSettingsContentWidth()
                .then(barBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .then(
                    if (scrollContent) {
                        Modifier.verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                    },
                )
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (subtitle != null) {
                MiuixHintText(subtitle)
            }
            content()
        }
    }
}

/** 首页 Hub：大标题顶栏 + 滚动内容区。 */
@Composable
fun MiuixHubScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val barBackdrop = rememberMiuixBlurBackdrop()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixAppBarBlur(barBackdrop),
                color = barBackdrop.miuixAppBarColor(),
                title = title,
                scrollBehavior = scrollBehavior,
            )
        },
        popupHost = {},
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .miuixSettingsContentWidth()
                .then(barBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .verticalScroll(scrollState)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(innerPadding)
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiuixHintText(subtitle)
            content()
        }
    }
}

@Composable
fun MiuixBackNavigationIcon(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}

/**
 * 分组圆角卡片行背景：每行独立 LazyColumn item，保持虚拟化。
 * 对齐 WeKit [groupedCardItem]。
 */
@Composable
fun Modifier.miuixGroupedCardItem(index: Int, count: Int): Modifier {
    val r = CardDefaults.CornerRadius
    val z = 0.dp
    val top = index == 0
    val bottom = index == count - 1
    return fillMaxWidth()
        .squircleSurface(
            color = MiuixTheme.colorScheme.surfaceContainer,
            topStart = if (top) r else z,
            topEnd = if (top) r else z,
            bottomEnd = if (bottom) r else z,
            bottomStart = if (bottom) r else z,
        )
}
