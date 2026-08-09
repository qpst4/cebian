package com.slideindex.app.ui

import com.slideindex.app.ui.viewmodel.NotificationHistoryViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Tune
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixScaffoldSearchTabBottomContent
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import androidx.compose.material3.DropdownMenu
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.notification.NotificationHistoryItem
import com.slideindex.app.notification.NotificationHistoryUiState
import com.slideindex.app.notification.computeNotificationHistoryUiState
import com.slideindex.app.ui.notificationhistory.NotificationFilterTab
import com.slideindex.app.ui.notificationhistory.activeNotificationsItems
import com.slideindex.app.ui.notificationhistory.hiddenNotificationsItems
import com.slideindex.app.ui.notificationhistory.historyNotificationsItems
import com.slideindex.app.ui.notificationhistory.NotificationHistoryTabRow
import com.slideindex.app.ui.notificationhistory.notificationHistoryFilterBarItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
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
    onOpenRules: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestListenerAccess: () -> Unit,
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val refreshGeneration by viewModel.refreshGeneration.collectAsStateWithLifecycle()
    var uiState by remember { mutableStateOf(NotificationHistoryUiState()) }
    var pendingDeleteItem by remember { mutableStateOf<NotificationHistoryItem?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    val replayOpenAppDialog by viewModel.replayOpenAppDialog.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(NotificationFilterTab.ACTIVE.ordinal) }
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

    val canClearHistory = selectedTab == NotificationFilterTab.HISTORY.ordinal &&
        visibleHistoryItems.isNotEmpty()
    val showSearchUi = selectedTab != NotificationFilterTab.ACTIVE.ordinal

    LaunchedEffect(selectedTab) {
        if (!showSearchUi) {
            searchExpanded = false
        }
    }

    val handleBack: () -> Unit = {
        if (
            !consumeExpandableSearchBack(
                expanded = searchExpanded,
                query = searchQuery,
                onExpandedChange = { searchExpanded = it },
                onQueryChange = { searchQuery = it },
            )
        ) {
            onBack()
        }
    }

    fun performHide(item: NotificationHistoryItem, historyId: String? = item.id.takeIf { it.isNotBlank() }) {
        if (!listenerEnabled) {
            onRequestListenerAccess()
            return
        }
        viewModel.hideNotification(item, listenerEnabled)
    }

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.notification_history_title),
        subtitle = stringResource(R.string.notification_history_subtitle),
        onBack = handleBack,
        actions = {
            if (showSearchUi) {
                MiuixExpandableSearchIconAction(
                    expanded = searchExpanded,
                    query = searchQuery,
                    onExpandedChange = { searchExpanded = it },
                    onQueryChange = { searchQuery = it },
                )
            }
            IconButton(onClick = onOpenRules) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = stringResource(R.string.notification_filter_rules_action),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.notification_filter_more_menu),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                ) {
                    if (canClearHistory) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.notification_history_clear_all)) },
                            onClick = {
                                showMoreMenu = false
                                showClearAllConfirm = true
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.notification_filter_settings_title)) },
                        onClick = {
                            showMoreMenu = false
                            onOpenSettings()
                        },
                    )
                }
            }
        },
        bottomContent = {
            MiuixScaffoldSearchTabBottomContent(
                searchExpanded = showSearchUi && searchExpanded,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                focusRequester = searchFocusRequester,
                hintResId = R.string.notification_history_search_hint,
                tabContent = {
                    NotificationHistoryTabRow(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                    )
                },
            )
        },
    ) {
        notificationHistoryFilterBarItems(
            listenerEnabled = listenerEnabled,
            onGrantListenerAccess = onRequestListenerAccess,
        )
        when (NotificationFilterTab.entries[selectedTab]) {
            NotificationFilterTab.ACTIVE -> activeNotificationsItems(
                listenerEnabled = listenerEnabled,
                activeNotifications = activeNotifications,
                itemMeta = { item -> classification.metaFor(item) },
                dateFormat = dateFormat,
                viewModel = viewModel,
                onHideItem = ::performHide,
            )
            NotificationFilterTab.HISTORY -> historyNotificationsItems(
                items = visibleHistoryItems,
                filteredItems = filteredHistoryItems,
                searchQuery = searchQuery,
                activeKeys = activeKeys,
                itemMeta = { item -> classification.metaFor(item) },
                dateFormat = dateFormat,
                viewModel = viewModel,
                onHideItem = ::performHide,
                onDelete = { pendingDeleteItem = it },
            )
            NotificationFilterTab.HIDDEN -> hiddenNotificationsItems(
                hiddenItems = hiddenItems,
                filteredItems = filteredHiddenItems,
                searchQuery = searchQuery,
                activeKeys = activeKeys,
                itemMeta = { item -> classification.metaFor(item) },
                dateFormat = dateFormat,
                viewModel = viewModel,
                onDelete = { pendingDeleteItem = it },
            )
        }
    }

    val deleteItem = pendingDeleteItem
    MiuixConfirmDialog(
        show = deleteItem != null,
        onDismissRequest = { pendingDeleteItem = null },
        title = stringResource(R.string.notification_history_delete_confirm_title),
        message = stringResource(R.string.notification_history_delete_confirm_message),
        onConfirm = {
            deleteItem?.let { item ->
                viewModel.deleteItem(item.id)
                pendingDeleteItem = null
            }
        },
    )

    MiuixConfirmDialog(
        show = showClearAllConfirm,
        onDismissRequest = { showClearAllConfirm = false },
        title = stringResource(R.string.notification_history_clear_all_confirm_title),
        message = stringResource(R.string.notification_history_clear_all_confirm_message),
        onConfirm = {
            viewModel.clearAll()
            showClearAllConfirm = false
        },
    )

    replayOpenAppDialog?.let { failure ->
        val packageName = failure.packageName.orEmpty()
        val appLabel = viewModel.getCachedAppLabel(packageName) ?: packageName
        MiuixConfirmDialog(
            show = true,
            onDismissRequest = viewModel::dismissReplayOpenAppDialog,
            title = stringResource(R.string.notification_history_recycled_title),
            message = stringResource(R.string.notification_history_recycled_message),
            confirmText = stringResource(R.string.notification_history_open_app, appLabel),
            onConfirm = { viewModel.openReplayTargetApp(packageName) },
        )
    }
}

@Composable
fun SettingsCardScope.NotificationHistoryEntryCard(
    itemCount: Int,
    listenerEnabled: Boolean,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label ->
            Icon(HubLeadingIcons.notificationHistory(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.notification_history_entry_title),
        subtitle = when {
            !listenerEnabled -> stringResource(R.string.notification_history_entry_permission_needed)
            itemCount > 0 -> stringResource(R.string.notification_history_entry_summary, itemCount)
            else -> stringResource(R.string.notification_history_entry_desc)
        },
        onClick = onClick,
    )
}
