package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.ui.NativeEnginePackSettingsScreen
import com.slideindex.app.ui.viewmodel.NativeEnginePackSettingsViewModel

fun NavEntryBuilder.nativeEnginePackNavEntry(ctx: MainNavContext) {
    hiltEntry<AppNavKey.NativeEnginePacks> {
        val viewModel: NativeEnginePackSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val packRows by viewModel.packRows.collectAsStateWithLifecycle()
        val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
        NativeEnginePackSettingsScreen(
            settings = settings,
            packRows = packRows,
            downloadState = downloadState,
            onBack = { ctx.backStack.removeLastOrNull() },
            onDownloadPack = viewModel::downloadPack,
            onDeletePack = viewModel::deletePack,
            onWifiOnlyChange = viewModel::setDownloadWifiOnly,
        )
    }
}
