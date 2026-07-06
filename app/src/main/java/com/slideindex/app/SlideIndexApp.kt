package com.slideindex.app

import android.app.Application
import android.util.Log
import com.slideindex.app.data.AppRepository
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.util.TaskManagerUtil
import com.slideindex.app.widget.WidgetPanelPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class SlideIndexApp : Application() {
    lateinit var appRepository: AppRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val shizukuBinderListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
        if (TaskManagerUtil.hasPermission()) {
            TaskManagerUtil.warmUp()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        appRepository = AppRepository(this)
        settingsRepository = SettingsRepository(this)
        appScope.launch {
            appRepository.loadApps()
            if (TaskManagerUtil.hasPermission()) {
                TaskManagerUtil.warmUp()
            }
        }
        Shizuku.addBinderReceivedListenerSticky(shizukuBinderListener)
        if (TaskManagerUtil.hasPermission()) {
            TaskManagerUtil.warmUp()
        }
    }

    val applicationScope: CoroutineScope
        get() = appScope

    fun schedulePersistWidgetPanelPages(pages: List<WidgetPanelPage>) {
        appScope.launch {
            settingsRepository.setWidgetPanelPages(pages)
        }
    }

    suspend fun persistWidgetPanelPagesNow(pages: List<WidgetPanelPage>) {
        settingsRepository.setWidgetPanelPages(pages)
    }

    companion object {
        private const val TAG = "SlideIndexApp"
        lateinit var instance: SlideIndexApp
            private set
    }
}
