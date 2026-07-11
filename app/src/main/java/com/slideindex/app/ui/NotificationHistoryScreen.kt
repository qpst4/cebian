package com.slideindex.app.ui

import com.slideindex.app.ui.viewmodel.NotificationHistoryViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.notification.NotificationHistoryItem
import com.slideindex.app.notification.NotificationHistoryUiState
import com.slideindex.app.notification.computeNotificationHistoryUiState
import com.slideindex.app.ui.notificationhistory.ActiveNotificationsTab
import com.slideindex.app.ui.notificationhistory.HiddenNotificationsTab
import com.slideindex.app.ui.notificationhistory.HistoryNotificationsTab
import com.slideindex.app.ui.notificationhistory.NotificationFilterTab
import com.slideindex.app.ui.notificationhistory.NotificationHistoryFilterBar
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import java.text.DateFormat

private data class NotificationHistoryQuery(
    val items: List<NotificationHistoryItem>,
    val rules: List<com.slideindex.app.notification.NotificationFilterRule>,
    val listenerEnabled: Boolean,
    val searchQuery: String,
    val refreshGeneration: Int,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, FlowPreview::class)
@Composable
fun NotificationHistoryScreen(
    viewModel: NotificationHistoryViewModel,
    listenerEnabled: Boolean,
    onBack: () -> Unit,
    onRequestListenerAccess: () -> Unit,
) {
    var showRulesScreen by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val refreshGeneration by viewModel.refreshGeneration.collectAsStateWithLifecycle()
    var uiState by remember { mutableStateOf(NotificationHistoryUiState()) }
    var pendingDeleteItem by remember { mutableStateOf<NotificationHistoryItem?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    val replayOpenAppDialog by viewModel.replayOpenAppDialog.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(NotificationFilterTab.ACTIVE.ordinal) }
    val filterRules by viewModel.rules.collectAsStateWithLifecycle()
    val visibleHistoryItems = uiState.classification.visibleItems
    val hiddenItems = uiState.classification.hiddenItems
    val filteredHistoryItems = uiState.filteredHistoryItems
    val filteredHiddenItems = uiState.filteredHiddenItems
    val activeNotifications = uiState.activeNotifications
    val activeKeys = uiState.activeKeys
    val classification = uiState.classification
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    LaunchedEffect(Unit) {
        viewModel.loadApps()
    }

    LaunchedEffect(Unit) {
        combine(
            viewModel.items,
            viewModel.rules,
            snapshotFlow {
                Triple(listenerEnabled, searchQuery, refreshGeneration)
            },
        ) { items, rules, (enabled, query, generation) ->
            NotificationHistoryQuery(
                items = items,
                rules = rules,
                listenerEnabled = enabled,
                searchQuery = query,
                refreshGeneration = generation,
            )
        }
            .debounce(250)
            .collectLatest { query ->
                val next = withContext(Dispatchers.Default) {
                    computeNotificationHistoryUiState(
                        items = query.items,
                        rules = query.rules,
                        listenerEnabled = query.listenerEnabled,
                        searchQuery = query.searchQuery,
                        activeNotificationsProvider = {
                            viewModel.getActiveNotifications(query.items)
                        },
                        activeKeysProvider = viewModel::getActiveNotificationKeys,
                    )
                }
                uiState = next
            }
    }

    BackHandler {
        when {
            showRulesScreen -> showRulesScreen = false
            showSettingsScreen -> showSettingsScreen = false
            else -> onBack()
        }
    }

    if (showRulesScreen) {
        NotificationRulesScreen(
            rules = filterRules.filter { it.userCreated },
            viewModel = viewModel,
            onBack = { showRulesScreen = false },
            onUpsertRule = viewModel::upsertRule,
            onRemoveRule = viewModel::removeRule,
            onSetRuleEnabled = viewModel::setRuleEnabled,
        )
        return
    }

    if (showSettingsScreen) {
        NotificationFilterSettingsScreen(
            viewModel = viewModel,
            listenerEnabled = listenerEnabled,
            onBack = { showSettingsScreen = false },
            onRequestListenerAccess = onRequestListenerAccess,
        )
        return
    }

    val canClearHistory = selectedTab == NotificationFilterTab.HISTORY.ordinal &&
        visibleHistoryItems.isNotEmpty()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val modifier = Modifier
        .fillMaxSize()
        .nestedScroll(scrollBehavior.nestedScrollConnection)

    fun performHide(item: NotificationHistoryItem, historyId: String? = item.id.takeIf { it.isNotBlank() }) {
        if (!listenerEnabled) {
            onRequestListenerAccess()
            return
        }
        viewModel.hideNotification(item, listenerEnabled)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { SettingsAppBarTitle(stringResource(R.string.notification_history_title)) },
                subtitle = { Text(stringResource(R.string.notification_history_subtitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showRulesScreen = true }) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = stringResource(R.string.notification_filter_rules_action),
                        )
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.notification_filter_more_menu),
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.notification_filter_settings_title)) },
                                onClick = {
                                    showMoreMenu = false
                                    showSettingsScreen = true
                                },
                            )
                        }
                    }
                    if (canClearHistory) {
                        TextButton(onClick = { showClearAllConfirm = true }) {
                            Text(stringResource(R.string.notification_history_clear_all))
                        }
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
            NotificationHistoryFilterBar(
                listenerEnabled = listenerEnabled,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onGrantListenerAccess = onRequestListenerAccess,
            )
            when (NotificationFilterTab.entries[selectedTab]) {
                NotificationFilterTab.ACTIVE -> ActiveNotificationsTab(
                    modifier = modifier,
                    listenerEnabled = listenerEnabled,
                    activeNotifications = activeNotifications,
                    itemMeta = { item -> classification.metaFor(item) },
                    dateFormat = dateFormat,
                    viewModel = viewModel,
                    onHideItem = ::performHide,
                )
                NotificationFilterTab.HISTORY -> HistoryNotificationsTab(
                    modifier = modifier,
                    items = visibleHistoryItems,
                    filteredItems = filteredHistoryItems,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    activeKeys = activeKeys,
                    itemMeta = { item -> classification.metaFor(item) },
                    dateFormat = dateFormat,
                    viewModel = viewModel,
                    onHideItem = ::performHide,
                    onDelete = { pendingDeleteItem = it },
                )
                NotificationFilterTab.HIDDEN -> HiddenNotificationsTab(
                    modifier = modifier,
                    hiddenItems = hiddenItems,
                    filteredItems = filteredHiddenItems,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    activeKeys = activeKeys,
                    itemMeta = { item -> classification.metaFor(item) },
                    dateFormat = dateFormat,
                    viewModel = viewModel,
                    onDelete = { pendingDeleteItem = it },
                )
            }
        }
    }

    pendingDeleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDeleteItem = null },
            title = { Text(stringResource(R.string.notification_history_delete_confirm_title)) },
            text = { Text(stringResource(R.string.notification_history_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem(item.id)
                        pendingDeleteItem = null
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteItem = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(stringResource(R.string.notification_history_clear_all_confirm_title)) },
            text = { Text(stringResource(R.string.notification_history_clear_all_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearAllConfirm = false
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    replayOpenAppDialog?.let { failure ->
        val packageName = failure.packageName.orEmpty()
        val appLabel = viewModel.getCachedAppLabel(packageName) ?: packageName
        AlertDialog(
            onDismissRequest = viewModel::dismissReplayOpenAppDialog,
            title = { Text(stringResource(R.string.notification_history_recycled_title)) },
            text = { Text(stringResource(R.string.notification_history_recycled_message)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.openReplayTargetApp(packageName) },
                ) {
                    Text(stringResource(R.string.notification_history_open_app, appLabel))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissReplayOpenAppDialog) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
fun NotificationHistoryEntryCard(
    itemCount: Int,
    listenerEnabled: Boolean,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
        title = stringResource(R.string.notification_history_entry_title),
        subtitle = when {
            !listenerEnabled -> stringResource(R.string.notification_history_entry_permission_needed)
            itemCount > 0 -> stringResource(R.string.notification_history_entry_summary, itemCount)
            else -> stringResource(R.string.notification_history_entry_desc)
        },
        onClick = onClick,
    )
}
