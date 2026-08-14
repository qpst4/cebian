@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureActionPermissionAuditor
import com.slideindex.app.gesture.MissingGesturePermission
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

@Composable
fun MissingGesturePermissionsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val missing = GestureActionPermissionAuditor.auditMissingPermissions(context, settings)
    val emptyHint = stringResource(R.string.missing_permissions_empty)
    val sectionTitle = stringResource(R.string.missing_permissions_section)

    SettingsScreenScaffold(
        title = stringResource(R.string.missing_permissions_title),
        subtitle = stringResource(R.string.missing_permissions_subtitle),
        onBack = onBack,
    ) {
        if (missing.isEmpty()) {
            settingsLazyHint(key = "missing-permissions-empty", text = emptyHint)
        } else {
            settingsLazySmallTitle(key = "missing-permissions-section", title = sectionTitle)
            groupedCardItems(
                keyPrefix = "missing-permissions",
                items = buildList {
                    missing.forEachIndexed { index, item ->
                        add(
                            settingsCardScopeItem("missing-permission-$index") {
                                MissingGesturePermissionRow(
                                    item = item,
                                    onClick = {
                                        GestureActionPermissionAuditor.requestPermission(context, item)
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SettingsCardScope.MissingGesturePermissionRow(
    item: MissingGesturePermission,
    onClick: () -> Unit,
) {
    val subtitle = buildString {
        item.actionDescription?.let {
            append(it)
            append('\n')
        }
        append(item.permissionHint)
    }
    SettingNavigationRow(
        icon = { label ->
            Icon(
                imageVector = gestureActionIcon(item.action),
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        title = item.actionLabel,
        subtitle = subtitle,
        onClick = onClick,
    )
}

@Composable
fun SettingsCardScope.MissingPermissionsEntryCard(
    missingCount: Int,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label ->
            Icon(Icons.Default.Warning, contentDescription = label, tint = MaterialTheme.colorScheme.error)
        },
        title = stringResource(R.string.missing_permissions_entry_title),
        subtitle = if (missingCount > 0) {
            pluralStringResource(
                R.plurals.missing_permissions_entry_desc_count,
                missingCount,
                missingCount,
            )
        } else {
            stringResource(R.string.missing_permissions_entry_desc_none)
        },
        onClick = onClick,
        enabled = missingCount > 0,
    )
}
