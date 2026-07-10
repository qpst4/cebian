package com.slideindex.app.ui

import com.slideindex.app.ui.viewmodel.NotificationHistoryViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.notification.ActiveNotificationEntry
import com.slideindex.app.notification.NotificationFilterRule
import com.slideindex.app.notification.NotificationHistoryItem
import com.slideindex.app.notification.NotificationHistoryClassification
import com.slideindex.app.notification.NotificationHistoryItemMeta
import com.slideindex.app.notification.NotificationHistoryUiState
import com.slideindex.app.notification.computeNotificationHistoryUiState
import com.slideindex.app.otp.OtpClipboardHelper
import java.text.DateFormat
import java.util.Date
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext

private enum class NotificationFilterTab {
    ACTIVE,
    HISTORY,
    HIDDEN,
}

private data class NotificationHistoryQuery(
    val items: List<NotificationHistoryItem>,
    val rules: List<NotificationFilterRule>,
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

    val onRefreshActive: () -> Unit = { viewModel.refreshActive() }

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
    val listModifier = Modifier
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
            if (!listenerEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    NotificationHistoryPermissionCard(onGrant = onRequestListenerAccess)
                }
            }
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == NotificationFilterTab.ACTIVE.ordinal,
                    onClick = { selectedTab = NotificationFilterTab.ACTIVE.ordinal },
                    text = { Text(stringResource(R.string.notification_filter_tab_active)) },
                )
                Tab(
                    selected = selectedTab == NotificationFilterTab.HISTORY.ordinal,
                    onClick = { selectedTab = NotificationFilterTab.HISTORY.ordinal },
                    text = { Text(stringResource(R.string.notification_filter_tab_history)) },
                )
                Tab(
                    selected = selectedTab == NotificationFilterTab.HIDDEN.ordinal,
                    onClick = { selectedTab = NotificationFilterTab.HIDDEN.ordinal },
                    text = { Text(stringResource(R.string.notification_filter_history_hidden)) },
                )
            }
            when (NotificationFilterTab.entries[selectedTab]) {
                NotificationFilterTab.ACTIVE -> ActiveNotificationsTab(
                    listModifier = listModifier,
                    listenerEnabled = listenerEnabled,
                    activeNotifications = activeNotifications,
                    itemMeta = { item -> classification.metaFor(item) },
                    dateFormat = dateFormat,
                    viewModel = viewModel,
                    onHideItem = ::performHide,
                )
                NotificationFilterTab.HISTORY -> HistoryNotificationsTab(
                    listModifier = listModifier,
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
                    listModifier = listModifier,
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
private fun rememberResolvableAppInfo(
    viewModel: NotificationHistoryViewModel,
    packageName: String,
): AppInfo? {
    var appInfo by remember(packageName) {
        mutableStateOf(viewModel.getCachedAppInfo(packageName))
    }
    LaunchedEffect(packageName) {
        if (appInfo == null) {
            appInfo = viewModel.resolveAppInfo(packageName)
        }
    }
    return appInfo
}

@Composable
private fun ActiveNotificationsTab(
    listModifier: Modifier,
    listenerEnabled: Boolean,
    activeNotifications: List<ActiveNotificationEntry>,
    itemMeta: (NotificationHistoryItem) -> NotificationHistoryItemMeta,
    dateFormat: DateFormat,
    viewModel: NotificationHistoryViewModel,
    onHideItem: (NotificationHistoryItem, String?) -> Unit,
) {
    LazyColumn(
        modifier = listModifier
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!listenerEnabled) {
            item(key = "active_permission_hint") {
                Text(
                    text = stringResource(R.string.notification_history_permission_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else if (activeNotifications.isEmpty()) {
            item(key = "active_empty") {
                Text(
                    text = stringResource(R.string.notification_filter_active_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            items(activeNotifications, key = { it.key }) { entry ->
                val historyItem = entry.historyItem
                val displayItem = historyItem ?: entry.toHistoryItem()
                val meta = historyItem?.let(itemMeta) ?: itemMeta(displayItem)
                val appInfo = rememberResolvableAppInfo(viewModel, entry.packageName)
                NotificationHistoryRow(
                    item = displayItem,
                    appInfo = appInfo,
                    timeLabel = dateFormat.format(Date(entry.postedAtMs)),
                    isActiveInShade = false,
                    matchingRule = meta.matchingHideRule,
                    showHiddenBadge = meta.isHidden,
                    showRestoreAction = meta.isHidden,
                    showDeleteAction = historyItem != null,
                    onOpen = { viewModel.replayActive(entry) },
                    onHide = {
                        onHideItem(displayItem, historyItem?.id)
                    },
                    onUnhide = {
                        viewModel.restoreSnoozed(historyItem ?: displayItem)
                    },
                    onDelete = {
                        historyItem?.let { item -> viewModel.deleteItem(item.id) }
                    },
                )
            }
        }
    }
}

@Composable
private fun HistoryNotificationsTab(
    listModifier: Modifier,
    items: List<NotificationHistoryItem>,
    filteredItems: List<NotificationHistoryItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    activeKeys: Set<String>,
    itemMeta: (NotificationHistoryItem) -> NotificationHistoryItemMeta,
    dateFormat: DateFormat,
    viewModel: NotificationHistoryViewModel,
    onHideItem: (NotificationHistoryItem, String?) -> Unit,
    onDelete: (NotificationHistoryItem) -> Unit,
) {
    LazyColumn(
        modifier = listModifier
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (items.isEmpty()) {
            item(key = "history_empty") {
                Text(
                    text = stringResource(R.string.notification_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            item(key = "search") {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    hintResId = R.string.notification_history_search_hint,
                )
            }
            if (filteredItems.isEmpty()) {
                item(key = "search_empty") {
                    Text(
                        text = stringResource(R.string.notification_history_search_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    val meta = itemMeta(item)
                    val appInfo = rememberResolvableAppInfo(viewModel, item.packageName)
                    NotificationHistoryRow(
                        item = item,
                        appInfo = appInfo,
                        timeLabel = dateFormat.format(Date(item.postedAtMs)),
                        isActiveInShade = item.notificationKey?.let { it in activeKeys } == true,
                        matchingRule = meta.matchingHideRule,
                        showHiddenBadge = meta.isHidden,
                        showRestoreAction = false,
                        onOpen = { viewModel.replay(item) },
                        onHide = { onHideItem(item, item.id) },
                        onUnhide = {},
                        onDelete = { onDelete(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HiddenNotificationsTab(
    listModifier: Modifier,
    hiddenItems: List<NotificationHistoryItem>,
    filteredItems: List<NotificationHistoryItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    activeKeys: Set<String>,
    itemMeta: (NotificationHistoryItem) -> NotificationHistoryItemMeta,
    dateFormat: DateFormat,
    viewModel: NotificationHistoryViewModel,
    onDelete: (NotificationHistoryItem) -> Unit,
) {
    LazyColumn(
        modifier = listModifier
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (hiddenItems.isEmpty()) {
            item(key = "hidden_empty") {
                Text(
                    text = stringResource(R.string.notification_filter_hidden_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            item(key = "hidden_search") {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    hintResId = R.string.notification_history_search_hint,
                )
            }
            if (filteredItems.isEmpty()) {
                item(key = "hidden_search_empty") {
                    Text(
                        text = stringResource(R.string.notification_history_search_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    val appInfo = rememberResolvableAppInfo(viewModel, item.packageName)
                    NotificationHistoryRow(
                        item = item,
                        appInfo = appInfo,
                        timeLabel = dateFormat.format(Date(item.postedAtMs)),
                        isActiveInShade = item.notificationKey?.let { it in activeKeys } == true,
                        matchingRule = itemMeta(item).matchingHideRule,
                        showHiddenBadge = true,
                        showRestoreAction = true,
                        onOpen = { viewModel.replay(item) },
                        onHide = {},
                        onUnhide = { viewModel.restoreSnoozed(item) },
                        onDelete = { onDelete(item) },
                    )
                }
            }
        }
    }
}

private fun ActiveNotificationEntry.toHistoryItem(): NotificationHistoryItem {
    return NotificationHistoryItem(
        packageName = packageName,
        title = title,
        text = text,
        postedAtMs = postedAtMs,
        intentUri = null,
        notificationKey = key,
    )
}

@Composable
private fun NotificationHistoryPermissionCard(onGrant: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.notification_history_permission_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.notification_history_permission_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onGrant) {
                Text(stringResource(R.string.notification_history_permission_grant))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationHistoryRow(
    item: NotificationHistoryItem,
    appInfo: AppInfo?,
    timeLabel: String,
    isActiveInShade: Boolean,
    matchingRule: NotificationFilterRule?,
    showHiddenBadge: Boolean = item.hidden,
    showDeleteAction: Boolean = true,
    showRestoreAction: Boolean = false,
    onOpen: () -> Unit,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val displayTitle = item.title.ifBlank { appInfo?.label ?: item.packageName }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { showMenu = true },
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (appInfo != null) {
                    Md3PickerAppLeading(appInfo)
                } else {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (showHiddenBadge) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = stringResource(R.string.notification_filter_hidden_badge),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (item.text.isNotBlank()) {
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!item.extractedCode.isNullOrBlank()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.otp_extracted_code_label, item.extractedCode!!),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            IconButton(
                                onClick = {
                                    OtpClipboardHelper.copyCode(context, item.extractedCode!!)
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.otp_copy_action),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = buildString {
                            append(appInfo?.label ?: item.packageName)
                            append(" · ")
                            append(timeLabel)
                            if (isActiveInShade) {
                                append(" · ")
                                append(stringResource(R.string.notification_filter_active_in_shade))
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showRestoreAction) {
                    TextButton(
                        onClick = onUnhide,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.notification_restore_action),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.notification_history_menu_reopen)) },
                onClick = {
                    showMenu = false
                    onOpen()
                },
            )
            if (!item.hidden && matchingRule == null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.notification_filter_hide)) },
                    onClick = {
                        showMenu = false
                        onHide()
                    },
                )
            }
            if (item.hidden || matchingRule != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.notification_history_menu_restore)) },
                    onClick = {
                        showMenu = false
                        onUnhide()
                    },
                )
            }
            if (showDeleteAction) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.notification_history_delete)) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                )
            }
        }
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
