package com.slideindex.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.gesturepicker.ActionPickerAppsTab
import com.slideindex.app.ui.gesturepicker.ActionPickerActionsTab
import com.slideindex.app.ui.gesturepicker.ActionPickerShortcutsTab
import com.slideindex.app.ui.gesturepicker.ActionPickerTab
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.pickerHorizontalSlideTransitionByDepth
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel

private sealed interface GesturePickerSubScreen {
    data object Main : GesturePickerSubScreen
    data object PickApp : GesturePickerSubScreen
    data class PickActivity(val packageName: String) : GesturePickerSubScreen
}

private fun GesturePickerSubScreen.navDepth(): Int = when (this) {
    GesturePickerSubScreen.Main -> 0
    GesturePickerSubScreen.PickApp -> 1
    is GesturePickerSubScreen.PickActivity -> 2
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GestureActionPickerScreen(
    trigger: GestureTriggerType,
    current: GestureAction,
    onDismiss: () -> Unit,
    onSelect: (GestureAction) -> Unit,
    includePointerGestureActions: Boolean = false,
    includeCornerInnerZoneActions: Boolean = false,
    pinNoneAtTop: Boolean = false,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var subScreen by remember { mutableStateOf<GesturePickerSubScreen>(GesturePickerSubScreen.Main) }
    val extensionViewModel: ExtensionSettingsViewModel = hiltViewModel()
    val appSettings by extensionViewModel.settings.collectAsStateWithLifecycle()
    val activityShortcuts = appSettings.activityShortcuts
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    val handleBack: () -> Unit = {
        when (subScreen) {
            GesturePickerSubScreen.Main -> onDismiss()
            GesturePickerSubScreen.PickApp -> subScreen = GesturePickerSubScreen.Main
            is GesturePickerSubScreen.PickActivity -> subScreen = GesturePickerSubScreen.PickApp
        }
    }
    BackHandler(onBack = handleBack)

    LaunchedEffect(Unit) {
        allApps = appRepository.loadApps(force = true)
    }

    AnimatedContent(
        targetState = subScreen,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { pickerHorizontalSlideTransitionByDepth(GesturePickerSubScreen::navDepth) },
        label = "gesturePickerSubNav",
    ) { screen ->
        when (screen) {
        GesturePickerSubScreen.PickApp -> {
            ActivityShortcutPickAppScreen(
                onBack = { subScreen = GesturePickerSubScreen.Main },
                onSelectApp = { app -> subScreen = GesturePickerSubScreen.PickActivity(app.packageName) },
            )
        }
        is GesturePickerSubScreen.PickActivity -> {
            ActivityShortcutPickActivityScreen(
                packageName = screen.packageName,
                onBack = { subScreen = GesturePickerSubScreen.PickApp },
                onSelectActivity = { activity ->
                    onSelect(
                        GestureAction.LaunchShortcut.component(
                            "${activity.packageName}/${activity.className}",
                            activity.label,
                        ),
                    )
                },
            )
        }
        GesturePickerSubScreen.Main -> {
            val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    MediumFlexibleTopAppBar(
                        title = { SettingsAppBarTitle(stringResource(R.string.slot_pick_action)) },
                        navigationIcon = {
                            IconButton(onClick = handleBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_navigate_back),
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    val modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                    PrimaryTabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text(stringResource(R.string.action_picker_tab_actions)) },
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text(stringResource(R.string.action_picker_tab_apps)) },
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text(stringResource(R.string.action_picker_tab_shortcuts)) },
                        )
                    }
                    when (ActionPickerTab.entries[selectedTab]) {
                        ActionPickerTab.ACTIONS -> ActionPickerActionsTab(
                            trigger = trigger,
                            current = current,
                            onSelect = onSelect,
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            modifier = modifier,
                            includePointerGestureActions = includePointerGestureActions,
                            includeCornerInnerZoneActions = includeCornerInnerZoneActions,
                            pinNoneAtTop = pinNoneAtTop,
                        )
                        ActionPickerTab.APPS -> ActionPickerAppsTab(
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            apps = allApps,
                            current = current,
                            onSelect = { app -> onSelect(GestureAction.LaunchApp(app.packageName)) },
                            modifier = modifier,
                        )
                        ActionPickerTab.SHORTCUTS -> ActionPickerShortcutsTab(
                            apps = allApps,
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            current = current,
                            onSelect = onSelect,
                            modifier = modifier,
                            activityShortcuts = activityShortcuts,
                            onBrowseActivityShortcut = { subScreen = GesturePickerSubScreen.PickApp },
                        )
                    }
                }
            }
        }
        }
    }
}
