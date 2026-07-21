@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureActionPermissionAuditor
import com.slideindex.app.gesture.MissingGesturePermission
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.gestureActionIcon
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold

@Composable
fun MissingGesturePermissionsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val missing = GestureActionPermissionAuditor.auditMissingPermissions(context, settings)

    SettingsScreenScaffold(
        title = stringResource(R.string.missing_permissions_title),
        subtitle = stringResource(R.string.missing_permissions_subtitle),
        onBack = onBack,
    ) {
        if (missing.isEmpty()) {
            SettingsHintText(stringResource(R.string.missing_permissions_empty))
        } else {
            Text(
                text = stringResource(R.string.missing_permissions_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            SettingsCard {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(missing, key = { "${it.actionLabel}:${it.permissionHint}" }) { item ->
                        MissingGesturePermissionRow(
                            item = item,
                            onClick = {
                                GestureActionPermissionAuditor.requestPermission(context, item.action)
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun MissingGesturePermissionRow(
    item: MissingGesturePermission,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = gestureActionIcon(item.action),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = item.actionLabel, style = MaterialTheme.typography.titleMedium)
            item.actionDescription?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = item.permissionHint,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
fun MissingPermissionsEntryCard(
    missingCount: Int,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label ->
            Icon(Icons.Default.Warning, contentDescription = label, tint = MaterialTheme.colorScheme.error)
        },
        title = stringResource(R.string.missing_permissions_entry_title),
        subtitle = if (missingCount > 0) {
            stringResource(R.string.missing_permissions_entry_desc_count, missingCount)
        } else {
            stringResource(R.string.missing_permissions_entry_desc_none)
        },
        onClick = onClick,
        enabled = missingCount > 0,
    )
}
