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
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.slideindex.app.ui.MainMiuixBottomNavOuterPadding
import com.slideindex.app.ui.MiuixFloatingBottomNavBar
import com.slideindex.app.ui.MiuixOfficialFloatingBottomNavBar
import com.slideindex.app.ui.miuix.rememberMiuixBlurBackdrop
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

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
    val scope = rememberCoroutineScope()
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val liquidGlassBackdrop = rememberLayerBackdrop()
    val floatingNavBackdrop = rememberMiuixBlurBackdrop(bottomNavUsesHaze)
    val activeBackdrop = when (bottomNavStyle) {
        BottomNavStyle.LIQUID_GLASS -> liquidGlassBackdrop
        BottomNavStyle.FLOATING_NAV -> floatingNavBackdrop
        else -> null
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val tab = MainBottomNavDestination.entries[page]
            visitedTabs.add(tab)
            onTabCommitted(tab)
        }
    }

    LaunchedEffect(currentTab) {
        val target = currentTab.ordinal
        if (pagerState.currentPage != target && !isDragged) {
            pagerState.animateScrollToPage(target)
        }
    }

    MainTabPagerBackHandler(
        enabled = isRootDestination && pagerState.currentPage != 0,
        onBackToFirstTab = { scope.launch { pagerState.animateScrollToPage(0) } },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(activeBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = isRootDestination,
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
        AnimatedVisibility(
            visible = isRootDestination,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(navBarFadeSpec) +
                slideInVertically(navBarSlideSpec) { fullHeight -> fullHeight },
            exit = fadeOut(navBarFadeSpec) +
                slideOutVertically(navBarSlideSpec) { fullHeight -> fullHeight },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (bottomNavStyle) {
                    BottomNavStyle.LIQUID_GLASS -> MiuixFloatingBottomNavBar(
                        backdrop = liquidGlassBackdrop,
                        targetTabIndex = pagerState.targetPage,
                        progress = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                        isTracking = { isDragged },
                        blurRadiusDp = bottomNavBlurRadiusDp,
                        glassEnabled = bottomNavUsesHaze,
                        showLabel = showBottomNavLabels,
                        onTabSelected = { tab ->
                            visitedTabs.add(tab)
                            scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
                        },
                        onTabReselected = onTabReselected,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = MainMiuixBottomNavOuterPadding),
                    )
                    BottomNavStyle.FLOATING_NAV -> Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        MiuixOfficialFloatingBottomNavBar(
                            selectedIndex = pagerState.targetPage,
                            onTabSelected = { tab ->
                                visitedTabs.add(tab)
                                scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
                            },
                            onTabReselected = onTabReselected,
                            showLabel = showBottomNavLabels,
                            backdrop = floatingNavBackdrop,
                            blurEnabled = bottomNavUsesHaze,
                            blurRadiusDp = bottomNavBlurRadiusDp,
                        )
                    }
                    else -> Unit
                }
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
