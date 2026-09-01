package com.slideindex.app.privilege

import com.slideindex.app.R
import com.slideindex.app.settings.PrivilegeMode

object PrivilegeUiStrings {
    fun shellPanelLabelRes(): Int =
        when (PrivilegeGateway.mode) {
            PrivilegeMode.ROOT -> R.string.shell_panel_root_label
            PrivilegeMode.SHIZUKU -> R.string.shell_panel_shizuku_label
        }

    fun shellPanelActiveRes(): Int =
        when (PrivilegeGateway.mode) {
            PrivilegeMode.ROOT -> R.string.shell_panel_root_active
            PrivilegeMode.SHIZUKU -> R.string.shell_panel_shizuku_active
        }

    fun shellPanelInactiveRes(): Int =
        when (PrivilegeGateway.mode) {
            PrivilegeMode.ROOT -> R.string.shell_panel_root_inactive
            PrivilegeMode.SHIZUKU -> R.string.shell_panel_shizuku_inactive
        }

    fun shellPanelActiveDescRes(): Int =
        when (PrivilegeGateway.mode) {
            PrivilegeMode.ROOT -> R.string.shell_panel_root_active_desc
            PrivilegeMode.SHIZUKU -> R.string.shell_panel_shizuku_active_desc
        }

    fun shellPanelInactiveDescRes(): Int =
        when (PrivilegeGateway.mode) {
            PrivilegeMode.ROOT -> R.string.shell_panel_root_inactive_desc
            PrivilegeMode.SHIZUKU -> R.string.shell_panel_shizuku_inactive_desc
        }

    fun shellPanelGrantTitleRes(): Int =
        when (PrivilegeGateway.mode) {
            PrivilegeMode.ROOT -> R.string.privilege_mode_root
            PrivilegeMode.SHIZUKU -> R.string.permission_shizuku_grant
        }

    fun shellPanelGrantDescRes(): Int =
        when (PrivilegeGateway.mode) {
            PrivilegeMode.ROOT -> R.string.privilege_mode_status_root_missing
            PrivilegeMode.SHIZUKU -> R.string.permission_shizuku_desc
        }

    fun privilegedAccessRequiredRes(): Int =
        when (PrivilegeGateway.mode) {
            PrivilegeMode.ROOT -> R.string.privileged_access_required_root
            PrivilegeMode.SHIZUKU -> R.string.privileged_access_required_shizuku
        }

    fun taskSwitcherAccessRequiredRes(): Int = R.string.task_switcher_no_privileged_access

    fun shellActionPermissionRes(): Int = privilegedAccessRequiredRes()
}
