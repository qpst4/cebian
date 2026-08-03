@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

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
import com.slideindex.app.ui.settings.components.SettingsCardGroupCoordinator
import com.slideindex.app.ui.settings.components.SettingsCardScope

/**
 * 分组设置卡片：同一 [SettingsCard] 内的多行共享圆角，对齐 WeKit「一个标题下一张卡片」。
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable SettingsCardScope.() -> Unit,
) {
    val coordinator = remember { SettingsCardGroupCoordinator() }
    val scope = SettingsCardScope()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        coordinator.clear()
        CompositionLocalProvider(
            LocalSettingsCardScope provides scope,
            LocalSettingsCardGroupCoordinator provides coordinator,
        ) {
            scope.content()
        }
        coordinator.RenderRows()
    }
}

typealias SegmentPosition = com.slideindex.app.ui.settings.components.SegmentPosition
