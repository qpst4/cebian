@file:OptIn(kotlinx.coroutines.FlowPreview::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.slideindex.app.MainActivity
import com.slideindex.app.SlideIndexApp
import com.slideindex.app.overlay.FloatingPointerAreaPreviewOverlay
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.FloatingBottomNavBar
import com.slideindex.app.ui.MainBottomNavDestination
import com.slideindex.app.ui.MainBottomNavHeight
import com.slideindex.app.ui.MainBottomNavOuterPadding
import com.slideindex.app.ui.theme.SlideIndexTheme

@Composable
fun MainNavHost(
    activity: MainActivity,
    app: SlideIndexApp,
    permissionStates: NavPermissionStates,
) {
    val settings by app.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
    )
    var savedBottomNavTab by rememberSaveable {
        mutableStateOf(MainBottomNavDestination.Home.name)
    }
    val initialKey = remember(savedBottomNavTab) {
        when (savedBottomNavTab) {
            MainBottomNavDestination.Shake.name -> AppNavKey.ShakeGestures
            MainBottomNavDestination.Notification.name -> AppNavKey.NotificationHub
            MainBottomNavDestination.Extension.name -> AppNavKey.ExtensionHub
            else -> AppNavKey.HomeMain
        }
    }
    @Suppress("UNCHECKED_CAST")
    val backStack = rememberNavBackStack(initialKey) as NavBackStack<AppNavKey>
    val floatingPointerAreaPreviewEnabledState = rememberSaveable { mutableStateOf(false) }
    val floatingPointerAreaPreviewEnabled by floatingPointerAreaPreviewEnabledState
    val rootBottomContentPadding = MainBottomNavHeight + MainBottomNavOuterPadding

    LaunchedEffect(settings.hideFromRecents) {
        activity.applyHideFromRecents(settings.hideFromRecents)
    }

    val currentKey = backStack.lastOrNull() ?: AppNavKey.HomeMain
    val permissions = permissionStates.collect()

    LaunchedEffect(currentKey, permissions.accessibilityGranted, floatingPointerAreaPreviewEnabled) {
        if (currentKey == AppNavKey.FloatingPointer &&
            permissions.accessibilityGranted &&
            floatingPointerAreaPreviewEnabled
        ) {
            FloatingPointerAreaPreviewOverlay.show(activity)
        } else if (FloatingPointerAreaPreviewOverlay.isShowing) {
            FloatingPointerAreaPreviewOverlay.hide()
        }
    }

    DisposableEffect(Unit) {
        onDispose { FloatingPointerAreaPreviewOverlay.hide() }
    }

    val navContext = remember(
        activity,
        app,
        backStack,
        permissionStates,
        rootBottomContentPadding,
    ) {
        MainNavContext(
            activity = activity,
            app = app,
            backStack = backStack,
            permissionStates = permissionStates,
            floatingPointerAreaPreviewEnabledState = floatingPointerAreaPreviewEnabledState,
            rootBottomContentPadding = rootBottomContentPadding,
        )
    }

    SlideIndexTheme(
        seedColor = androidx.compose.ui.graphics.Color(settings.themeColorArgb),
        dynamicColor = settings.dynamicColorEnabled,
    ) {
        val motionScheme = MaterialTheme.motionScheme
        Box(modifier = Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                transitionSpec = {
                    val spatialSpec = motionScheme.defaultSpatialSpec<IntOffset>()
                    val effectsSpec = motionScheme.defaultEffectsSpec<Float>()
                    slideInHorizontally(spatialSpec) { it / 4 } + fadeIn(effectsSpec) togetherWith
                        slideOutHorizontally(spatialSpec) { -it / 4 } + fadeOut(effectsSpec)
                },
                popTransitionSpec = {
                    val spatialSpec = motionScheme.defaultSpatialSpec<IntOffset>()
                    val effectsSpec = motionScheme.defaultEffectsSpec<Float>()
                    slideInHorizontally(spatialSpec) { -it / 4 } + fadeIn(effectsSpec) togetherWith
                        slideOutHorizontally(spatialSpec) { it / 4 } + fadeOut(effectsSpec)
                },
                entryProvider = entryProvider {
                    homeNavEntries(navContext)
                    shakeNavEntries(navContext)
                    notificationNavEntries(navContext)
                    extensionNavEntries(navContext)
                },
            )
            if (currentKey.isRootDestination()) {
                FloatingBottomNavBar(
                    selected = currentKey.toBottomNavDestination(),
                    onDestinationSelected = { tab ->
                        savedBottomNavTab = tab.name
                        backStack.replaceRoot(tab.toRootNavKey())
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = MainBottomNavOuterPadding),
                )
            }
        }
    }
}
