package com.slideindex.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.SwapVert
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.otp.OtpClipboardHelper
import com.slideindex.app.otp.OtpRecord
import com.slideindex.app.ui.viewmodel.OtpRecordsViewModel
import java.text.DateFormat
import java.util.Date

enum class OtpRecordSortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OtpRecordsScreen(
    onBack: (() -> Unit)? = null,
    onOpenTestFlow: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: OtpRecordsViewModel = hiltViewModel(),
) {
    val embeddedInHub = onBack == null

    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    val context = LocalContext.current
    val appContext = context.applicationContext
    val records by viewModel.records.collectAsStateWithLifecycle()
    var sortOrder by remember { mutableStateOf(OtpRecordSortOrder.NEWEST_FIRST) }
    var filterPackage by remember { mutableStateOf<String?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<OtpRecord?>(null) }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(Unit) {
        viewModel.loadApps()
    }

    val packageOptions = remember(records) {
        records.map { it.packageName }.distinct().sorted()
    }
    val filteredRecords = remember(records, filterPackage, sortOrder) {
        val filtered = if (filterPackage == null) {
            records
        } else {
            records.filter { it.packageName == filterPackage }
        }
        when (sortOrder) {
            OtpRecordSortOrder.NEWEST_FIRST -> filtered.sortedByDescending { it.timestampMs }
            OtpRecordSortOrder.OLDEST_FIRST -> filtered.sortedBy { it.timestampMs }
        }
    }

    val recordsListContent: @Composable (Modifier) -> Unit = { listModifier ->
        if (records.isEmpty()) {
            OtpRecordsEmptyState(
                modifier = listModifier,
                onOpenTestFlow = onOpenTestFlow,
            )
        } else {
            LazyColumn(
                modifier = listModifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (filteredRecords.isEmpty()) {
                    item(key = "filter_empty") {
                        Text(
                            text = stringResource(R.string.otp_records_filter_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else {
                    items(filteredRecords, key = { it.id }) { record ->
                        val appInfo = viewModel.getCachedAppInfo(record.packageName)
                        OtpRecordRow(
                            record = record,
                            appInfo = appInfo,
                            timeLabel = dateFormat.format(Date(record.timestampMs)),
                            onCopy = {
                                OtpClipboardHelper.copyCode(context, record.code)
                                Toast.makeText(
                                    context,
                                    appContext.getString(R.string.otp_copied_to_clipboard, record.code),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onDelete = { pendingDelete = record },
                        )
                    }
                }
            }
        }
    }

    if (embeddedInHub) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = contentPadding.calculateBottomPadding()),
        ) {
            OtpRecordsEmbeddedToolbar(
                showSortMenu = showSortMenu,
                onShowFilterSheet = { showFilterSheet = true },
                onShowSortMenu = { showSortMenu = true },
                onDismissSortMenu = { showSortMenu = false },
                onSortNewest = {
                    sortOrder = OtpRecordSortOrder.NEWEST_FIRST
                    showSortMenu = false
                },
                onSortOldest = {
                    sortOrder = OtpRecordSortOrder.OLDEST_FIRST
                    showSortMenu = false
                },
            )
            recordsListContent(Modifier.fillMaxSize())
        }
    } else {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                MediumFlexibleTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.otp_records_title),
                            style = MaterialTheme.typography.titleLargeEmphasized,
                        )
                    },
                    navigationIcon = {
                        onBack?.let { back ->
                            IconButton(onClick = back) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            }
                        }
                    },
                    actions = {
                        OtpRecordsFilterSortActions(
                            showSortMenu = showSortMenu,
                            onShowFilterSheet = { showFilterSheet = true },
                            onShowSortMenu = { showSortMenu = true },
                            onDismissSortMenu = { showSortMenu = false },
                            onSortNewest = {
                                sortOrder = OtpRecordSortOrder.NEWEST_FIRST
                                showSortMenu = false
                            },
                            onSortOldest = {
                                sortOrder = OtpRecordSortOrder.OLDEST_FIRST
                                showSortMenu = false
                            },
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(bottom = contentPadding.calculateBottomPadding()),
            ) {
                recordsListContent(Modifier.fillMaxSize())
            }
        }
    }

    if (showFilterSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.otp_records_filter),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OtpRecordFilterOption(
                    label = stringResource(R.string.otp_records_filter_all),
                    selected = filterPackage == null,
                    onClick = {
                        filterPackage = null
                        showFilterSheet = false
                    },
                )
                packageOptions.forEach { packageName ->
                    val appInfo = viewModel.ensureAppInfo(packageName)
                    OtpRecordFilterOption(
                        label = appInfo?.label ?: packageName,
                        selected = filterPackage == packageName,
                        onClick = {
                            filterPackage = packageName
                            showFilterSheet = false
                        },
                    )
                }
            }
        }
    }

    pendingDelete?.let { record ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.otp_records_delete_title)) },
            text = { Text(stringResource(R.string.otp_records_delete_message, record.code)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRecord(record.id)
                        pendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.otp_rules_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.shell_panel_close))
                }
            },
        )
    }
}

@Composable
private fun OtpRecordsFilterSortActions(
    showSortMenu: Boolean,
    onShowFilterSheet: () -> Unit,
    onShowSortMenu: () -> Unit,
    onDismissSortMenu: () -> Unit,
    onSortNewest: () -> Unit,
    onSortOldest: () -> Unit,
) {
    IconButton(onClick = onShowFilterSheet) {
        Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.otp_records_filter))
    }
    IconButton(onClick = onShowSortMenu) {
        Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.otp_records_sort))
    }
    DropdownMenu(
        expanded = showSortMenu,
        onDismissRequest = onDismissSortMenu,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.otp_records_sort_newest)) },
            onClick = onSortNewest,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.otp_records_sort_oldest)) },
            onClick = onSortOldest,
        )
    }
}

@Composable
private fun OtpRecordsEmbeddedToolbar(
    showSortMenu: Boolean,
    onShowFilterSheet: () -> Unit,
    onShowSortMenu: () -> Unit,
    onDismissSortMenu: () -> Unit,
    onSortNewest: () -> Unit,
    onSortOldest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.otp_records_title),
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.weight(1f),
        )
        OtpRecordsFilterSortActions(
            showSortMenu = showSortMenu,
            onShowFilterSheet = onShowFilterSheet,
            onShowSortMenu = onShowSortMenu,
            onDismissSortMenu = onDismissSortMenu,
            onSortNewest = onSortNewest,
            onSortOldest = onSortOldest,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OtpRecordFilterOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OtpRecordsEmptyState(
    modifier: Modifier = Modifier,
    onOpenTestFlow: (() -> Unit)?,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.MailOutline,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Text(
            text = stringResource(R.string.otp_records_empty_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 20.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.otp_records_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center,
        )
        if (onOpenTestFlow != null) {
            TextButton(
                onClick = onOpenTestFlow,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text(stringResource(R.string.otp_records_open_test_flow))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OtpRecordRow(
    record: OtpRecord,
    appInfo: AppInfo?,
    timeLabel: String,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val sourceLabel = when {
        record.isTest -> stringResource(R.string.otp_records_test_source)
        appInfo != null -> appInfo.label
        else -> record.packageName
    }
    val snippet = record.text.ifBlank { record.title }.ifBlank { record.packageName }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onCopy,
                onLongClick = onDelete,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = record.code,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = sourceLabel,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (snippet.isNotBlank()) {
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            record.ruleName?.let { ruleName ->
                Text(
                    text = stringResource(R.string.otp_records_rule_label, ruleName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
