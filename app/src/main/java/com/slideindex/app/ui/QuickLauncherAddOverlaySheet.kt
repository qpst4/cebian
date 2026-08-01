package com.slideindex.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.ui.quicklauncher.QUICK_LAUNCHER_SHEET_ENTER_MS
import com.slideindex.app.ui.quicklauncher.QUICK_LAUNCHER_SHEET_EXIT_MS
import com.slideindex.app.ui.quicklauncher.QuickLauncherAddOverlaySheetBody
import com.slideindex.app.ui.quicklauncher.QuickLauncherAddSubScreen
import com.slideindex.app.ui.quicklauncher.addQuickLauncherItem
import com.slideindex.app.ui.quicklauncher.removeQuickLauncherItem
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AppShortcutLoader.CreatedShortcut
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLauncherAddOverlaySheet(
    panelSide: PanelSide,
    apps: List<AppInfo>,
    configuredAppPackages: Set<String>,
    configuredShortcutKeys: Set<String>,
    configuredActionKeys: Set<String>,
    activityShortcuts: List<ActivityShortcut> = emptyList(),
    onDismiss: () -> Unit,
    onDismissComplete: () -> Unit = onDismiss,
    registerBackHandler: ((() -> Unit) -> Unit)? = null,
    onAdd: (QuickLauncherItem) -> Unit,
    onRemove: (QuickLauncherItem) -> Unit = {},
    launchCreateShortcut: (
        AppShortcutLoader.CreateShortcutHost,
        (CreatedShortcut?) -> Unit,
    ) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    var subScreen by remember { mutableStateOf<QuickLauncherAddSubScreen>(QuickLauncherAddSubScreen.Main) }
    val requestDismiss = remember { { visible = false } }
    val handleOverlayBack: () -> Unit = {
        when (subScreen) {
            QuickLauncherAddSubScreen.Main -> requestDismiss()
            QuickLauncherAddSubScreen.PickApp -> subScreen = QuickLauncherAddSubScreen.Main
            is QuickLauncherAddSubScreen.PickActivity -> subScreen = QuickLauncherAddSubScreen.PickApp
        }
    }

    SideEffect {
        registerBackHandler?.invoke(handleOverlayBack)
    }

    LaunchedEffect(Unit) {
        visible = true
    }

    LaunchedEffect(visible) {
        if (!visible) {
            delay(QUICK_LAUNCHER_SHEET_EXIT_MS.toLong())
            onDismissComplete()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(QUICK_LAUNCHER_SHEET_ENTER_MS)) +
                slideInHorizontally(
                    initialOffsetX = { fullWidth ->
                        if (panelSide == PanelSide.LEFT) -fullWidth / 3 else fullWidth / 3
                    },
                    animationSpec = tween(QUICK_LAUNCHER_SHEET_ENTER_MS),
                ),
            exit = fadeOut(tween(QUICK_LAUNCHER_SHEET_EXIT_MS)) +
                slideOutHorizontally(
                    targetOffsetX = { fullWidth ->
                        if (panelSide == PanelSide.LEFT) -fullWidth / 3 else fullWidth / 3
                    },
                    animationSpec = tween(QUICK_LAUNCHER_SHEET_EXIT_MS),
                ),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = requestDismiss,
                        ),
                )
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = when (panelSide) {
                        PanelSide.LEFT -> Alignment.CenterStart
                        PanelSide.RIGHT -> Alignment.CenterEnd
                        PanelSide.BOTTOM -> Alignment.BottomCenter
                        PanelSide.TOP -> Alignment.TopCenter
                    },
                ) {
                    Surface(
                        modifier = Modifier
                            .padding(start = 10.dp, end = 10.dp, top = 24.dp, bottom = 56.dp)
                            .widthIn(min = 280.dp, max = 400.dp)
                            .fillMaxHeight(0.78f),
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 12.dp,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            QuickLauncherAddOverlayHeader(
                                subScreen = subScreen,
                                onBack = handleOverlayBack,
                                onDone = requestDismiss,
                            )
                            HorizontalDivider()
                            QuickLauncherAddOverlaySheetContent(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                apps = apps,
                                configuredAppPackages = configuredAppPackages,
                                configuredShortcutKeys = configuredShortcutKeys,
                                configuredActionKeys = configuredActionKeys,
                                activityShortcuts = activityShortcuts,
                                onDismiss = requestDismiss,
                                onAdd = onAdd,
                                onRemove = onRemove,
                                launchCreateShortcut = launchCreateShortcut,
                                showTopBar = false,
                                subScreen = subScreen,
                                onSubScreenChange = { subScreen = it },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuickLauncherAddOverlaySheetContent(
    apps: List<AppInfo>,
    configuredAppPackages: Set<String>,
    configuredShortcutKeys: Set<String>,
    configuredActionKeys: Set<String>,
    activityShortcuts: List<ActivityShortcut>,
    onDismiss: () -> Unit,
    onAdd: (QuickLauncherItem) -> Unit,
    onRemove: (QuickLauncherItem) -> Unit,
    launchCreateShortcut: (
        AppShortcutLoader.CreateShortcutHost,
        (CreatedShortcut?) -> Unit,
    ) -> Unit,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
    subScreen: QuickLauncherAddSubScreen = QuickLauncherAddSubScreen.Main,
    onSubScreenChange: (QuickLauncherAddSubScreen) -> Unit = {},
) {
    var searchQuery by remember { mutableStateOf("") }
    var addedAppPackages by remember(configuredAppPackages) { mutableStateOf(configuredAppPackages) }
    var addedShortcutKeys by remember(configuredShortcutKeys) { mutableStateOf(configuredShortcutKeys) }
    var addedActionKeys by remember(configuredActionKeys) { mutableStateOf(configuredActionKeys) }

    fun addItem(item: QuickLauncherItem) {
        val (apps, shortcuts, actions) = addQuickLauncherItem(
            item = item,
            addedAppPackages = addedAppPackages,
            addedShortcutKeys = addedShortcutKeys,
            addedActionKeys = addedActionKeys,
            onAdd = onAdd,
        )
        addedAppPackages = apps
        addedShortcutKeys = shortcuts
        addedActionKeys = actions
    }

    fun removeItem(item: QuickLauncherItem) {
        val (apps, shortcuts, actions) = removeQuickLauncherItem(
            item = item,
            addedAppPackages = addedAppPackages,
            addedShortcutKeys = addedShortcutKeys,
            addedActionKeys = addedActionKeys,
            onRemove = onRemove,
        )
        addedAppPackages = apps
        addedShortcutKeys = shortcuts
        addedActionKeys = actions
    }

    fun toggleItem(item: QuickLauncherItem, added: Boolean) {
        if (added) removeItem(item) else addItem(item)
    }

    if (showTopBar) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                MediumFlexibleTopAppBar(
                    title = {
                        SettingsAppBarTitle(stringResource(R.string.quick_launcher_add))
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_navigate_back))
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            QuickLauncherAddOverlaySheetBody(
                padding = padding,
                nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                apps = apps,
                addedAppPackages = addedAppPackages,
                addedShortcutKeys = addedShortcutKeys,
                addedActionKeys = addedActionKeys,
                activityShortcuts = activityShortcuts,
                onToggle = ::toggleItem,
                launchCreateShortcut = launchCreateShortcut,
                subScreen = subScreen,
                onSubScreenChange = onSubScreenChange,
            )
        }
    } else {
        QuickLauncherAddOverlaySheetBody(
            modifier = modifier.fillMaxSize(),
            padding = PaddingValues(0.dp),
            nestedScrollConnection = null,
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            apps = apps,
            addedAppPackages = addedAppPackages,
            addedShortcutKeys = addedShortcutKeys,
            addedActionKeys = addedActionKeys,
            activityShortcuts = activityShortcuts,
            onToggle = ::toggleItem,
            launchCreateShortcut = launchCreateShortcut,
            subScreen = subScreen,
            onSubScreenChange = onSubScreenChange,
        )
    }
}

@Composable
private fun QuickLauncherAddOverlayHeader(
    subScreen: QuickLauncherAddSubScreen,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val title = when (subScreen) {
        QuickLauncherAddSubScreen.Main -> stringResource(R.string.quick_launcher_add)
        QuickLauncherAddSubScreen.PickApp -> stringResource(R.string.activity_shortcut_pick_app_title)
        is QuickLauncherAddSubScreen.PickActivity ->
            stringResource(R.string.search_engine_pick_activity_title)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_navigate_back),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onDone) {
            Text(stringResource(R.string.quick_launcher_add_overlay_done))
        }
    }
}
