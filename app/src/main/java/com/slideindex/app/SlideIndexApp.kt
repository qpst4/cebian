package com.slideindex.app

import android.app.Application
import android.content.Context
import android.os.Build
import com.slideindex.app.clipboard.DragFileMirror
import com.slideindex.app.clipboard.monitor.ClipboardMonitorStartup
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.di.OtpAutoFillStatsInstaller
import com.slideindex.app.di.OtpRecordLimitsInstaller
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
import com.slideindex.app.util.AppProcess
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class SlideIndexApp : Application(), androidx.work.Configuration.Provider {
    private companion object {
        /** 启动期重活延后时间，给前台服务 startForeground 留出窗口。 */
        const val STARTUP_HEAVY_TASK_DELAY_MS = 1_500L

        /**
         * 给 WorkManager 声明的 JobScheduler id 范围（lint: SpecifyJobSchedulerIdRange）。
         *
         * 本应用除了 WorkManager，还自己直接用 JobScheduler（[com.slideindex.app.service.OverlayWatchdogJobService]
         * 的 id 是 0x5101 = 20737，必须落在下面这个范围之外，否则两边编号可能撞车）。
         * WorkManager 默认范围是 [0, Int.MAX_VALUE]，等于"整段都可以用"，所以必须显式收窄；
         * 收窄后老的 WorkManager 任务仍落在新范围内（它们用的是很小的自增编号），不会被漏掉。
         */
        const val WORK_MANAGER_JOB_ID_MIN = 0
        const val WORK_MANAGER_JOB_ID_MAX = 20_000
    }

    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setJobSchedulerJobIdRange(WORK_MANAGER_JOB_ID_MIN, WORK_MANAGER_JOB_ID_MAX)
            .build()

    @Inject lateinit var deps: AppDependencies
    @Inject lateinit var shizukuInitializer: ShizukuInitializer
    @Inject lateinit var privilegeModeInitializer: PrivilegeModeInitializer
    @Inject lateinit var otpAutoFillStatsInstaller: OtpAutoFillStatsInstaller
    @Inject lateinit var otpRecordLimitsInstaller: OtpRecordLimitsInstaller
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
        // TaskManagerUtil 只是"存一下 app context"，不做任何跨进程动作，必须在每个进程一开始就喂上。
        // 它此前只被 ShizukuInitializer 顺带初始化，而 :overlay 侧的 Shizuku 初始化现在是"延后且
        // 主进程活着才做"——只要没走到那一步，:overlay 里所有特权调用都会抛
        // "TaskManagerUtil.initialize() must be called before use"：任务切换器空、小窗动作失效
        // （真机 01:01 日志）。惰性化只该针对"取 Shizuku binder"，不该连这个纯赋值初始化一起拖。
        com.slideindex.app.util.TaskManagerUtil.initialize(this)
        // Shizuku 的 binder 只会投递给声明了 ShizukuProvider 的进程（这里是默认进程）。
        // 注意 enableMultiProcessSupport(flag) 的 flag 含义是"**当前进程**是不是 provider 进程"，
        // 传 true 会被 Shizuku 当成 provider 进程，紧接着的 requestBinderForNonProviderProcess 会直接 return。
        //
        // 非主进程**不在启动期取 binder**：取 binder 会顺带把主进程拉起来，并让本进程依赖
        // 主进程里的这个 provider —— 覆盖安装后主进程启动慢、被 AMS 判成 attach 超时杀掉时，
        // 本进程（:overlay）会被系统连带杀掉，表现为"装完悬浮球半天不出来"。
        // 具体策略见 ShizukuBinderBridge（启动期延后补取 + 真正用到时惰性取）。
        runCatching {
            if (AppProcess.isMain) {
                rikka.shizuku.ShizukuProvider.enableMultiProcessSupport(true)
            } else {
                com.slideindex.app.shizuku.ShizukuBinderBridge.scheduleDeferredAcquire(this)
            }
        }
        // 所有进程共有：崩溃记录 + Hidden API 放行 + 引擎运行时接入。
        com.slideindex.app.util.LocalCrashHandler.install(this)
        HiddenApiBootstrap.install()
        NativeEngineRuntime.coordinator = nativeEnginePackCoordinator
        NativeEngineRuntime.onRequestSegmentationPack = { segmentationEngineProvisioner.requestIfNeeded() }
        NativeEngineRuntime.onOcrEnginePackInvalidated = {
            OcrDependencyAccess.inferenceService(this)?.invalidateEngineBlocking()
        }
        if (AppProcess.isEngine) return

        // OCR 推理改由 :engine 进程执行（onnxruntime/opencv 不再常驻调用方进程）；
        // 传输未接或失败时 OcrInferenceService 会自动回退本地推理。
        if (!AppProcess.isEngine) {
            com.slideindex.app.ocr.OcrRemoteBridge.transport =
                com.slideindex.app.engine.EngineOcrTransport(this)
        }

        // overlay 状态端口：overlay 进程负责接收命令，其它进程维护只读镜像。
        if (AppProcess.isOverlay) {
            com.slideindex.app.overlay.OverlayStatePort.startCommandReceiver(this)
        } else {
            com.slideindex.app.overlay.OverlayStatePort.startMirroring(this)
        }
        // 剪贴板监听状态镜像：监听进程发布，其它进程（设置页）读。
        com.slideindex.app.clipboard.monitor.ClipboardMonitorStatusPort.start(this)

        // —— 主进程与 :overlay 共有：常驻交互（无障碍/浮层/模块桥/系统监听）需要用到的部分 ——
        deps.launcherAppsCallbackBridge.register()
        if (AppProcess.isMain) {
            // 主进程读一次最新语言并写入系统 LocaleManager（其它进程从轻量缓存取）
            runBlocking(Dispatchers.IO) {
                val language = AppUiLanguage.fromStorageTag(
                    runCatching { deps.settingsRepository.readFreshSnapshot().appUiLanguageTag }.getOrDefault("")
                )
                AppLocaleApplier.apply(this@SlideIndexApp, language)
            }
            shizukuInitializer.start()
            moduleHookConfigSync.start()
        } else {
            // :overlay 等进程：启动期只做廉价操作，把可能阻塞的初始化挪到后台，
            // 保证前台服务的 startForeground 窗口不被挤掉。
            AppLocaleApplier.primeFromStorage(this)
            // 同上：shizukuInitializer.start() 会立刻去取 binder（= 拉起主进程并形成依赖），
            // 非主进程要等主进程稳定后再做，否则会把浮层进程一起搭进去。
            deps.applicationScope.launch {
                delay(com.slideindex.app.shizuku.ShizukuBinderBridge.DEFERRED_ACQUIRE_DELAY_MS)
                if (com.slideindex.app.shizuku.ShizukuBinderBridge.isMainProcessAlive(this@SlideIndexApp)) {
                    shizukuInitializer.start()
                }
            }
            deps.applicationScope.launch { moduleHookConfigSync.start() }
            if (AppProcess.isOverlay) {
                // 键盘上方的剪贴板小窗由 :overlay 渲染，开关（clipboardFloatEnabled 等）
                // 必须在本进程下发：此前只有主进程调 ClipboardFloatLifecycle.syncFromSettings，
                // 导致 overlay 侧一直是 false → 弹出键盘看不到入口。
                deps.applicationScope.launch {
                    deps.settingsRepository.settings.collect { settings ->
                        com.slideindex.app.clipboardfloat.ClipboardFloatImeCoordinator.applySettings(settings)
                    }
                }
            }
        }
        otpAutoFillStatsInstaller.install()
        otpRecordLimitsInstaller.install()
        ClipboardMonitorStartup.applicationReady = true

        // —— 仅主进程：启动期重活、调度、拉起常驻服务 ——
        // :overlay 进程不跑这些，否则它自己就成了"启动期卡顿"的下一个受害者。
        if (AppProcess.isMain) {
        com.slideindex.app.ui.icon.AppIconTheme.ensureSelectedThemeEnabled(this)
        // 首帧后再做 OCR 校验、分词 warm-up、应用列表扫描，减轻装后首开卡顿
        // 延迟执行启动期重活：开机瞬间主线程若被 OCR 校验 / 分词 warm-up / 应用列表扫描占住，
        // 剪贴板监听前台服务会来不及在 5 秒内 startForeground（实测过一次
        // ForegroundServiceDidNotStartInTimeException）。
        ClipboardMonitorStartup.runOnMainWhenReady {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // 引擎包校验 + OCR 冒烟改到 :engine 进程执行（native 库与解压消耗不再压在本进程）
            com.slideindex.app.service.EngineBootService.start(this@SlideIndexApp)
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
        // 常驻巡检：用户不开 App 时，overlay 掉线/进程被杀也能被自己拉回来。
        com.slideindex.app.service.OverlayWatchdogJobService.schedule(this)
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
            ocrEnginePackMigrationStartup.start()
            FreezerLauncherHelper.cleanupLegacyAlias(this)
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
