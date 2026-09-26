package com.slideindex.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.slideindex.app.di.OcrInstalledModelStartupVerifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * `:engine` 进程的引擎侧启动入口。
 *
 * 原来引擎包的完整性修复与 OCR 冒烟校验跑在默认进程里，会把 onnxruntime / opencv / tesseract
 * 这些 native 库拉进 UI 进程（也是我们测到 OOM 的地方）。现在这两件事在 `:engine` 里做：
 * 引擎分配的缓冲和解压消耗只影响引擎进程，UI / 常驻交互进程不再被牵连。
 */
@AndroidEntryPoint
class EngineBootService : Service() {

    @Inject lateinit var ocrInstalledModelStartupVerifier: OcrInstalledModelStartupVerifier

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching { ocrInstalledModelStartupVerifier.start() }
            .onFailure { Log.w(TAG, "engine boot work failed", it) }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "EngineBootService"

        /**
         * 让 `:engine` 进程起来做引擎侧初始化。后台限制下可能被拒（startService 抛异常），
         * 那只是少了一次预热，不影响正确性。
         */
        fun start(context: Context) {
            runCatching {
                context.applicationContext.startService(
                    Intent(context.applicationContext, EngineBootService::class.java)
                )
            }.onFailure { Log.w(TAG, "start failed", it) }
        }
    }
}
