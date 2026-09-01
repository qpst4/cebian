package com.slideindex.app.privilege

import com.slideindex.app.settings.PrivilegeMode
import com.slideindex.app.shizuku.RootShellSession
import com.slideindex.app.shizuku.TaskManagerShellExecutor
import com.slideindex.app.util.ShellCommandExecutor
import com.slideindex.app.util.TaskManagerUtil

object PrivilegeGateway {
    @Volatile
    var mode: PrivilegeMode = PrivilegeMode.SHIZUKU
        private set

    fun updateMode(mode: PrivilegeMode) {
        if (this.mode == mode) return
        this.mode = mode
        ShellCommandExecutor.invalidateRootCache()
        TaskManagerUtil.invalidatePrivilegedAccessCache()
        RootShellSession.close()
    }

    fun isRootMode(): Boolean = mode == PrivilegeMode.ROOT

    fun isShizukuMode(): Boolean = mode == PrivilegeMode.SHIZUKU

    fun probeDirectRootAvailable(): Boolean = TaskManagerUtil.probeDirectRootAvailable()
}
