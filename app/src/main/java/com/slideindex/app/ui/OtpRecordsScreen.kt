package com.slideindex.app.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.otp.OtpAutoFillUiLabels
import com.slideindex.app.otp.OtpClipboardHelper
import com.slideindex.app.otp.OtpRecord
import com.slideindex.app.otp.OtpRecordFillStatus
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.miuix.MiuixBottomSheet
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.settings.components.SettingsHintText
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.viewmodel.OtpRecordsViewModel
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import java.text.DateFormat
import java.util.Date

enum class OtpRecordSortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST,
}

class OtpRecordsUi(
    val appendListItems: LazyListScope.() -> Unit,
    val overlays: @Composable () -> Unit,
    val scaffoldActions: @Composable RowScope.() -> Unit,
)

@Composable
fun rememberOtpRecordsUi(
    embeddedInHub: Boolean,
    onOpenTestFlow: (() -> Unit)?,
    viewModel: OtpRecordsViewModel = hiltViewModel(),
): OtpRecordsUi {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val records by viewModel.records.collectAsStateWithLifecycle()
    var sortOrder by remember { mutableStateOf(OtpRecordSortOrder.NEWEST_FIRST) }
    var filterPackage by remember { mutableStateOf<String?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<OtpRecord?>(null) }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

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

    val filterSortActions: @Composable RowScope.() -> Unit = {
        OtpRecordsFilterSortActions(
            sortOrder = sortOrder,
            onShowFilterSheet = { showFilterSheet = true },
            onSortNewest = { sortOrder = OtpRecordSortOrder.NEWEST_FIRST },
            onSortOldest = { sortOrder = OtpRecordSortOrder.OLDEST_FIRST },
        )
    }

    val lazyItems: LazyListScope.() -> Unit = {
        if (embeddedInHub) {
            item(key = "records_toolbar") {
                OtpRecordsEmbeddedToolbar(
                    embeddedInHub = true,
                    sortOrder = sortOrder,
                    onShowFilterSheet = { showFilterSheet = true },
                    onSortNewest = { sortOrder = OtpRecordSortOrder.NEWEST_FIRST },
                    onSortOldest = { sortOrder = OtpRecordSortOrder.OLDEST_FIRST },
                )
            }
        }
        otpRecordsListItems(
            embeddedInHub = embeddedInHub,
            records = records,
            filteredRecords = filteredRecords,
            viewModel = viewModel,
            dateFormat = dateFormat,
            onOpenTestFlow = onOpenTestFlow,
            onCopy = { record ->
                OtpClipboardHelper.copyCode(context, record.code)
                Toast.makeText(
                    context,
                    appContext.getString(R.string.otp_copied_to_clipboard, record.code),
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onDelete = { pendingDelete = it },
        )
    }

    val overlays: @Composable () -> Unit = {
        MiuixBottomSheet(
            show = showFilterSheet,
            title = stringResource(R.string.otp_records_filter),
            onDismissRequest = { showFilterSheet = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
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

        val deleteRecord = pendingDelete
        MiuixConfirmDialog(
            show = deleteRecord != null,
            onDismissRequest = { pendingDelete = null },
            title = stringResource(R.string.otp_records_delete_title),
            message = deleteRecord?.let {
                stringResource(R.string.otp_records_delete_message, it.code)
            },
            confirmText = stringResource(R.string.otp_rules_delete),
            dismissText = stringResource(R.string.shell_panel_close),
            onConfirm = {
                deleteRecord?.let { record ->
                    viewModel.deleteRecord(record.id)
                    pendingDelete = null
                }
            },
        )
    }

    return OtpRecordsUi(
        appendListItems = lazyItems,
        overlays = overlays,
        scaffoldActions = filterSortActions,
    )
}

fun LazyListScope.otpRecordsListItems(
    embeddedInHub: Boolean,
    records: List<OtpRecord>,
    filteredRecords: List<OtpRecord>,
    viewModel: OtpRecordsViewModel,
    dateFormat: DateFormat,
    onOpenTestFlow: (() -> Unit)?,
    onCopy: (OtpRecord) -> Unit,
    onDelete: (OtpRecord) -> Unit,
) {
    if (records.isEmpty()) {
        item(key = "records_empty") {
            OtpRecordsEmptyState(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                onOpenTestFlow = onOpenTestFlow,
                embeddedInHub = embeddedInHub,
            )
        }
        return
    }

    if (embeddedInHub) {
        item(key = "sender_hint") {
            SettingsHintText(stringResource(R.string.otp_records_sender_hint))
        }
    }

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
        groupedCardItems(
            keyPrefix = "otp-records",
            items = filteredRecords.map { record ->
                val appInfo = viewModel.getCachedAppInfo(record.packageName)
                CardItem(key = record.id) {
                    OtpRecordRow(
                        record = record,
                        appInfo = appInfo,
                        timeLabel = dateFormat.format(Date(record.timestampMs)),
                        onCopy = { onCopy(record) },
                        onDelete = { onDelete(record) },
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OtpRecordsScreen(
    onBack: (() -> Unit)? = null,
    onOpenTestFlow: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: OtpRecordsViewModel = hiltViewModel(),
) {
    val recordsUi = rememberOtpRecordsUi(
        embeddedInHub = false,
        onOpenTestFlow = onOpenTestFlow,
        viewModel = viewModel,
    )

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.otp_records_title),
        onBack = onBack,
        actions = recordsUi.scaffoldActions,
    ) {
        recordsUi.appendListItems(this)
    }

    recordsUi.overlays()
}

@Composable
private fun OtpRecordsFilterSortActions(
    sortOrder: OtpRecordSortOrder,
    onShowFilterSheet: () -> Unit,
    onSortNewest: () -> Unit,
    onSortOldest: () -> Unit,
) {
    val newestLabel = stringResource(R.string.otp_records_sort_newest)
    val oldestLabel = stringResource(R.string.otp_records_sort_oldest)
    val sortEntry = remember(sortOrder, newestLabel, oldestLabel) {
        DropdownEntry(
            items = listOf(
                DropdownItem(
                    text = newestLabel,
                    selected = sortOrder == OtpRecordSortOrder.NEWEST_FIRST,
                    onClick = onSortNewest,
                ),
                DropdownItem(
                    text = oldestLabel,
                    selected = sortOrder == OtpRecordSortOrder.OLDEST_FIRST,
                    onClick = onSortOldest,
                ),
            ),
        )
    }
    IconButton(onClick = onShowFilterSheet) {
        Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.otp_records_filter))
    }
    WindowIconDropdownMenu(entry = sortEntry) {
        Icon(
            Icons.Default.SwapVert,
            contentDescription = stringResource(R.string.otp_records_sort),
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun OtpRecordsEmbeddedToolbar(
    embeddedInHub: Boolean,
    sortOrder: OtpRecordSortOrder,
    onShowFilterSheet: () -> Unit,
    onSortNewest: () -> Unit,
    onSortOldest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (embeddedInHub) 0.dp else 20.dp,
                end = if (embeddedInHub) 0.dp else 20.dp,
                top = 4.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.otp_records_title),
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.weight(1f),
        )
        OtpRecordsFilterSortActions(
            sortOrder = sortOrder,
            onShowFilterSheet = onShowFilterSheet,
            onSortNewest = onSortNewest,
            onSortOldest = onSortOldest,
        )
    }
}

@Composable
private fun OtpRecordFilterOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    RadioButtonPreference(
        modifier = Modifier.fillMaxWidth(),
        title = label,
        selected = selected,
        onClick = onClick,
        radioButtonLocation = RadioButtonLocation.End,
    )
}

@Composable
private fun OtpRecordsEmptyState(
    modifier: Modifier = Modifier,
    onOpenTestFlow: (() -> Unit)?,
    embeddedInHub: Boolean = false,
) {
    Column(
        modifier = modifier.padding(horizontal = if (embeddedInHub) 0.dp else 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.MailOutline,
            contentDescription = stringResource(R.string.otp_records_empty_title),
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
                text = stringResource(R.string.otp_records_open_test_flow),
                onClick = onOpenTestFlow,
                modifier = Modifier.padding(top = 12.dp),
            )
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
    val context = LocalContext.current
    val fillLabel = OtpAutoFillUiLabels.formatRecordFillStatus(context, record.autoFillStatus)
    val fillDetail = OtpAutoFillUiLabels.formatRecordFillDetail(
        context,
        record.autoFillStatus,
        record.autoFillReason,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onCopy,
                onLongClick = onDelete,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = record.code,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                OtpRecordFillBadge(
                    label = fillLabel,
                    status = record.autoFillStatus,
                )
            }
            fillDetail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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

@Composable
private fun OtpRecordFillBadge(
    label: String,
    status: OtpRecordFillStatus,
) {
    val containerColor = when (status) {
        OtpRecordFillStatus.LSPOSED -> MaterialTheme.colorScheme.primaryContainer
        OtpRecordFillStatus.ACCESSIBILITY -> MaterialTheme.colorScheme.secondaryContainer
        OtpRecordFillStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        OtpRecordFillStatus.PENDING -> MaterialTheme.colorScheme.tertiaryContainer
        OtpRecordFillStatus.NONE -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when (status) {
        OtpRecordFillStatus.LSPOSED -> MaterialTheme.colorScheme.onPrimaryContainer
        OtpRecordFillStatus.ACCESSIBILITY -> MaterialTheme.colorScheme.onSecondaryContainer
        OtpRecordFillStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        OtpRecordFillStatus.PENDING -> MaterialTheme.colorScheme.onTertiaryContainer
        OtpRecordFillStatus.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
        )
    }
}
