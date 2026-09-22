package com.slideindex.app.di

import android.content.Context
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.settings.edgeTriggerWidthDp
import com.slideindex.app.settings.interceptWindowWidthDp
import com.slideindex.app.settings.triggerHandles
import com.slideindex.app.xposed.bridge.ModuleHookConfigWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 把接管开关与触钮几何同步给 system_server 模块（EdgeX 的快照文件 + 广播模式）。
 *
 * 仅在与接管相关的配置真正变化时才下发，避免无谓的跨进程广播。
 */
@Singleton
class ModuleHookConfigSync @Inject constructor(
  @ApplicationContext private val context: Context,
  private val settingsRepository: SettingsRepository,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private var started = false

  @OptIn(FlowPreview::class)
  fun start() {
    if (started) return
    started = true
    scope.launch {
      settingsRepository.settings
        .map { settings -> takeoverSignature(settings) }
        .distinctUntilChanged()
        .debounce(SYNC_DEBOUNCE_MS)
        .collect { publish() }
    }
  }

  fun publish() {
    runCatching {
      ModuleHookConfigWriter.publish(context, settingsRepository.readSnapshot())
    }
  }

  private fun takeoverSignature(settings: AppSettings): String = buildString {
    append(ModuleHookConfigWriter.takeoverGroups(settings))
    append('|').append(settings.interceptSystemBackGesture)
    append('|').append(ModuleHookConfigWriter.navigationMode(context))
    append('|').append(settings.limitMaxInterceptLength)
    for (side in listOf(PanelSide.LEFT, PanelSide.RIGHT, PanelSide.BOTTOM, PanelSide.TOP)) {
      append('#').append(side.name)
      append(':').append(settings.interceptWindowWidthDp(side))
      append(':').append(settings.edgeTriggerWidthDp(side))
      settings.triggerHandles(side).forEach { handle ->
        append(',').append(handle.id)
          .append('@').append(handle.topFraction)
          .append('+').append(handle.heightFraction)
          .append('*').append(handle.edgeWidthDp)
      }
    }
  }

  private companion object {
    const val SYNC_DEBOUNCE_MS = 300L
  }
}