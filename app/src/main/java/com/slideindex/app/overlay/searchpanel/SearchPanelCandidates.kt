package com.slideindex.app.overlay.searchpanel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo

private val AppCandidateItemWidth = 56.dp
private val AppCandidateIconSize = 40.dp

@Composable
fun SearchPanelLinkCandidates(
    urls: List<String>,
    onOpenUrl: (url: String, longPressTriggered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    if (urls.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        urls.forEach { url ->
            val host = remember(url) { urlHostLabel(url) }
            val label = stringResource(R.string.search_panel_open_link_host, host)
            SearchPanelCandidateChip(
                label = label,
                leading = {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                longPressEnabled = longPressEnabled,
                onClick = { onOpenUrl(url, false) },
                onLongClick = { onOpenUrl(url, true) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchPanelAppCandidates(
    apps: List<AppInfo>,
    onLaunchApp: (AppInfo, longPressTriggered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    if (apps.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        apps.forEach { app ->
            Column(
                modifier = Modifier
                    .width(AppCandidateItemWidth)
                    .then(
                        if (longPressEnabled) {
                            Modifier.combinedClickable(
                                onClick = { onLaunchApp(app, false) },
                                onLongClick = { onLaunchApp(app, true) },
                            )
                        } else {
                            Modifier.combinedClickable(onClick = { onLaunchApp(app, false) })
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val pm = androidx.compose.ui.platform.LocalContext.current.packageManager
                val iconBitmap = androidx.compose.runtime.remember(app.packageName) {
                    val drawable = try {
                        pm.getApplicationIcon(app.packageName)
                    } catch (_: Exception) {
                        null
                    }
                    drawable?.toBitmap(96, 96)?.asImageBitmap()
                }
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = app.label,
                        modifier = Modifier
                            .size(AppCandidateIconSize)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = app.label,
                        modifier = Modifier
                            .size(AppCandidateIconSize)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                }
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchPanelCandidateChip(
    label: String,
    leading: @Composable () -> Unit,
    longPressEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val clickModifier = if (longPressEnabled) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        Modifier.combinedClickable(onClick = onClick)
    }
    Row(
        modifier = Modifier
            .then(clickModifier)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun urlHostLabel(url: String): String {
    val host = url.substringAfter("://", missingDelimiterValue = url)
        .substringBefore('/')
        .substringBefore('?')
    return host.ifBlank { url }
}
