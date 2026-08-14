package com.slideindex.app.ui.miuix

/**
 * Portions derived from Mishka (https://github.com/YuKongA/Mishka)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private fun PaddingValues.withSettingsListHorizontalPadding(
    sidePadding: Dp,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
): PaddingValues {
    val horizontal = sidePadding + SettingsListHorizontalPadding
    return PaddingValues(
        start = calculateStartPadding(layoutDirection) + horizontal,
        top = calculateTopPadding(),
        end = calculateEndPadding(layoutDirection) + horizontal,
        bottom = calculateBottomPadding(),
    )
}

/** TabRow 置于 [TopAppBar.bottomContent] 时的标准边距，与列表内容水平对齐。 */
@Composable
fun MiuixScaffoldTabRowBottomContent(
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
    ) {
        content()
    }
}

@Composable
fun MiuixListScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    userScrollEnabled: Boolean = true,
    listState: LazyListState? = null,
    bottomContentPadding: Dp = 0.dp,
    content: LazyListScope.() -> Unit,
) {
    val resolvedListState = listState ?: rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val barBackdrop = rememberMiuixBlurBackdrop()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixAppBarBlur(barBackdrop),
                color = barBackdrop.miuixAppBarColor(),
                title = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = { navigationIcon?.invoke() },
                actions = actions,
                bottomContent = bottomContent,
            )
        },
        floatingActionButton = floatingActionButton,
        popupHost = { },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        WideContentBox { sidePadding ->
            LazyColumn(
                state = resolvedListState,
                modifier = Modifier
                    .fillMaxHeight()
                    .horizontalCutoutPadding()
                    .then(barBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = innerPadding.withSettingsListHorizontalPadding(sidePadding, layoutDirection),
                overscrollEffect = null,
                userScrollEnabled = userScrollEnabled,
            ) {
                content()
                if (bottomContentPadding > 0.dp) {
                    item(key = "list-bottom-inset") {
                        Spacer(Modifier.height(bottomContentPadding))
                    }
                }
            }
        }
    }
}

/** 子页设置：Miuix 顶栏 + LazyColumn（Mishka 严格虚拟化）。 */
@Composable
fun MiuixSettingsScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    enableBackHandler: Boolean = true,
    overlayMode: Boolean = false,
    scrollContent: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    bottomContentPadding: Dp = 0.dp,
    content: LazyListScope.() -> Unit,
) {
    if (onBack != null && enableBackHandler) {
        BackHandler(onBack = onBack)
    }
    val scrollBehavior = MiuixScrollBehavior()
    val barBackdrop = rememberMiuixBlurBackdrop(enabled = !overlayMode)
    CompositionLocalProvider(LocalMiuixScreenBackdrop provides barBackdrop) {
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    modifier = if (overlayMode) Modifier else Modifier.miuixAppBarBlur(barBackdrop),
                    color = if (overlayMode) {
                        MiuixTheme.colorScheme.surface
                    } else {
                        barBackdrop.miuixAppBarColor()
                    },
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
            val layoutDirection = LocalLayoutDirection.current
            val listModifier = Modifier
                .fillMaxSize()
                .horizontalCutoutPadding()
                .then(
                    if (!overlayMode) {
                        barBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier
                    } else {
                        Modifier
                    },
                )
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
            val lazyContent: LazyListScope.() -> Unit = {
                if (subtitle != null) {
                    item(key = "subtitle") {
                        MiuixHintText(subtitle)
                    }
                }
                content()
                item(key = "settings-bottom-inset") {
                    Spacer(Modifier.height(8.dp + bottomContentPadding))
                }
            }
            if (overlayMode) {
                LazyColumn(
                    modifier = listModifier,
                    contentPadding = innerPadding.withSettingsListHorizontalPadding(0.dp, layoutDirection),
                    overscrollEffect = null,
                    userScrollEnabled = scrollContent,
                    content = lazyContent,
                )
            } else {
                WideContentBox { sidePadding ->
                    LazyColumn(
                        modifier = listModifier,
                        contentPadding = innerPadding.withSettingsListHorizontalPadding(sidePadding, layoutDirection),
                        overscrollEffect = null,
                        userScrollEnabled = scrollContent,
                        content = lazyContent,
                    )
                }
            }
        }
    }
}

/** 首页 Hub：大标题顶栏 + LazyColumn 内容区。 */
@Composable
fun MiuixHubScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    bottomContentPadding: Dp = 0.dp,
    content: LazyListScope.() -> Unit,
) {
    MiuixListScaffold(
        title = title,
        modifier = modifier,
        listState = listState,
        bottomContentPadding = 8.dp + bottomContentPadding,
    ) {
        item(key = "hub-subtitle") {
            MiuixHintText(subtitle)
        }
        content()
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
