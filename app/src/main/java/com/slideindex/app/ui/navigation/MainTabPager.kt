package com.slideindex.app.ui.navigation

import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import com.slideindex.app.MainActivity
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.settings.BottomNavStyle
import com.slideindex.app.ui.MainBottomNavDestination
import com.slideindex.app.ui.MiuixFloatingBottomNavBar
import com.slideindex.app.ui.MiuixOfficialFloatingBottomNavBar
import com.slideindex.app.ui.miuix.rememberMiuixBlurBackdrop
import dev.chrisbanes.haze.HazeState
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerInterceptionMode
import top.yukonga.miuix.kmp.utils.pagerGestureOverride
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val navBarFadeSpec = tween<Float>(durationMillis = MainNavTransitionDurationMs)
private val navBarSlideSpec = tween<IntOffset>(durationMillis = MainNavTransitionDurationMs)

/**
 * 液态玻璃与 Miuix 浮动导航共用的 pager 宿主。
 * 两种样式共用同一棵页面子树，切换样式只换底栏与 backdrop，不会销毁当前页面。
 */
@Composable
internal fun MainTabPagerHost(
    bottomNavStyle: BottomNavStyle,
    currentTab: MainBottomNavDestination,
    visitedTabs: SnapshotStateSet<MainBottomNavDestination>,
    backStacks: Map<MainBottomNavDestination, NavBackStack>,
    activity: MainActivity,
    deps: AppDependencies,
    permissionStates: NavPermissionStates,
    swipeDismissEnabled: Boolean,
    floatingPointerAreaPreviewEnabledState: MutableState<Boolean>,
    rootBottomContentPadding: Dp,
    bottomNavReselectCounts: Map<MainBottomNavDestination, Int>,
    hazeState: HazeState,
    bottomNavUsesHaze: Boolean,
    bottomNavBlurRadiusDp: Float,
    showBottomNavLabels: Boolean,
    isRootDestination: Boolean,
    onBottomNavBlurPreviewChange: (Float) -> Unit,
    onBottomNavBlurPreviewStop: () -> Unit,
    onTabCommitted: (MainBottomNavDestination) -> Unit,
    onTabReselected: (MainBottomNavDestination) -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = currentTab.ordinal,
        pageCount = { MainBottomNavDestination.entries.size },
    )
    val mainTabPagerState = rememberMainTabPagerState(pagerState)
    // 横滑由 miuix 的 PagerSwipeNode 接管（见下方 pagerGestureOverride），不再经过 pager 原生
    // 手势，interactionSource 收不到拖动事件，所以自行跟踪“用户正在横滑”用于底栏 1:1 跟随。
    var isPagerDragTracking by remember(pagerState) { mutableStateOf(false) }
    val liquidGlassBackdrop = rememberLayerBackdrop()
    val floatingNavBackdrop = rememberMiuixBlurBackdrop(bottomNavUsesHaze)
    val activeBackdrop = when (bottomNavStyle) {
        BottomNavStyle.LIQUID_GLASS -> liquidGlassBackdrop
        BottomNavStyle.FLOATING_NAV -> floatingNavBackdrop
        else -> null
    }

    LaunchedEffect(pagerState) {
        launch {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect {
                    mainTabPagerState.syncPage()
                }
        }
        launch {
            // miuix 的横滑与落位共用一个 scroll 突变，突变结束即表示这一轮横滑收尾。
            snapshotFlow { pagerState.isScrollInProgress }
                .collect { inProgress ->
                    if (!inProgress) isPagerDragTracking = false
                }
        }
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val tab = MainBottomNavDestination.entries[page]
            visitedTabs.add(tab)
            onTabCommitted(tab)
        }
    }

    LaunchedEffect(currentTab) {
        val target = currentTab.ordinal
        if (pagerState.currentPage != target && !isPagerDragTracking && !mainTabPagerState.isNavigating) {
            mainTabPagerState.animateToPage(target)
        }
    }

    MainTabPagerBackHandler(
        enabled = isRootDestination && pagerState.currentPage != 0,
        onBackToFirstTab = { mainTabPagerState.animateToPage(0) },
    )

    // 对齐 Mishka MainPage：外层 Scaffold 固定铺 background，pager 边缘 overscroll 不会露底。
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AnimatedVisibility(
                visible = isRootDestination,
                enter = fadeIn(navBarFadeSpec) +
                    slideInVertically(navBarSlideSpec) { fullHeight -> fullHeight },
                exit = fadeOut(navBarFadeSpec) +
                    slideOutVertically(navBarSlideSpec) { fullHeight -> fullHeight },
            ) {
                // 液态玻璃底栏（Mishka 实现）内部自带 navigationBars 留白，这里不再叠加。
                val bottomBarModifier = Modifier
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(bottomBarModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    when (bottomNavStyle) {
                        BottomNavStyle.LIQUID_GLASS -> MiuixFloatingBottomNavBar(
                            backdrop = liquidGlassBackdrop,
                            targetTabIndex = mainTabPagerState.selectedPage,
                            progress = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                            isTracking = { isPagerDragTracking },
                            blurRadiusDp = bottomNavBlurRadiusDp,
                            glassEnabled = bottomNavUsesHaze,
                            showLabel = showBottomNavLabels,
                            onTabSelected = { tab ->
                                visitedTabs.add(tab)
                                mainTabPagerState.animateToPage(tab.ordinal)
                            },
                            onTabReselected = onTabReselected,
                        )
                        BottomNavStyle.FLOATING_NAV -> MiuixOfficialFloatingBottomNavBar(
                            selectedIndex = mainTabPagerState.selectedPage,
                            onTabSelected = { tab ->
                                visitedTabs.add(tab)
                                mainTabPagerState.animateToPage(tab.ordinal)
                            },
                            onTabReselected = onTabReselected,
                            showLabel = showBottomNavLabels,
                            backdrop = floatingNavBackdrop,
                            blurEnabled = bottomNavUsesHaze,
                            blurRadiusDp = bottomNavBlurRadiusDp,
                        )
                        else -> Unit
                    }
                }
            }
        },
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(activeBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier),
        ) {
            // 竖列表惯性滚动 / 回弹过程中的横滑交给 miuix 的 Cross-Axis 拦截；只在本 Tab
            // 根页启用，子页里的横向滚动（搜索面板、取词结果等）保持原生手势不被抢走。
            val pagerGestureEnabled = isRootDestination
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .pagerGestureOverride(
                        pagerState = pagerState,
                        mode = PagerInterceptionMode.CrossAxisInterceptor,
                        enabled = pagerGestureEnabled,
                        onTriggered = { isPagerDragTracking = true },
                    ),
                userScrollEnabled = false,
                pageNestedScrollConnection = if (pagerGestureEnabled) {
                    PagerGestureNestedScrollConnection
                } else {
                    PagerDefaults.pageNestedScrollConnection(pagerState, Orientation.Horizontal)
                },
                beyondViewportPageCount = 1,
                key = { it },
            ) { page ->
                val destination = MainBottomNavDestination.entries[page]
                if (destination !in visitedTabs) return@HorizontalPager
                MainTabNavStackSingle(
                    destination = destination,
                    backStack = backStacks[destination]!!,
                    activity = activity,
                    deps = deps,
                    permissionStates = permissionStates,
                    swipeDismissEnabled = swipeDismissEnabled,
                    floatingPointerAreaPreviewEnabledState = floatingPointerAreaPreviewEnabledState,
                    rootBottomContentPadding = rootBottomContentPadding,
                    bottomNavReselectCount = bottomNavReselectCounts[destination] ?: 0,
                    hazeState = hazeState,
                    bottomNavUsesHaze = bottomNavUsesHaze,
                    isActiveForHaze = page == pagerState.settledPage,
                    onBottomNavBlurPreviewChange = onBottomNavBlurPreviewChange,
                    onBottomNavBlurPreviewStop = onBottomNavBlurPreviewStop,
                )
            }
        }
    }
}

@Composable
private fun MainTabPagerBackHandler(
    enabled: Boolean,
    onBackToFirstTab: () -> Unit,
) {
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = enabled,
        onBackCompleted = onBackToFirstTab,
    )
}
