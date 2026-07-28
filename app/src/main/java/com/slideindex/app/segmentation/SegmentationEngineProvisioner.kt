package com.slideindex.app.segmentation

import android.content.Context
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.nativeengine.NativeEnginePackCoordinator
import com.slideindex.app.nativeengine.NativeEnginePackIds
import com.slideindex.app.service.NativeEnginePackDownloadService
import com.slideindex.app.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Singleton
class SegmentationEngineProvisioner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: NativeEnginePackCoordinator,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile
    private var requested = false

    fun requestIfNeeded() {
        if (coordinator.isPackInstalled(NativeEnginePackIds.SEGMENTATION)) return
        synchronized(this) {
            if (requested) return
            requested = true
        }
        scope.launch {
            Toast.makeText(context, R.string.segmentation_engine_preparing, Toast.LENGTH_SHORT).show()
            val wifiOnly = settingsRepository.settings.first().ocrDownloadWifiOnly
            NativeEnginePackDownloadService.start(
                context,
                NativeEnginePackIds.SEGMENTATION,
                wifiOnly,
            )
        }
    }
}
