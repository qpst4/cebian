package com.slideindex.app.di

import android.content.Context
import com.slideindex.app.privilege.ShizukuRequirement
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.shizuku.ShizukuUserServiceHost
import com.slideindex.app.util.TaskManagerUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

@Singleton
class ShizukuInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var listenerRegistered = false

    private val binderListener = Shizuku.OnBinderReceivedListener {
        if (TaskManagerUtil.hasShizukuPermission()) {
            TaskManagerUtil.warmUp()
        }
    }

    init {
        scope.launch {
            settingsRepository.settings
                .map(ShizukuRequirement::needsShizuku)
                .distinctUntilChanged()
                .collect { needed -> apply(needed) }
        }
    }

    fun start() {
        TaskManagerUtil.initialize(context)
        apply(ShizukuRequirement.needsShizuku(settingsRepository.readSnapshot()))
    }

    private fun apply(needed: Boolean) {
        if (needed) {
            if (!listenerRegistered) {
                Shizuku.addBinderReceivedListenerSticky(binderListener)
                listenerRegistered = true
            }
            if (TaskManagerUtil.hasShizukuPermission()) {
                TaskManagerUtil.warmUp()
            }
        } else {
            if (listenerRegistered) {
                Shizuku.removeBinderReceivedListener(binderListener)
                listenerRegistered = false
            }
            ShizukuUserServiceHost.drop(context)
        }
    }
}
