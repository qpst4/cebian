package com.slideindex.app.ui.notificationhistory

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.popup.WindowDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.notification.NotificationFilterRule
import com.slideindex.app.notification.NotificationHistoryItem
import com.slideindex.app.otp.OtpClipboardHelper
import com.slideindex.app.ui.Md3PickerAppLeading

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NotificationHistoryRow(
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
    val reopenLabel = stringResource(R.string.notification_history_menu_reopen)
    val hideLabel = stringResource(R.string.notification_filter_hide)
    val restoreLabel = stringResource(R.string.notification_history_menu_restore)
    val deleteLabel = stringResource(R.string.notification_history_delete)
    val menuEntry = DropdownEntry(
        items = buildList {
            add(
                DropdownItem(
                    text = reopenLabel,
                    onClick = {
                        showMenu = false
                        onOpen()
                    },
                ),
            )
            if (!item.hidden && matchingRule == null) {
                add(
                    DropdownItem(
                        text = hideLabel,
                        onClick = {
                            showMenu = false
                            onHide()
                        },
                    ),
                )
            }
            if (item.hidden || matchingRule != null) {
                add(
                    DropdownItem(
                        text = restoreLabel,
                        onClick = {
                            showMenu = false
                            onUnhide()
                        },
                    ),
                )
            }
            if (showDeleteAction) {
                add(
                    DropdownItem(
                        text = deleteLabel,
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                    ),
                )
            }
        },
    )

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { showMenu = true },
                ),
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
                        contentDescription = stringResource(R.string.cd_notification_icon),
                        modifier = Modifier.size(40.dp),
                        tint = MiuixTheme.colorScheme.primary,
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
                            style = MiuixTheme.textStyles.title4,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (showHiddenBadge) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = stringResource(R.string.notification_filter_hidden_badge),
                                modifier = Modifier.size(18.dp),
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                            )
                        }
                    }
                    if (item.text.isNotBlank()) {
                        Text(
                            text = item.text,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
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
                                style = MiuixTheme.textStyles.title4,
                                color = MiuixTheme.colorScheme.primary,
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
                                    tint = MiuixTheme.colorScheme.onSurfaceSecondary,
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
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showRestoreAction) {
                    TextButton(
                        text = stringResource(R.string.notification_restore_action),
                        onClick = onUnhide,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
        WindowDropdownPopup(
            entry = menuEntry,
            show = showMenu,
            onDismiss = { showMenu = false },
            onDismissFinished = {},
            maxHeight = null,
            dropdownColors = DropdownDefaults.dropdownColors(),
        )
    }
}
