@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardPermissionHelper
import com.slideindex.app.gesture.GestureActionPermissionAuditor
import com.slideindex.app.gesture.MissingGesturePermission
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun MissingGesturePermissionsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var missing by remember {
        mutableStateOf(GestureActionPermissionAuditor.auditMissingPermissions(context, settings))
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        missing = GestureActionPermissionAuditor.auditMissingPermissions(context, settings)
        if (granted) {
            if (settings.clipboardScreenshotMonitoring) {
                SlideIndexAccessibilityService.accessibilityInstance()?.syncScreenshotMonitoring()
            }
        } else {
            val permission = ClipboardPermissionHelper.mediaReadPermission()
            val activity = context.findActivity()
            if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                Toast.makeText(context, R.string.clipboard_media_read_status_denied, Toast.LENGTH_SHORT).show()
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    DisposableEffect(lifecycleOwner, settings) {
        missing = GestureActionPermissionAuditor.auditMissingPermissions(context, settings)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                missing = GestureActionPermissionAuditor.auditMissingPermissions(context, settings)
                if (settings.clipboardScreenshotMonitoring && ClipboardPermissionHelper.hasMediaReadPermission(context)) {
                    SlideIndexAccessibilityService.accessibilityInstance()?.syncScreenshotMonitoring()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
                        val rowKey = "missing-permission-${item.action.type.name}-${item.requestTag.orEmpty()}-$index"
                        add(
                            settingsCardScopeItem(rowKey) {
                                MissingGesturePermissionRow(
                                    item = item,
                                    onClick = {
                                        if (item.requestTag == GestureActionPermissionAuditor.REQUEST_CLIPBOARD_MEDIA_READ) {
                                            mediaPermissionLauncher.launch(ClipboardPermissionHelper.mediaReadPermission())
                                        } else {
                                            GestureActionPermissionAuditor.requestPermission(context, item)
                                        }
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
