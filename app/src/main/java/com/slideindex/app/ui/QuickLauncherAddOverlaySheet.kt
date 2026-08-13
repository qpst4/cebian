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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.ui.miuix.MiuixExpandableSearchFieldStrip
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixTabRowContourHost
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
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
    shellCommands: List<com.slideindex.app.shell.ShellCommand> = emptyList(),
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
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val requestDismiss = remember { { visible = false } }
    val handleOverlayBack: () -> Unit = {
        when (subScreen) {
            QuickLauncherAddSubScreen.Main -> {
                if (
                    !consumeExpandableSearchBack(
                        expanded = searchExpanded,
                        query = searchQuery,
                        onExpandedChange = { searchExpanded = it },
                        onQueryChange = { searchQuery = it },
                    )
                ) {
                    requestDismiss()
                }
            }
            QuickLauncherAddSubScreen.PickApp -> subScreen = QuickLauncherAddSubScreen.Main
            is QuickLauncherAddSubScreen.PickActivity -> subScreen = QuickLauncherAddSubScreen.PickApp
            is QuickLauncherAddSubScreen.ShellCommandConfig -> subScreen = QuickLauncherAddSubScreen.Main
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
                            val searchHintResId = when (selectedTab) {
                                0 -> R.string.search_actions_hint
                                else -> R.string.search_hint
                            }
                            if (subScreen !is QuickLauncherAddSubScreen.ShellCommandConfig) {
                                QuickLauncherAddOverlayHeader(
                                    subScreen = subScreen,
                                    onBack = handleOverlayBack,
                                    onDone = requestDismiss,
                                    showPickerChrome = subScreen is QuickLauncherAddSubScreen.Main,
                                    selectedTab = selectedTab,
                                    onTabSelected = { selectedTab = it },
                                    searchExpanded = searchExpanded,
                                    onSearchExpandedChange = { searchExpanded = it },
                                    searchQuery = searchQuery,
                                    onSearchChange = { searchQuery = it },
                                    searchFocusRequester = searchFocusRequester,
                                    searchHintResId = searchHintResId,
                                )
                                HorizontalDivider()
                            }
                            QuickLauncherAddOverlaySheetContent(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                apps = apps,
                                configuredAppPackages = configuredAppPackages,
                                configuredShortcutKeys = configuredShortcutKeys,
                                configuredActionKeys = configuredActionKeys,
                                activityShortcuts = activityShortcuts,
                                shellCommands = shellCommands,
                                onDismiss = requestDismiss,
                                onAdd = onAdd,
                                onRemove = onRemove,
                                launchCreateShortcut = launchCreateShortcut,
                                subScreen = subScreen,
                                onSubScreenChange = { subScreen = it },
                                selectedTab = selectedTab,
                                searchQuery = searchQuery,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickLauncherAddOverlaySheetContent(
    apps: List<AppInfo>,
    configuredAppPackages: Set<String>,
    configuredShortcutKeys: Set<String>,
    configuredActionKeys: Set<String>,
    activityShortcuts: List<ActivityShortcut>,
    modifier: Modifier = Modifier,
    shellCommands: List<com.slideindex.app.shell.ShellCommand> = emptyList(),
    onDismiss: () -> Unit,
    onAdd: (QuickLauncherItem) -> Unit,
    onRemove: (QuickLauncherItem) -> Unit,
    launchCreateShortcut: (
        AppShortcutLoader.CreateShortcutHost,
        (CreatedShortcut?) -> Unit,
    ) -> Unit,
    subScreen: QuickLauncherAddSubScreen = QuickLauncherAddSubScreen.Main,
    onSubScreenChange: (QuickLauncherAddSubScreen) -> Unit = {},
    selectedTab: Int = 0,
    searchQuery: String = "",
) {
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

    QuickLauncherAddOverlaySheetBody(
        modifier = modifier.fillMaxSize(),
        padding = PaddingValues(0.dp),
        nestedScrollConnection = null,
        searchQuery = searchQuery,
        apps = apps,
        addedAppPackages = addedAppPackages,
        addedShortcutKeys = addedShortcutKeys,
        addedActionKeys = addedActionKeys,
        activityShortcuts = activityShortcuts,
        shellCommands = shellCommands,
        onToggle = ::toggleItem,
        launchCreateShortcut = launchCreateShortcut,
        subScreen = subScreen,
        onSubScreenChange = onSubScreenChange,
        selectedTab = selectedTab,
    )
}

@Composable
private fun QuickLauncherAddOverlayHeader(
    subScreen: QuickLauncherAddSubScreen,
    onBack: () -> Unit,
    onDone: () -> Unit,
    showPickerChrome: Boolean,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    searchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    searchFocusRequester: FocusRequester,
    searchHintResId: Int,
) {
    val title = when (subScreen) {
        QuickLauncherAddSubScreen.Main -> stringResource(R.string.quick_launcher_add)
        QuickLauncherAddSubScreen.PickApp -> stringResource(R.string.activity_shortcut_pick_app_title)
        is QuickLauncherAddSubScreen.PickActivity ->
            stringResource(R.string.search_engine_pick_activity_title)
        is QuickLauncherAddSubScreen.ShellCommandConfig ->
            stringResource(R.string.gesture_shell_command_config_title)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
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
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showPickerChrome) {
                MiuixExpandableSearchIconAction(
                    expanded = searchExpanded,
                    query = searchQuery,
                    onExpandedChange = onSearchExpandedChange,
                    onQueryChange = onSearchChange,
                )
            }
            TextButton(onClick = onDone) {
                Text(
                    stringResource(R.string.quick_launcher_add_overlay_done),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (showPickerChrome) {
            MiuixExpandableSearchFieldStrip(
                expanded = searchExpanded,
                query = searchQuery,
                onQueryChange = onSearchChange,
                focusRequester = searchFocusRequester,
                hintResId = searchHintResId,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            MiuixTabRowWithContour(
                tabs = listOf(
                    stringResource(R.string.action_picker_tab_actions),
                    stringResource(R.string.action_picker_tab_apps),
                    stringResource(R.string.action_picker_tab_shortcuts),
                ),
                selectedTabIndex = selectedTab,
                onTabSelected = onTabSelected,
                contourHost = MiuixTabRowContourHost.SurfaceContainer,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}
