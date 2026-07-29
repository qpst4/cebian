@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package com.slideindex.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.slideindex.app.MainActivity
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.overlay.FloatingPointerAreaPreviewOverlay
import com.slideindex.app.settings.AppRootSettings
import com.slideindex.app.settings.OverlaySettings
import com.slideindex.app.settings.usesBottomNavHaze
import com.slideindex.app.ui.FloatingBottomNavBar
import com.slideindex.app.ui.FloatingSideNavRail
import com.slideindex.app.ui.MainBottomNavDestination
import com.slideindex.app.ui.MainBottomNavHorizontalPadding
import com.slideindex.app.ui.MainBottomNavOuterPadding
import com.slideindex.app.ui.mainAppPrefersNavigationRail
import com.slideindex.app.ui.mainAppRootBottomContentPadding
import androidx.hilt.navigation.compose.hiltViewModel
import com.slideindex.app.update.UpdateHost
import com.slideindex.app.update.UpdateViewModel
import com.slideindex.app.ui.OnboardingDialog
import com.slideindex.app.ui.compose.LocalAppDependencies
import com.slideindex.app.ui.feedback.UserMessageSnackbarHost
import com.slideindex.app.ui.theme.SlideIndexTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private const val NAV_ANIMATION_DURATION_MS = 400

@Composable
fun MainNavHost(
    activity: MainActivity,
    deps: AppDependencies,
    permissionStates: NavPermissionStates,
    initialIntentAction: String? = null,
) {
    val settingsSnapshot = remember(deps) { deps.settingsRepository.readSnapshot() }
    val rootSettings by deps.settingsRepository.appRootSettings.collectAsStateWithLifecycle(
        initialValue = AppRootSettings.from(settingsSnapshot),
    )
    val overlayUiSettings by deps.settingsRepository.overlaySettings.collectAsStateWithLifecycle(
        initialValue = OverlaySettings.from(settingsSnapshot),
    )
    var savedBottomNavTab by rememberSaveable {
        val initialTab = if (initialIntentAction == "com.slideindex.app.action.OPEN_NOTIFICATION_HISTORY") {
            MainBottomNavDestination.Notification.name
        } else {
            MainBottomNavDestination.Home.name
        }
        mutableStateOf(initialTab)
    }

    @Suppress("UNCHECKED_CAST")
    val homeBackStack = rememberNavBackStack(AppNavKey.HomeMain) as NavBackStack<AppNavKey>

    @Suppress("UNCHECKED_CAST")
    val shakeBackStack = rememberNavBackStack(AppNavKey.ShakeGestures) as NavBackStack<AppNavKey>

    val notificationInitial = if (initialIntentAction == "com.slideindex.app.action.OPEN_NOTIFICATION_HISTORY") {
        arrayOf(AppNavKey.NotificationHub, AppNavKey.NotificationHistory)
    } else {
        arrayOf(AppNavKey.NotificationHub)
    }
    @Suppress("UNCHECKED_CAST")
    val notificationBackStack = rememberNavBackStack(*notificationInitial) as NavBackStack<AppNavKey>

    @Suppress("UNCHECKED_CAST")
    val extensionBackStack = rememberNavBackStack(AppNavKey.ExtensionHub) as NavBackStack<AppNavKey>

    val backStacks = mapOf(
        MainBottomNavDestination.Home to homeBackStack,
        MainBottomNavDestination.Shake to shakeBackStack,
        MainBottomNavDestination.Notification to notificationBackStack,
        MainBottomNavDestination.Extension to extensionBackStack,
    )
    val currentTab = MainBottomNavDestination.valueOf(savedBottomNavTab)
    val activeBackStack = backStacks[currentTab]!!
    var bottomNavReselectCounts by remember {
        mutableStateOf(MainBottomNavDestination.entries.associateWith { 0 })
    }

    val floatingPointerAreaPreviewEnabledState = rememberSaveable { mutableStateOf(false) }
    val floatingPointerAreaPreviewEnabled by floatingPointerAreaPreviewEnabledState
    val prefersNavigationRail = mainAppPrefersNavigationRail()
    val currentKey = activeBackStack.lastOrNull() ?: currentTab.toRootNavKey()
    val isRootDestination = currentKey.isRootDestination()
    val showSideNavRail = prefersNavigationRail
    val rootBottomContentPadding = mainAppRootBottomContentPadding(prefersNavigationRail, isRootDestination)

    LaunchedEffect(initialIntentAction) {
        if (initialIntentAction == "com.slideindex.app.action.OPEN_NOTIFICATION_HISTORY") {
            savedBottomNavTab = MainBottomNavDestination.Notification.name
            if (notificationBackStack.lastOrNull() != AppNavKey.NotificationHistory) {
                notificationBackStack.clear()
                notificationBackStack.add(AppNavKey.NotificationHub)
                notificationBackStack.add(AppNavKey.NotificationHistory)
            }
        }
    }

    LaunchedEffect(rootSettings.hideFromRecents) {
        activity.applyHideFromRecents(rootSettings.hideFromRecents)
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
        isRootDestination -> rootBottomContentPadding + MainBottomNavOuterPadding
        else -> 16.dp
    }
    CompositionLocalProvider(LocalAppDependencies provides deps) {
        SlideIndexTheme(
            seedColor = androidx.compose.ui.graphics.Color(rootSettings.themeColorArgb),
            dynamicColor = rootSettings.dynamicColorEnabled,
            paletteStyle = com.slideindex.app.settings.ThemePaletteStyle.fromId(rootSettings.themePaletteStyleId),
        ) {
            val hazeState = remember { HazeState() }
            val bottomNavUsesHaze = overlayUiSettings.usesBottomNavHaze()
            val onTabSelected: (MainBottomNavDestination) -> Unit = { tab ->
                if (tab == currentTab) {
                    bottomNavReselectCounts = bottomNavReselectCounts +
                        (tab to bottomNavReselectCounts.getValue(tab) + 1)
                } else {
                    savedBottomNavTab = tab.name
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                val visitedTabs = remember {
                    mutableStateSetOf(
                        if (initialIntentAction == "com.slideindex.app.action.OPEN_NOTIFICATION_HISTORY") {
                            MainBottomNavDestination.Notification
                        } else {
                            MainBottomNavDestination.Home
                        },
                    )
                }
                LaunchedEffect(currentTab) {
                    visitedTabs.add(currentTab)
                }
                val navContentModifier = Modifier.fillMaxSize().let { base ->
                    val withBackground = base.background(MaterialTheme.colorScheme.surface)
                    if (bottomNavUsesHaze) {
                        withBackground.hazeSource(state = hazeState)
                    } else {
                        withBackground
                    }
                }
                val mainTabNavContent: @Composable () -> Unit = {
                    MainTabNavStacks(
                        currentTab = currentTab,
                        visitedTabs = visitedTabs,
                        backStacks = backStacks,
                        activity = activity,
                        deps = deps,
                        permissionStates = permissionStates,
                        floatingPointerAreaPreviewEnabledState = floatingPointerAreaPreviewEnabledState,
                        rootBottomContentPadding = rootBottomContentPadding,
                        bottomNavReselectCounts = bottomNavReselectCounts,
                    )
                }
                if (showSideNavRail) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        FloatingSideNavRail(
                            hazeState = hazeState,
                            glassEnabled = false,
                            selected = currentTab,
                            blurRadiusDp = overlayUiSettings.bottomNavBlurRadiusDp,
                            onDestinationSelected = onTabSelected,
                            modifier = Modifier
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .padding(
                                    start = MainBottomNavOuterPadding,
                                    top = MainBottomNavOuterPadding,
                                    bottom = MainBottomNavOuterPadding,
                                ),
                        )
                        Box(modifier = navContentModifier.weight(1f).fillMaxHeight()) {
                            mainTabNavContent()
                        }
                    }
                } else {
                    Box(modifier = navContentModifier) {
                        mainTabNavContent()
                    }
                    if (isRootDestination) {
                        FloatingBottomNavBar(
                            hazeState = hazeState,
                            glassEnabled = bottomNavUsesHaze,
                            selected = currentTab,
                            blurRadiusDp = overlayUiSettings.bottomNavBlurRadiusDp,
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
    backStacks: Map<MainBottomNavDestination, NavBackStack<AppNavKey>>,
    activity: MainActivity,
    deps: AppDependencies,
    permissionStates: NavPermissionStates,
    floatingPointerAreaPreviewEnabledState: MutableState<Boolean>,
    rootBottomContentPadding: Dp,
    bottomNavReselectCounts: Map<MainBottomNavDestination, Int>,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        MainBottomNavDestination.entries.forEach { destination ->
            if (destination !in visitedTabs) return@forEach
            val stack = backStacks[destination]!!
            val tabNavContext = remember(
                destination,
                stack,
                rootBottomContentPadding,
                bottomNavReselectCounts[destination],
            ) {
                MainNavContext(
                    activity = activity,
                    deps = deps,
                    backStack = stack,
                    permissionStates = permissionStates,
                    floatingPointerAreaPreviewEnabledState = floatingPointerAreaPreviewEnabledState,
                    rootBottomContentPadding = rootBottomContentPadding,
                    bottomNavReselectCount = bottomNavReselectCounts[destination] ?: 0,
                )
            }
            val isActive = destination == currentTab
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (isActive) 1f else 0f)
                    .graphicsLayer { alpha = if (isActive) 1f else 0f },
            ) {
                NavDisplay(
                    backStack = stack,
                    onBack = { stack.removeLastOrNull() },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    transitionSpec = {
                        slideInHorizontally(animationSpec = tween(NAV_ANIMATION_DURATION_MS)) { it } togetherWith
                            slideOutHorizontally(animationSpec = tween(NAV_ANIMATION_DURATION_MS)) { -it / 3 }
                    },
                    popTransitionSpec = {
                        slideInHorizontally(animationSpec = tween(NAV_ANIMATION_DURATION_MS)) { -it / 3 } togetherWith
                            slideOutHorizontally(animationSpec = tween(NAV_ANIMATION_DURATION_MS)) { it }
                    },
                    entryProvider = entryProvider {
                        homeNavEntries(tabNavContext)
                        shakeNavEntries(tabNavContext)
                        notificationNavEntries(tabNavContext)
                        extensionNavEntries(tabNavContext)
                    },
                )
            }
        }
    }
}
