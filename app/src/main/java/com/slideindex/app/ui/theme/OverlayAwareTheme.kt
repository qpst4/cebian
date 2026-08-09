package com.slideindex.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.theme.ModuleTheme

/**
 * 浮层 / 无参数主题入口：从 Overlay DI 读取完整主题设置，走 [ModuleTheme]，
 * 与主界面一致（含 [com.slideindex.app.settings.AppThemeMode]）。
 */
@Composable
fun OverlayAwareModuleTheme(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(AppSettings()) }
    LaunchedEffect(Unit) {
        val repo = OverlayDependencyAccess.overlayDependencies(context)?.settingsRepository
            ?: return@LaunchedEffect
        repo.settings.collect { settings = it }
    }
    ModuleTheme(settings = settings, content = content)
}
