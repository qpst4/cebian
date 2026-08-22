package com.slideindex.app

import android.app.Application
import com.slideindex.app.clipboard.monitor.ClipboardMonitorStartup
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.di.OtpAutoFillStatsInstaller
import com.slideindex.app.di.OcrInstalledModelStartupVerifier
import com.slideindex.app.di.ShizukuInitializer
import com.slideindex.app.nativeengine.NativeEnginePackCoordinator
import com.slideindex.app.nativeengine.NativeEngineRuntime
import com.slideindex.app.segmentation.JiebaWarmUp
import com.slideindex.app.segmentation.SegmentationEngineProvisioner
import com.slideindex.app.service.ClipboardFloatLifecycle
import com.slideindex.app.service.GestureToggleTileWarmup
import com.slideindex.app.service.HistoryFloatLifecycle
import com.slideindex.app.util.ServiceEnabledStore
import com.slideindex.app.widget.WidgetPanelPage
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

@HiltAndroidApp
class SlideIndexApp : Application() {
    @Inject lateinit var deps: AppDependencies
    @Inject lateinit var shizukuInitializer: ShizukuInitializer
    @Inject lateinit var otpAutoFillStatsInstaller: OtpAutoFillStatsInstaller
    @Inject lateinit var ocrInstalledModelStartupVerifier: OcrInstalledModelStartupVerifier
    @Inject lateinit var nativeEnginePackCoordinator: NativeEnginePackCoordinator
    @Inject lateinit var segmentationEngineProvisioner: SegmentationEngineProvisioner

    override fun onCreate() {
        super.onCreate()
        HiddenApiBypass.setHiddenApiExemptions("L")
        NativeEngineRuntime.coordinator = nativeEnginePackCoordinator
        NativeEngineRuntime.onRequestSegmentationPack = { segmentationEngineProvisioner.requestIfNeeded() }
        shizukuInitializer.start()
        otpAutoFillStatsInstaller.install()
        com.slideindex.app.ui.icon.AppIconTheme.ensureSelectedThemeEnabled(this)
        ClipboardMonitorStartup.applicationReady = true
        // 首帧后再做 OCR 校验、分词 warm-up、应用列表扫描，减轻装后首开卡顿
        ClipboardMonitorStartup.runOnMainWhenIdle {
            ocrInstalledModelStartupVerifier.start()
            JiebaWarmUp.start(this@SlideIndexApp)
            deps.applicationScope.launch(Dispatchers.IO) {
                deps.appRepository.loadApps()
                com.slideindex.app.widget.WidgetCatalog.preload(this@SlideIndexApp)
            }
        }
        deps.stashRepository
        deps.clipboardHistoryRepository
        deps.applicationScope.launch(Dispatchers.IO) {
            val enabled = deps.settingsRepository.settings.first().serviceEnabled
            ServiceEnabledStore.write(this@SlideIndexApp, enabled)
        }
        deps.applicationScope.launch {
            HistoryFloatLifecycle.syncFromSettings(this@SlideIndexApp, deps.settingsRepository)
            ClipboardFloatLifecycle.syncFromSettings(this@SlideIndexApp, deps.settingsRepository)
        }
        GestureToggleTileWarmup.requestListening(this, "appOnCreate")
    }

    fun schedulePersistWidgetPanelPages(pages: List<WidgetPanelPage>) {
        deps.widgetPanelPersistence.schedulePersist(pages)
    }

    suspend fun persistWidgetPanelPagesNow(pages: List<WidgetPanelPage>) {
        deps.widgetPanelPersistence.persistNow(pages)
    }
}
