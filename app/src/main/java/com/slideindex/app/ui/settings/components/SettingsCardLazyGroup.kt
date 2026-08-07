package com.slideindex.app.ui.settings.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import com.slideindex.app.ui.miuix.groupedCardItems
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 在 [SettingsLazyScreenScaffold] 等直接写 [LazyListScope] 的混合屏里，
 * 把 [SettingsCard] 多行拆成独立 lazy item（对齐 Mishka [groupedCardItems]）。
 *
 * 用法：
 * ```
 * val card = rememberSettingsCardGroup("my-card") { SettingSwitchRow(...) }
 * SettingsLazyScreenScaffold { emitSettingsCardGroup(card) }
 * ```
 */
class SettingsCardLazyGroup internal constructor(
    val keyPrefix: String,
    val selectableGroup: Boolean,
    internal val coordinator: SettingsCardGroupCoordinator,
)

@Composable
fun rememberSettingsCardGroup(
    keyPrefix: String,
    selectableGroup: Boolean = false,
    content: @Composable SettingsCardScope.() -> Unit,
): SettingsCardLazyGroup {
    val coordinator = remember(keyPrefix) { SettingsCardGroupCoordinator() }
    val scope = remember { SettingsCardScope() }
    coordinator.clear()
    CompositionLocalProvider(
        LocalSettingsCardScope provides scope,
        LocalSettingsCardGroupCoordinator provides coordinator,
    ) {
        scope.content()
    }
    return remember(keyPrefix, selectableGroup) {
        SettingsCardLazyGroup(keyPrefix, selectableGroup, coordinator)
    }
}

fun LazyListScope.emitSettingsCardGroup(group: SettingsCardLazyGroup) {
    emitCoordinatorGroupedCard(
        keyPrefix = group.keyPrefix,
        coordinator = group.coordinator,
        selectableGroup = group.selectableGroup,
    )
}

fun LazyListScope.settingsLazySmallTitle(
    key: String,
    title: String,
    sectionTop: Boolean = false,
) {
    item(key = key) {
        MiuixSmallTitle(
            title,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (sectionTop) {
                        Modifier.padding(top = MiuixSmallTitleSectionTop)
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

internal fun LazyListScope.emitCoordinatorGroupedCard(
    keyPrefix: String,
    coordinator: SettingsCardGroupCoordinator,
    selectableGroup: Boolean = false,
) {
    val rows = coordinator.rowsSnapshot()
    if (rows.isEmpty()) return
    groupedCardItems(
        keyPrefix = keyPrefix,
        items = rows.mapIndexed { index, row ->
            CardItem("$index-${row.key}") {
                CompositionLocalProvider(
                    LocalSettingsCardSegmentMode provides true,
                    LocalContentColor provides MiuixTheme.colorScheme.onSurfaceContainer,
                ) {
                    row.content(SegmentPosition(index, rows.size))
                }
            }
        },
        selectableGroup = selectableGroup,
    )
}
