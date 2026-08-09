@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/**
 * Portions derived from Mishka (https://github.com/YuKongA/Mishka)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.settings.components.LocalSettingsCardGroupCoordinator
import com.slideindex.app.ui.settings.components.LocalSettingsCardScope
import com.slideindex.app.ui.settings.components.LocalSettingsLazyEmitter
import com.slideindex.app.ui.settings.components.SettingsCardGroupCoordinator
import com.slideindex.app.ui.settings.components.SettingsCardScope

/**
 * 分组设置卡片：同一 [SettingsCard] 内的多行共享圆角。
 * 在 Lazy 脚手架内自动拆为独立 lazy item（对齐 Mishka [groupedCardItems]）。
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    keyPrefix: String? = null,
    content: @Composable SettingsCardScope.() -> Unit,
) {
    val lazyEmitter = LocalSettingsLazyEmitter.current
    val coordinator = remember { SettingsCardGroupCoordinator() }
    val scope = remember { SettingsCardScope() }
    coordinator.clear()
    CompositionLocalProvider(
        LocalSettingsCardScope provides scope,
        LocalSettingsCardGroupCoordinator provides coordinator,
    ) {
        scope.content()
    }
    if (lazyEmitter != null) {
        lazyEmitter.registerGroupedCard(keyPrefix, coordinator)
        return
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        coordinator.RenderRows()
    }
}

typealias SegmentPosition = com.slideindex.app.ui.settings.components.SegmentPosition
