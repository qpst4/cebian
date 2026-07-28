@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package com.slideindex.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.slideindex.app.ui.MainBottomNavDestination
import com.slideindex.app.ui.MainBottomNavHeight
import com.slideindex.app.ui.MainBottomNavHorizontalPadding
import com.slideindex.app.ui.MainBottomNavOuterPadding
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
    val rootBottomContentPadding = MainBottomNavHeight + MainBottomNavOuterPadding

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

    val currentKey = activeBackStack.lastOrNull() ?: currentTab.toRootNavKey()
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
    val snackbarBottomPadding = if (currentKey.isRootDestination()) {
        rootBottomContentPadding + MainBottomNavOuterPadding
    } else {
        16.dp
    }
    CompositionLocalProvider(LocalAppDependencies provides deps) {
        SlideIndexTheme(
            seedColor = androidx.compose.ui.graphics.Color(rootSettings.themeColorArgb),
            dynamicColor = rootSettings.dynamicColorEnabled,
            paletteStyle = com.slideindex.app.settings.ThemePaletteStyle.fromId(rootSettings.themePaletteStyleId),
        ) {
            val hazeState = remember { HazeState() }
            val bottomNavUsesHaze = overlayUiSettings.usesBottomNavHaze()
            Box(modifier = Modifier.fillMaxSize()) {
                val contentModifier = Modifier.fillMaxSize().let { base ->
                    if (bottomNavUsesHaze) {
                        base.hazeSource(state = hazeState)
                    } else {
                        base
                    }
                }
                Box(modifier = contentModifier) {
                    val tabBackStack = backStacks[currentTab]!!
                    val tabNavContext = remember(
                        currentTab,
                        tabBackStack,
                        rootBottomContentPadding,
                        bottomNavReselectCounts[currentTab],
                    ) {
                        MainNavContext(
                            activity = activity,
                            deps = deps,
                            backStack = tabBackStack,
                            permissionStates = permissionStates,
                            floatingPointerAreaPreviewEnabledState = floatingPointerAreaPreviewEnabledState,
                            rootBottomContentPadding = rootBottomContentPadding,
                            bottomNavReselectCount = bottomNavReselectCounts[currentTab] ?: 0,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        NavDisplay(
                            backStack = tabBackStack,
                            onBack = { tabBackStack.removeLastOrNull() },
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
                if (currentKey.isRootDestination()) {
                    FloatingBottomNavBar(
                        hazeState = hazeState,
                        glassEnabled = bottomNavUsesHaze,
                        selected = currentKey.toBottomNavDestination(),
                        blurRadiusDp = overlayUiSettings.bottomNavBlurRadiusDp,
                        onDestinationSelected = { tab ->
                            if (tab == currentTab) {
                                bottomNavReselectCounts = bottomNavReselectCounts +
                                    (tab to bottomNavReselectCounts.getValue(tab) + 1)
                            } else {
                                savedBottomNavTab = tab.name
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = MainBottomNavHorizontalPadding)
                            .padding(bottom = MainBottomNavOuterPadding),
                    )
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
            }
        }
    }
}
