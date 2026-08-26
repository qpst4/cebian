@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package com.slideindex.app.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import com.slideindex.app.MainActivity
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.overlay.FloatingPointerAreaPreviewOverlay
import com.slideindex.app.settings.AppRootSettings
import com.slideindex.app.settings.HomeMainSettings
import com.slideindex.app.settings.BottomNavMode
import com.slideindex.app.settings.BottomNavStyle
import com.slideindex.app.settings.OverlaySettings
import com.slideindex.app.settings.usesBottomNavHaze
import com.slideindex.app.ui.FloatingBottomNavBar
import com.slideindex.app.ui.ClassicFloatingSideNavRailOverlay
import com.slideindex.app.ui.LocalMainNavContentStartInset
import com.slideindex.app.ui.classicFloatingSideNavRailSlotWidth
import com.slideindex.app.ui.mainNavMiuixRailContentInsets
import com.slideindex.app.ui.MainBottomNavDestination
import com.slideindex.app.ui.MainMiuixNavigationRail
import com.slideindex.app.ui.MainBottomNavHorizontalPadding
import com.slideindex.app.ui.MainBottomNavOuterPadding
import com.slideindex.app.ui.mainAppPrefersNavigationRail
import com.slideindex.app.ui.mainAppRootBottomContentPadding
import com.slideindex.app.ui.MainMiuixBottomNavOuterPadding
import com.slideindex.app.ui.MainFloatingNavBarBottomOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.slideindex.app.update.UpdateHost
import com.slideindex.app.update.UpdateViewModel
import com.slideindex.app.ui.OnboardingDialog
import com.slideindex.app.ui.compose.LocalAppDependencies
import com.slideindex.app.ui.feedback.UserMessageSnackbarHost
import com.slideindex.app.ui.miuix.theme.ModuleTheme
import com.slideindex.app.ui.miuix.theme.toModuleThemeSettings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.android.awaitFrame

/** 底栏显隐动画时长（与 NavDisplay 默认转场大致对齐）。 */
internal const val MainNavTransitionDurationMs = 400
/** 悬浮底栏（宽度 Compact，典型竖屏手机）Tab 切换 */
private const val MAIN_TAB_SWITCH_DURATION_BOTTOM_BAR_MS = 170
/** 侧栏（宽度 ≥ Medium，典型横屏 / 平板）Tab 切换 */
private const val MAIN_TAB_SWITCH_DURATION_RAIL_MS = 220
private val MainTabSwitchSlideOffset = 28.dp
private const val MainTabSwitchInactiveScale = 0.98f
private const val MainBottomNavHostFadeDurationMs = 150

@Composable
fun MainNavHost(
    activity: MainActivity,
    deps: AppDependencies,
    permissionStates: NavPermissionStates,
    initialIntentAction: String? = null,
    initialNavRoute: String? = null,
) {
    val settingsSnapshot = remember(deps) { deps.settingsRepository.readSnapshot() }
    val rootSettings by deps.settingsRepository.appRootSettings.collectAsStateWithLifecycle(
        initialValue = AppRootSettings.from(settingsSnapshot),
    )
    val overlayUiSettings by deps.settingsRepository.overlaySettings.collectAsStateWithLifecycle(
        initialValue = OverlaySettings.from(settingsSnapshot),
    )
    val homeMainSettings by deps.settingsRepository.homeMainSettings.collectAsStateWithLifecycle(
        initialValue = HomeMainSettings.from(settingsSnapshot),
    )
    var savedBottomNavTab by rememberSaveable {
        val initialTab = if (initialIntentAction == MainActivity.ACTION_OPEN_NOTIFICATION_HISTORY) {
            MainBottomNavDestination.Notification.name
        } else {
            MainBottomNavDestination.Home.name
        }
        mutableStateOf(initialTab)
    }

    val homeBackStack = rememberNavBackStack<AppNavKey>(AppNavKey.HomeMain)

    val shakeBackStack = rememberNavBackStack<AppNavKey>(AppNavKey.ShakeGestures)

    val notificationInitial = if (initialIntentAction == MainActivity.ACTION_OPEN_NOTIFICATION_HISTORY) {
        arrayOf(AppNavKey.NotificationHub, AppNavKey.NotificationHistory)
    } else {
        arrayOf(AppNavKey.NotificationHub)
    }
    val notificationBackStack = rememberNavBackStack<AppNavKey>(*notificationInitial)

    val extensionBackStack = rememberNavBackStack<AppNavKey>(AppNavKey.ExtensionHub)

    val backStacks = mapOf(
        MainBottomNavDestination.Home to homeBackStack,
        MainBottomNavDestination.Shake to shakeBackStack,
        MainBottomNavDestination.Notification to notificationBackStack,
        MainBottomNavDestination.Extension to extensionBackStack,
    )
    var deferredTabSelection by remember { mutableStateOf<MainBottomNavDestination?>(null) }
    val currentTab = MainBottomNavDestination.valueOf(savedBottomNavTab)
    val bottomNavSelectedTab = deferredTabSelection ?: currentTab
    val activeBackStack = backStacks[currentTab]!!
    var bottomNavReselectCounts by remember {
        mutableStateOf(MainBottomNavDestination.entries.associateWith { 0 })
    }
    val visitedTabs = remember {
        mutableStateSetOf(currentTab)
    }

    val floatingPointerAreaPreviewEnabledState = rememberSaveable { mutableStateOf(false) }
    val floatingPointerAreaPreviewEnabled by floatingPointerAreaPreviewEnabledState
    val prefersNavigationRail = mainAppPrefersNavigationRail()
    val bottomNavStyle = BottomNavStyle.fromId(overlayUiSettings.bottomNavStyleId)
    val currentKey = activeBackStack.currentAppNavKey() ?: currentTab.toRootNavKey()
    val isRootDestination = currentKey.isRootDestination()
    val showSideNavRail = prefersNavigationRail
    val showClassicSideNavRail = showSideNavRail && bottomNavStyle == BottomNavStyle.CLASSIC
    val showMiuixSideNavRail = showSideNavRail &&
        (bottomNavStyle == BottomNavStyle.LIQUID_GLASS || bottomNavStyle == BottomNavStyle.FLOATING_NAV)
    val useLiquidGlassBottomNav = bottomNavStyle == BottomNavStyle.LIQUID_GLASS && !showSideNavRail
    val useFloatingNavBottomNav = bottomNavStyle == BottomNavStyle.FLOATING_NAV && !showSideNavRail
    val usePagerBottomNav = useLiquidGlassBottomNav || useFloatingNavBottomNav
    val effectiveBottomNavStyle = when {
        useLiquidGlassBottomNav -> BottomNavStyle.LIQUID_GLASS
        useFloatingNavBottomNav -> BottomNavStyle.FLOATING_NAV
        else -> BottomNavStyle.CLASSIC
    }
    val showBottomNavLabels = BottomNavMode.fromId(
        overlayUiSettings.bottomNavModeId,
    ).showLabels
    val rootBottomContentPadding = mainAppRootBottomContentPadding(
        prefersNavigationRail = showSideNavRail,
        isRootDestination = isRootDestination,
        bottomNavStyle = effectiveBottomNavStyle,
    )

    LaunchedEffect(initialIntentAction, initialNavRoute) {
        if (initialIntentAction == MainActivity.ACTION_OPEN_NOTIFICATION_HISTORY) {
            savedBottomNavTab = MainBottomNavDestination.Notification.name
            if (notificationBackStack.lastOrNull() != AppNavKey.NotificationHistory) {
                notificationBackStack.clear()
                notificationBackStack.add(AppNavKey.NotificationHub)
                notificationBackStack.add(AppNavKey.NotificationHistory)
            }
        }
        when (initialNavRoute) {
            "extension_freezer" -> {
                savedBottomNavTab = MainBottomNavDestination.Extension.name
                extensionBackStack.clear()
                extensionBackStack.add(AppNavKey.ExtensionHub)
                extensionBackStack.add(AppNavKey.ExtensionFreezer)
            }
        }
    }

    LaunchedEffect(rootSettings.hideFromRecents) {
        activity.applyHideFromRecents(rootSettings.hideFromRecents)
    }

    val skipPredictiveBackRecreate = remember { mutableStateOf(true) }
    LaunchedEffect(rootSettings.predictiveBackEnabled) {
        activity.applyPredictiveBackEnabled(rootSettings.predictiveBackEnabled)
        if (skipPredictiveBackRecreate.value) {
            skipPredictiveBackRecreate.value = false
        } else {
            activity.recreateWithoutTransition()
        }
    }

    val permissions = permissionStates.collect()

    LaunchedEffect(currentKey, permissions.accessibilityGranted, floatingPointerAreaPreviewEnabled) {
        if (currentKey == AppNavKey.FloatingPointer &&
            permissions.accessibilityGranted &&
            floatingPointerAreaPreviewEnabled
        ) {
            FloatingPointerAreaPreviewOverlay.show(deps)
        } else if (FloatingPointerAreaPreviewOverlay.isShowing) {
            FloatingPointerAreaPreviewOverlay.hide()
        }
    }

    DisposableEffect(Unit) {
        onDispose { FloatingPointerAreaPreviewOverlay.hide() }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val updateViewModel: UpdateViewModel = hiltViewModel(activity)
    val snackbarBottomPadding = when {
        showSideNavRail -> MainBottomNavOuterPadding
        isRootDestination && useLiquidGlassBottomNav ->
            rootBottomContentPadding + MainMiuixBottomNavOuterPadding
        isRootDestination && useFloatingNavBottomNav ->
            rootBottomContentPadding + MainFloatingNavBarBottomOffset
        isRootDestination -> rootBottomContentPadding + MainBottomNavOuterPadding
        else -> 16.dp
    }
    CompositionLocalProvider(LocalAppDependencies provides deps) {
        ModuleTheme(settings = overlayUiSettings.toModuleThemeSettings()) {
            val hazeState = remember { HazeState() }
            val bottomNavUsesHaze = overlayUiSettings.usesBottomNavHaze()
            var bottomNavBlurPreviewRadiusDp by remember { mutableStateOf<Float?>(null) }
            val bottomNavBlurRadiusDp = bottomNavBlurPreviewRadiusDp
                ?: overlayUiSettings.bottomNavBlurRadiusDp
            val onBottomNavBlurPreviewChange: (Float) -> Unit = { value ->
                bottomNavBlurPreviewRadiusDp = value
            }
            val onBottomNavBlurPreviewStop: () -> Unit = {
                bottomNavBlurPreviewRadiusDp = null
            }
            LaunchedEffect(Unit) {
                awaitFrame()
                MainBottomNavDestination.entries.forEach { destination ->
                    if (destination !in visitedTabs) {
                        visitedTabs.add(destination)
                        awaitFrame()
                    }
                }
            }
            LaunchedEffect(deferredTabSelection) {
                val tab = deferredTabSelection ?: return@LaunchedEffect
                awaitFrame()
                if (deferredTabSelection == tab) {
                    savedBottomNavTab = tab.name
                    deferredTabSelection = null
                }
            }
            val onTabSelected: (MainBottomNavDestination) -> Unit = { tab ->
                if (tab == bottomNavSelectedTab) {
                    bottomNavReselectCounts = bottomNavReselectCounts +
                        (tab to bottomNavReselectCounts.getValue(tab) + 1)
                } else {
                    val firstComposition = tab !in visitedTabs
                    visitedTabs.add(tab)
                    if (firstComposition) {
                        deferredTabSelection = tab
                    } else {
                        deferredTabSelection = null
                        savedBottomNavTab = tab.name
                    }
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                val navContentModifier = Modifier.fillMaxSize()
                var lastPagerStyle by remember { mutableStateOf(BottomNavStyle.LIQUID_GLASS) }
                val activePagerStyle = if (useFloatingNavBottomNav) {
                    BottomNavStyle.FLOATING_NAV
                } else {
                    BottomNavStyle.LIQUID_GLASS
                }
                if (usePagerBottomNav) {
                    lastPagerStyle = activePagerStyle
                }
                val pagerStyle = if (usePagerBottomNav) activePagerStyle else lastPagerStyle
                val saveableStateHolder = rememberSaveableStateHolder()
                val mainTabNavContent: @Composable () -> Unit = {
                    Crossfade(
                        targetState = usePagerBottomNav,
                        animationSpec = tween(durationMillis = MainBottomNavHostFadeDurationMs),
                        label = "bottomNavHostFade",
                    ) { usePager ->
                        if (usePager) {
                            saveableStateHolder.SaveableStateProvider("pager") {
                                MainTabPagerHost(
                                    bottomNavStyle = pagerStyle,
                                    currentTab = currentTab,
                                    visitedTabs = visitedTabs,
                                    backStacks = backStacks,
                                    activity = activity,
                                    deps = deps,
                                    permissionStates = permissionStates,
                                    swipeDismissEnabled = homeMainSettings.swipeDismissEnabled,
                                    floatingPointerAreaPreviewEnabledState = floatingPointerAreaPreviewEnabledState,
                                    rootBottomContentPadding = rootBottomContentPadding,
                                    bottomNavReselectCounts = bottomNavReselectCounts,
                                    hazeState = hazeState,
                                    bottomNavUsesHaze = bottomNavUsesHaze,
                                    bottomNavBlurRadiusDp = bottomNavBlurRadiusDp,
                                    showBottomNavLabels = showBottomNavLabels,
                                    isRootDestination = isRootDestination,
                                    onBottomNavBlurPreviewChange = onBottomNavBlurPreviewChange,
                                    onBottomNavBlurPreviewStop = onBottomNavBlurPreviewStop,
                                    onTabCommitted = { tab ->
                                        deferredTabSelection = null
                                        savedBottomNavTab = tab.name
                                    },
                                    onTabReselected = { tab ->
                                        bottomNavReselectCounts = bottomNavReselectCounts +
                                            (tab to bottomNavReselectCounts.getValue(tab) + 1)
                                    },
                                )
                            }
                        } else {
                            saveableStateHolder.SaveableStateProvider("stacks") {
                                MainTabNavStacks(
                                    currentTab = currentTab,
                                    visitedTabs = visitedTabs,
                                    backStacks = backStacks,
                                    activity = activity,
                                    deps = deps,
                                    permissionStates = permissionStates,
                                    swipeDismissEnabled = homeMainSettings.swipeDismissEnabled,
                                    floatingPointerAreaPreviewEnabledState = floatingPointerAreaPreviewEnabledState,
                                    rootBottomContentPadding = rootBottomContentPadding,
                                    bottomNavReselectCounts = bottomNavReselectCounts,
                                    hazeState = hazeState,
                                    bottomNavUsesHaze = bottomNavUsesHaze,
                                    onBottomNavBlurPreviewChange = onBottomNavBlurPreviewChange,
                                    onBottomNavBlurPreviewStop = onBottomNavBlurPreviewStop,
                                )
                            }
                        }
                    }
                }
                if (showSideNavRail) {
                    when {
                        showClassicSideNavRail -> {
                            val classicRailInset = classicFloatingSideNavRailSlotWidth()
                            Box(modifier = Modifier.fillMaxSize()) {
                                CompositionLocalProvider(
                                    LocalMainNavContentStartInset provides classicRailInset,
                                ) {
                                    Box(modifier = navContentModifier) {
                                        mainTabNavContent()
                                    }
                                }
                                ClassicFloatingSideNavRailOverlay(
                                    cutoutFillColor = MiuixTheme.colorScheme.background,
                                    hazeState = hazeState,
                                    glassEnabled = bottomNavUsesHaze,
                                    selected = bottomNavSelectedTab,
                                    blurRadiusDp = bottomNavBlurRadiusDp,
                                    onDestinationSelected = onTabSelected,
                                    modifier = Modifier.align(Alignment.CenterStart),
                                )
                            }
                        }
                        showMiuixSideNavRail -> {
                            Row(modifier = Modifier.fillMaxSize()) {
                                MainMiuixNavigationRail(
                                    selected = bottomNavSelectedTab,
                                    onDestinationSelected = onTabSelected,
                                    modifier = Modifier.fillMaxHeight(),
                                )
                                Box(
                                    modifier = navContentModifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .mainNavMiuixRailContentInsets(),
                                ) {
                                    CompositionLocalProvider(
                                        LocalMainNavContentStartInset provides 0.dp,
                                    ) {
                                        mainTabNavContent()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = navContentModifier) {
                        mainTabNavContent()
                    }
                    if (isRootDestination && !usePagerBottomNav) {
                        FloatingBottomNavBar(
                            hazeState = hazeState,
                            glassEnabled = bottomNavUsesHaze,
                            selected = bottomNavSelectedTab,
                            blurRadiusDp = bottomNavBlurRadiusDp,
                            showLabels = showBottomNavLabels,
                            onDestinationSelected = onTabSelected,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = MainBottomNavHorizontalPadding)
                                .padding(bottom = MainBottomNavOuterPadding),
                        )
                    }
                }
                UserMessageSnackbarHost(
                    userMessageBus = deps.userMessageBus,
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = snackbarBottomPadding),
                )
                val globalNavContext = remember(
                    activeBackStack,
                    rootBottomContentPadding,
                    bottomNavReselectCounts[currentTab],
                ) {
                    MainNavContext(
                        activity = activity,
                        deps = deps,
                        backStack = activeBackStack,
                        permissionStates = permissionStates,
                        floatingPointerAreaPreviewEnabledState = floatingPointerAreaPreviewEnabledState,
                        rootBottomContentPadding = rootBottomContentPadding,
                        bottomNavReselectCount = bottomNavReselectCounts[currentTab] ?: 0,
                    )
                }
                OnboardingDialog(
                    visible = !rootSettings.onboardingCompleted,
                    permissions = permissions,
                    onRequestOverlay = { globalNavContext.openOverlaySettings() },
                    onRequestAccessibility = { globalNavContext.openAccessibilitySettings() },
                    onRequestNotification = { globalNavContext.requestNotificationPermission() },
                    onComplete = {
                        globalNavContext.launch {
                            deps.settingsRepository.setOnboardingCompleted(true)
                        }
                    },
                    onSkip = {
                        globalNavContext.launch {
                            deps.settingsRepository.setOnboardingCompleted(true)
                        }
                    },
                )
                UpdateHost(viewModel = updateViewModel, entryIntentAction = initialIntentAction)
            }
        }
    }
}

@Composable
private fun MainTabNavStacks(
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
    onBottomNavBlurPreviewChange: (Float) -> Unit,
    onBottomNavBlurPreviewStop: () -> Unit,
) {
    val slideDistancePx = with(LocalDensity.current) { MainTabSwitchSlideOffset.toPx() }
    val currentTabIndex = currentTab.ordinal
    val tabSwitchAxisVertical = mainAppPrefersNavigationRail()
    val tabSwitchAnimationSpec = remember(tabSwitchAxisVertical) {
        val durationMs = if (tabSwitchAxisVertical) {
            MAIN_TAB_SWITCH_DURATION_RAIL_MS
        } else {
            MAIN_TAB_SWITCH_DURATION_BOTTOM_BAR_MS
        }
        tween<Float>(durationMillis = durationMs, easing = FastOutSlowInEasing)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .then(
                if (bottomNavUsesHaze) {
                    Modifier.hazeSource(state = hazeState)
                } else {
                    Modifier
                },
            ),
    ) {
        MainBottomNavDestination.entries.forEach { destination ->
            if (destination !in visitedTabs) return@forEach
            val stack = backStacks[destination]!!
            val isActive = destination == currentTab
            val targetAlpha = if (isActive) 1f else 0f
            val targetTranslationX = if (tabSwitchAxisVertical || isActive) {
                0f
            } else {
                when {
                    destination.ordinal < currentTabIndex -> -slideDistancePx
                    destination.ordinal > currentTabIndex -> slideDistancePx
                    else -> 0f
                }
            }
            val targetTranslationY = if (!tabSwitchAxisVertical || isActive) {
                0f
            } else {
                when {
                    destination.ordinal < currentTabIndex -> -slideDistancePx
                    destination.ordinal > currentTabIndex -> slideDistancePx
                    else -> 0f
                }
            }
            val targetScale = if (isActive) 1f else MainTabSwitchInactiveScale
            val tabAlpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tabSwitchAnimationSpec,
                label = "mainTabAlpha-${destination.name}",
            )
            val tabTranslationX by animateFloatAsState(
                targetValue = targetTranslationX,
                animationSpec = tabSwitchAnimationSpec,
                label = "mainTabTranslationX-${destination.name}",
            )
            val tabTranslationY by animateFloatAsState(
                targetValue = targetTranslationY,
                animationSpec = tabSwitchAnimationSpec,
                label = "mainTabTranslationY-${destination.name}",
            )
            val tabScale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = tabSwitchAnimationSpec,
                label = "mainTabScale-${destination.name}",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (isActive) 1f else 0f)
                    .graphicsLayer {
                        alpha = tabAlpha
                        translationX = tabTranslationX
                        translationY = tabTranslationY
                        scaleX = tabScale
                        scaleY = tabScale
                    },
            ) {
                MainTabNavStackSingle(
                    destination = destination,
                    backStack = stack,
                    activity = activity,
                    deps = deps,
                    permissionStates = permissionStates,
                    swipeDismissEnabled = swipeDismissEnabled,
                    floatingPointerAreaPreviewEnabledState = floatingPointerAreaPreviewEnabledState,
                    rootBottomContentPadding = rootBottomContentPadding,
                    bottomNavReselectCount = bottomNavReselectCounts[destination] ?: 0,
                    hazeState = null,
                    bottomNavUsesHaze = bottomNavUsesHaze,
                    isActiveForHaze = isActive,
                    onBottomNavBlurPreviewChange = onBottomNavBlurPreviewChange,
                    onBottomNavBlurPreviewStop = onBottomNavBlurPreviewStop,
                )
            }
        }
    }
}

@Composable
internal fun MainTabNavStackSingle(
    destination: MainBottomNavDestination,
    backStack: NavBackStack,
    activity: MainActivity,
    deps: AppDependencies,
    permissionStates: NavPermissionStates,
    swipeDismissEnabled: Boolean,
    floatingPointerAreaPreviewEnabledState: MutableState<Boolean>,
    rootBottomContentPadding: Dp,
    bottomNavReselectCount: Int,
    hazeState: HazeState?,
    bottomNavUsesHaze: Boolean,
    isActiveForHaze: Boolean = true,
    onBottomNavBlurPreviewChange: (Float) -> Unit,
    onBottomNavBlurPreviewStop: () -> Unit,
) {
    val tabNavContext = remember(
        destination,
        backStack,
        rootBottomContentPadding,
        bottomNavReselectCount,
    ) {
        MainNavContext(
            activity = activity,
            deps = deps,
            backStack = backStack,
            permissionStates = permissionStates,
            floatingPointerAreaPreviewEnabledState = floatingPointerAreaPreviewEnabledState,
            rootBottomContentPadding = rootBottomContentPadding,
            bottomNavReselectCount = bottomNavReselectCount,
            onBottomNavBlurPreviewChange = onBottomNavBlurPreviewChange,
            onBottomNavBlurPreviewStop = onBottomNavBlurPreviewStop,
        )
    }
    val swipeBackDirection = when (LocalLayoutDirection.current) {
        LayoutDirection.Rtl -> NavSwipeDirection.RightToLeft
        else -> NavSwipeDirection.LeftToRight
    }
    val swipeDismiss = if (swipeDismissEnabled) swipeBackDirection else null
    val registerEntries = remember(destination, tabNavContext, swipeDismiss) {
        mainTabNavEntryProvider(swipeDismiss, destination, tabNavContext)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (hazeState != null && bottomNavUsesHaze && isActiveForHaze) {
                    Modifier.hazeSource(state = hazeState)
                } else {
                    Modifier
                },
            ),
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            effects = NavDisplayEffects(
                cornerClipRadius = rememberNavSystemCornerRadius(),
            ),
            content = registerEntries,
        )
    }
}

private fun mainTabNavEntryProvider(
    swipeDismiss: NavSwipeDirection?,
    destination: MainBottomNavDestination,
    tabNavContext: MainNavContext,
): NavEntryBuilder.() -> Unit = {
    NavEntrySwipeDismissScope.current = swipeDismiss
    registerMainTabNavEntries(destination, tabNavContext)
}

private fun NavEntryBuilder.registerMainTabNavEntries(
    destination: MainBottomNavDestination,
    ctx: MainNavContext,
) {
    when (destination) {
        MainBottomNavDestination.Home -> {
            homeNavEntries(ctx)
            floatBallNavEntries(ctx)
        }
        MainBottomNavDestination.Shake -> shakeNavEntries(ctx)
        MainBottomNavDestination.Notification -> notificationNavEntries(ctx)
        MainBottomNavDestination.Extension -> extensionNavEntries(ctx)
    }
}
