package com.slideindex.app

import android.app.Application
import android.content.Context
import android.os.Build
import com.slideindex.app.clipboard.DragFileMirror
import com.slideindex.app.clipboard.monitor.ClipboardMonitorStartup
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.di.OtpAutoFillStatsInstaller
import com.slideindex.app.di.OcrEnginePackMigrationStartup
import com.slideindex.app.di.OcrInstalledModelStartupVerifier
import com.slideindex.app.di.ModuleHookConfigSync
import com.slideindex.app.di.PrivilegeModeInitializer
import com.slideindex.app.freezer.FreezerLauncherHelper
import com.slideindex.app.di.ShizukuInitializer
import com.slideindex.app.nativeengine.NativeEnginePackCoordinator
import com.slideindex.app.nativeengine.NativeEngineRuntime
import com.slideindex.app.ocr.OcrDependencyAccess
import com.slideindex.app.segmentation.JiebaWarmUp
import com.slideindex.app.segmentation.SegmentationEngineProvisioner
import com.slideindex.app.service.ClipboardFloatLifecycle
import com.slideindex.app.service.GestureToggleTileWarmup
import com.slideindex.app.service.HistoryFloatLifecycle
import com.slideindex.app.service.OverlayServiceLifecycle
import com.slideindex.app.util.HiddenApiBootstrap
import com.slideindex.app.util.AppLocaleApplier
import com.slideindex.app.util.PredictiveBackHelper
import com.slideindex.app.util.ServiceEnabledStore
import com.slideindex.app.settings.AppUiLanguage
import com.slideindex.app.update.UpdateCheckScheduler
import com.slideindex.app.update.UpdatePreferencesStore
import com.slideindex.app.widget.WidgetPanelPage
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class SlideIndexApp : Application() {
    private companion object {
        /** 启动期重活延后时间，给前台服务 startForeground 留出窗口。 */
        const val STARTUP_HEAVY_TASK_DELAY_MS = 1_500L
    }

    @Inject lateinit var deps: AppDependencies
    @Inject lateinit var shizukuInitializer: ShizukuInitializer
    @Inject lateinit var privilegeModeInitializer: PrivilegeModeInitializer
    @Inject lateinit var otpAutoFillStatsInstaller: OtpAutoFillStatsInstaller
    @Inject lateinit var ocrInstalledModelStartupVerifier: OcrInstalledModelStartupVerifier
    @Inject lateinit var nativeEnginePackCoordinator: NativeEnginePackCoordinator
    @Inject lateinit var ocrEnginePackMigrationStartup: OcrEnginePackMigrationStartup
    @Inject lateinit var segmentationEngineProvisioner: SegmentationEngineProvisioner
    @Inject lateinit var updatePreferencesStore: UpdatePreferencesStore
    @Inject lateinit var moduleHookConfigSync: ModuleHookConfigSync

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocaleApplier.wrapContextIfNeeded(base))
    }

    override fun onCreate() {
        super.onCreate()
        deps.launcherAppsCallbackBridge.register()
        runBlocking(Dispatchers.IO) {
            val language = AppUiLanguage.fromStorageTag(
                runCatching { deps.settingsRepository.readFreshSnapshot().appUiLanguageTag }.getOrDefault("")
            )
            AppLocaleApplier.apply(this@SlideIndexApp, language)
        }
        com.slideindex.app.util.LocalCrashHandler.install(this)
        HiddenApiBootstrap.install()
        NativeEngineRuntime.coordinator = nativeEnginePackCoordinator
        NativeEngineRuntime.onRequestSegmentationPack = { segmentationEngineProvisioner.requestIfNeeded() }
        NativeEngineRuntime.onOcrEnginePackInvalidated = {
            OcrDependencyAccess.inferenceService(this)?.invalidateEngineBlocking()
        }
        ocrEnginePackMigrationStartup.start()
        shizukuInitializer.start()
        moduleHookConfigSync.start()
        otpAutoFillStatsInstaller.install()
        com.slideindex.app.ui.icon.AppIconTheme.ensureSelectedThemeEnabled(this)
        FreezerLauncherHelper.cleanupLegacyAlias(this)
        ClipboardMonitorStartup.applicationReady = true
        // 首帧后再做 OCR 校验、分词 warm-up、应用列表扫描，减轻装后首开卡顿
        // 延迟执行启动期重活：开机瞬间主线程若被 OCR 校验 / 分词 warm-up / 应用列表扫描占住，
        // 剪贴板监听前台服务会来不及在 5 秒内 startForeground（实测过一次
        // ForegroundServiceDidNotStartInTimeException）。
        ClipboardMonitorStartup.runOnMainWhenReady {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            ocrInstalledModelStartupVerifier.start()
            JiebaWarmUp.start(this@SlideIndexApp)
            if (deps.settingsRepository.readSnapshot().onboardingCompleted) {
                deps.applicationScope.launch(Dispatchers.IO) {
                    deps.appRepository.loadApps()
                    com.slideindex.app.widget.WidgetCatalog.preload(this@SlideIndexApp)
                }
            }
            }, STARTUP_HEAVY_TASK_DELAY_MS)
        }
        deps.stashRepository
        deps.clipboardHistoryRepository
        deps.applicationScope.launch(Dispatchers.IO) {
            val enabled = deps.settingsRepository.settings.first().serviceEnabled
            ServiceEnabledStore.write(this@SlideIndexApp, enabled)
        }
        deps.applicationScope.launch(Dispatchers.IO) {
            // 兜底清理进程被杀留下的拖拽镜像；按年龄判定，任何进程调用都安全。
            DragFileMirror.purgeOrphans(this@SlideIndexApp)
        }
        deps.applicationScope.launch {
            HistoryFloatLifecycle.syncFromSettings(this@SlideIndexApp, deps.settingsRepository)
            ClipboardFloatLifecycle.syncFromSettings(this@SlideIndexApp, deps.settingsRepository)
            OverlayServiceLifecycle.syncFromSettings(
                this@SlideIndexApp,
                deps.settingsRepository,
                accessibilityRecoverRetries = true,
            )
        }
        GestureToggleTileWarmup.requestListening(this, "appOnCreate")
        deps.applicationScope.launch {
            if (updatePreferencesStore.read().autoCheckUpdate) {
                UpdateCheckScheduler.schedule(this@SlideIndexApp)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val enabled = deps.settingsRepository.readSnapshot().predictiveBackEnabled
            PredictiveBackHelper.applyEnabled(applicationInfo, enabled)
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL || level >= TRIM_MEMORY_UI_HIDDEN) {
            com.slideindex.app.clipboard.ClipboardThumbnailCache.evictAll()
            com.slideindex.app.util.GestureActionIconBitmap.evictAll()
        }
    }

    fun schedulePersistWidgetPanelPages(pages: List<WidgetPanelPage>) {
        deps.widgetPanelPersistence.schedulePersist(pages)
    }

    suspend fun persistWidgetPanelPagesNow(pages: List<WidgetPanelPage>) {
        deps.widgetPanelPersistence.persistNow(pages)
    }
}
