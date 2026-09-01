package com.slideindex.app.di

import com.slideindex.app.clipboard.ClipboardHistoryRepository
import com.slideindex.app.privilege.PrivilegeGateway
import com.slideindex.app.settings.ClipboardMonitoringMode
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.util.TaskManagerUtil
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Singleton
class PrivilegeModeInitializer @Inject constructor(
    settingsRepository: SettingsRepository,
    private val clipboardHistoryRepository: ClipboardHistoryRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        PrivilegeGateway.updateMode(settingsRepository.readSnapshot().privilegeMode)
        warmRootShellIfNeeded()
        scope.launch {
            settingsRepository.settings
                .map { it.privilegeMode }
                .distinctUntilChanged()
                .collect { mode ->
                    PrivilegeGateway.updateMode(mode)
                    warmRootShellIfNeeded()
                    val settings = settingsRepository.readSnapshot()
                    if (settings.clipboardBackgroundMonitoring &&
                        settings.clipboardBackgroundMonitoringMode == ClipboardMonitoringMode.FOLLOW_PRIVILEGE
                    ) {
                        clipboardHistoryRepository.restartClipboardMonitoringFromSettings()
                    }
                }
        }
    }

    private fun warmRootShellIfNeeded() {
        if (!PrivilegeGateway.isRootMode()) return
        scope.launch {
            runCatching {
                TaskManagerUtil.invalidatePrivilegedAccessCache()
                TaskManagerUtil.hasPrivilegedAccess()
            }
        }
    }
}
