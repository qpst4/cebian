package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.PrivilegeMode
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingLinkRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PrivilegeModeSettingsScreen(
    privilegeMode: PrivilegeMode,
    shizukuGranted: Boolean,
    rootAvailable: Boolean,
    onBack: () -> Unit,
    onPrivilegeModeChange: (PrivilegeMode) -> Unit,
    onRequestShizuku: () -> Unit,
) {
    SettingsScreenScaffold(
        title = stringResource(R.string.privilege_mode_title),
        subtitle = stringResource(R.string.privilege_mode_desc),
        onBack = onBack,
    ) {
        groupedCardItems(
            keyPrefix = "privilege-mode",
            items = buildList {
                add(
                    settingsCardScopeItem("privilege-mode-shizuku") {
                        SettingRadioRow(
                            title = stringResource(R.string.privilege_mode_shizuku),
                            subtitle = stringResource(R.string.privilege_mode_shizuku_desc),
                            selected = privilegeMode == PrivilegeMode.SHIZUKU,
                            onClick = { onPrivilegeModeChange(PrivilegeMode.SHIZUKU) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("privilege-mode-root") {
                        SettingRadioRow(
                            title = stringResource(R.string.privilege_mode_root),
                            subtitle = stringResource(R.string.privilege_mode_root_desc),
                            selected = privilegeMode == PrivilegeMode.ROOT,
                            onClick = { onPrivilegeModeChange(PrivilegeMode.ROOT) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("privilege-mode-status") {
                        val statusText = when (privilegeMode) {
                            PrivilegeMode.SHIZUKU ->
                                if (shizukuGranted) {
                                    stringResource(R.string.privilege_mode_status_shizuku_ready)
                                } else {
                                    stringResource(R.string.privilege_mode_status_shizuku_missing)
                                }
                            PrivilegeMode.ROOT ->
                                if (rootAvailable) {
                                    stringResource(R.string.privilege_mode_status_root_ready)
                                } else {
                                    stringResource(R.string.privilege_mode_status_root_missing)
                                }
                        }
                        SettingLinkRow(
                            title = stringResource(R.string.privilege_mode_status_title),
                            subtitle = statusText,
                            onClick = {
                                if (privilegeMode == PrivilegeMode.SHIZUKU && !shizukuGranted) {
                                    onRequestShizuku()
                                }
                            },
                        )
                    },
                )
            },
        )
    }
}

@Composable
fun SettingsCardScope.PrivilegeModeEntryCard(
    privilegeMode: PrivilegeMode,
    privilegedAccessGranted: Boolean,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    val subtitle = when (privilegeMode) {
        PrivilegeMode.SHIZUKU ->
            if (privilegedAccessGranted) {
                stringResource(R.string.privilege_mode_entry_summary_shizuku_ready)
            } else {
                stringResource(R.string.privilege_mode_entry_summary_shizuku_missing)
            }
        PrivilegeMode.ROOT ->
            if (privilegedAccessGranted) {
                stringResource(R.string.privilege_mode_entry_summary_root_ready)
            } else {
                stringResource(R.string.privilege_mode_entry_summary_root_missing)
            }
    }
    SettingNavigationRow(
        icon = { label ->
            Icon(HomeLeadingIcons.privilegeMode(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.privilege_mode_title),
        subtitle = subtitle,
        onClick = onClick,
    )
}
