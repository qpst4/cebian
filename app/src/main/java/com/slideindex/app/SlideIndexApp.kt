package com.slideindex.app

import android.app.Application
import com.slideindex.app.clipboard.ClipboardWhitelistBridge
import com.slideindex.app.clipboard.XposedServiceHolder
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.di.OtpAutoFillStatsInstaller
import com.slideindex.app.di.OcrInstalledModelStartupVerifier
import com.slideindex.app.di.ShizukuInitializer
import com.slideindex.app.segmentation.JiebaWarmUp
import com.slideindex.app.segmentation.SegmentationEngineProvisioner
import com.slideindex.app.widget.WidgetPanelPage
import com.slideindex.app.nativeengine.NativeEnginePackCoordinator
import com.slideindex.app.nativeengine.NativeEnginePackIds
import com.slideindex.app.nativeengine.NativeEngineRuntime
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        NativeEngineRuntime.coordinator = nativeEnginePackCoordinator
        NativeEngineRuntime.onRequestSegmentationPack = { segmentationEngineProvisioner.requestIfNeeded() }
        XposedServiceHolder.init(this)
        XposedServiceHolder.addListener {
            ClipboardWhitelistBridge.sync(deps.settingsRepository.readSnapshot())
        }
        deps.applicationScope.launch {
            ClipboardWhitelistBridge.sync(deps.settingsRepository.readSnapshot())
        }
        JiebaWarmUp.start(this)
        shizukuInitializer.start()
        otpAutoFillStatsInstaller.install()
        ocrInstalledModelStartupVerifier.start()
        deps.applicationScope.launch(Dispatchers.IO) {
            listOf(
                NativeEnginePackIds.OCR,
                NativeEnginePackIds.TRANSLATE,
                NativeEnginePackIds.SEGMENTATION,
            ).forEach { packId ->
                nativeEnginePackCoordinator.ensurePackReady(packId)
            }
        }
        // 确保暂存夹、剪贴板仓库在应用启动时初始化。
        deps.stashRepository
        deps.clipboardHistoryRepository
        deps.applicationScope.launch {
            deps.appRepository.loadApps()
        }
    }

    fun schedulePersistWidgetPanelPages(pages: List<WidgetPanelPage>) {
        deps.widgetPanelPersistence.schedulePersist(pages)
    }

    suspend fun persistWidgetPanelPagesNow(pages: List<WidgetPanelPage>) {
        deps.widgetPanelPersistence.persistNow(pages)
    }
}
