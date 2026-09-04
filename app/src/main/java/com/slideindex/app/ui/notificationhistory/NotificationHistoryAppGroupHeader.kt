package com.slideindex.app.ui.notificationhistory

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.notification.NotificationHistoryItem
import com.slideindex.app.ui.Md3PickerAppLeading
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NotificationHistoryAppGroupHeader(
    appInfo: AppInfo?,
    packageName: String,
    count: Int,
    latestItem: NotificationHistoryItem,
    timeLabel: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val appLabel = appInfo?.label ?: packageName
    val latestPreview = latestItem.title.ifBlank { latestItem.text }.ifBlank { appLabel }
    val showStack = !expanded && count >= 2
    val stackLayers = if (showStack) minOf(count - 1, 2) else 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        if (showStack) {
            for (layer in stackLayers downTo 1) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = (layer * 8).dp)
                        .offset(y = (layer * 6).dp)
                        .alpha(0.25f + (stackLayers - layer + 1) * 0.12f),
                ) {
                    Spacer(Modifier.height(76.dp))
                }
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onToggle),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box {
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
                    if (count >= 2) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .background(MiuixTheme.colorScheme.error, CircleShape)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (count > 99) "99+" else count.toString(),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onError,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = appLabel,
                        style = MiuixTheme.textStyles.title4,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.notification_history_group_summary, count),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!expanded) {
                        Text(
                            text = stringResource(R.string.notification_history_group_latest_preview, latestPreview),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = buildString {
                            append(timeLabel)
                            append(" · ")
                            append(
                                stringResource(
                                    if (expanded) {
                                        R.string.notification_history_group_tap_collapse
                                    } else {
                                        R.string.notification_history_group_tap_expand
                                    },
                                ),
                            )
                        },
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.cd_collapse_section else R.string.cd_expand_section,
                    ),
                    modifier = Modifier.size(24.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
    }
}
