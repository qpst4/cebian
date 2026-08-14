package com.slideindex.app.ui



import android.content.ClipData

import android.content.ClipboardManager

import android.content.Context

import android.widget.Toast

import com.slideindex.app.ui.HomeLeadingIcons

import com.slideindex.app.ui.miuix.MiuixConfirmDialog

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

import androidx.compose.material3.Icon

import androidx.compose.runtime.Composable

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.res.stringResource

import com.slideindex.app.R

import com.slideindex.app.util.SecureSettingsHelper

import com.slideindex.app.ui.miuix.groupedCardItems

import com.slideindex.app.ui.settings.components.SettingsCardScope

import com.slideindex.app.ui.settings.components.SettingsScreenScaffold

import com.slideindex.app.ui.settings.components.settingsCardScopeItem



@OptIn(ExperimentalMaterial3ExpressiveApi::class)

@Composable

fun AppKeepAliveSettingsScreen(

    hideFromRecents: Boolean,

    batteryOptimizationExempt: Boolean,

    accessibilityKeepAliveEnabled: Boolean,

    writeSecureSettingsGranted: Boolean,

    shizukuGranted: Boolean,

    onBack: () -> Unit,

    onRequestBatteryOptimization: () -> Unit,

    onRequestAutoStart: () -> Unit,

    onHideFromRecentsChange: (Boolean) -> Unit,

    onAccessibilityKeepAliveChange: (Boolean) -> Unit,

    onRequestSecureSettingsGrant: () -> Boolean,

) {

    val context = LocalContext.current

    var showAdbDialog by remember { mutableStateOf(false) }

    val adbCommand = remember { SecureSettingsHelper.adbGrantCommand(context) }



    val copiedMessage = stringResource(R.string.secure_settings_adb_copied)



    SettingsScreenScaffold(

        title = stringResource(R.string.app_keep_alive_title),

        subtitle = stringResource(R.string.app_keep_alive_desc),

        onBack = onBack,

    ) {

        groupedCardItems(

            keyPrefix = "app-keep-alive",

            items = buildList {

                add(

                    settingsCardScopeItem("battery-optimization") {

                        SettingSwitchRow(

                            title = stringResource(R.string.battery_optimization_title),

                            subtitle = stringResource(R.string.battery_optimization_desc),

                            checked = batteryOptimizationExempt,

                            enabled = true,

                            onCheckedChange = { onRequestBatteryOptimization() },

                        )

                    },

                )

                add(

                    settingsCardScopeItem("auto-start") {

                        SettingLinkRow(

                            title = stringResource(R.string.auto_start_title),

                            subtitle = stringResource(R.string.auto_start_desc),

                            onClick = onRequestAutoStart,

                        )

                    },

                )

                add(

                    settingsCardScopeItem("hide-from-recents") {

                        SettingSwitchRow(

                            title = stringResource(R.string.hide_from_recents_title),

                            subtitle = stringResource(R.string.hide_from_recents_desc),

                            checked = hideFromRecents,

                            enabled = true,

                            onCheckedChange = onHideFromRecentsChange,

                        )

                    },

                )

                add(

                    settingsCardScopeItem("secure-settings") {

                        SettingSwitchRow(

                            title = stringResource(R.string.secure_settings_title),

                            subtitle = stringResource(R.string.secure_settings_desc),

                            checked = accessibilityKeepAliveEnabled,

                            enabled = true,

                            onCheckedChange = { enabled ->

                                if (!enabled) {

                                    onAccessibilityKeepAliveChange(false)

                                    return@SettingSwitchRow

                                }

                                if (writeSecureSettingsGranted) {

                                    onAccessibilityKeepAliveChange(true)

                                    return@SettingSwitchRow

                                }

                                if (shizukuGranted) {

                                    val granted = onRequestSecureSettingsGrant()

                                    if (granted) {

                                        onAccessibilityKeepAliveChange(true)

                                    } else {

                                        showAdbDialog = true

                                    }

                                } else {

                                    showAdbDialog = true

                                }

                            },

                        )

                    },

                )

            },

        )

    }



    MiuixConfirmDialog(

        show = showAdbDialog,

        onDismissRequest = { showAdbDialog = false },

        title = stringResource(R.string.secure_settings_adb_dialog_title),

        message = stringResource(R.string.secure_settings_adb_dialog_message, adbCommand),

        confirmText = stringResource(R.string.secure_settings_adb_copy),

        dismissOnConfirm = false,

        onConfirm = {

            copyToClipboard(context, adbCommand)

            Toast.makeText(

                context,

                copiedMessage,

                Toast.LENGTH_SHORT,

            ).show()

        },

        dismissText = stringResource(R.string.confirm),

    )

}



@Composable

fun SettingsCardScope.AppKeepAliveEntryCard(

    batteryOptimizationExempt: Boolean,

    hideFromRecents: Boolean,

    accessibilityKeepAliveEnabled: Boolean,

    outlinedLeadingIcons: Boolean = false,

    onClick: () -> Unit,

) {

    val subtitle = when {

        batteryOptimizationExempt && hideFromRecents && accessibilityKeepAliveEnabled ->

            stringResource(R.string.app_keep_alive_entry_summary_all)

        batteryOptimizationExempt && hideFromRecents ->

            stringResource(R.string.app_keep_alive_entry_summary_all)

        batteryOptimizationExempt ->

            stringResource(R.string.app_keep_alive_entry_summary_battery)

        hideFromRecents ->

            stringResource(R.string.app_keep_alive_entry_summary_recents)

        accessibilityKeepAliveEnabled ->

            stringResource(R.string.secure_settings_enabled_summary)

        else -> stringResource(R.string.app_keep_alive_entry_desc)

    }

    SettingNavigationRow(

        icon = { label ->

            Icon(HomeLeadingIcons.batteryKeepAlive(outlinedLeadingIcons), contentDescription = label)

        },

        title = stringResource(R.string.app_keep_alive_title),

        subtitle = subtitle,

        onClick = onClick,

    )

}



private fun copyToClipboard(context: Context, text: String) {

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    clipboard.setPrimaryClip(ClipData.newPlainText("adb_command", text))

}


