package com.slideindex.app.ui.settings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

internal val LocalSettingsLazyEmitter = staticCompositionLocalOf<SettingsLazyEmitter?> { null }

/** 行背景由 [CardSegment] 提供时，Preference 行不再叠加 [miuixGroupedCardItem]。 */
internal val LocalSettingsCardSegmentMode = compositionLocalOf { false }

/**
 * 在 Lazy 设置脚手架的收集阶段，把顶层 composable 登记为独立 lazy item。
 */
/** 在 Emitter 脚手架内包裹自定义整块（预览、表单、非 SettingsCard 列表等）。 */
@Composable
fun SettingsLazyBlock(
    key: String,
    content: @Composable () -> Unit,
) = LazySettingsItem(key = key, content = content)

@Composable
fun LazySettingsItem(
    key: String,
    fillParentMaxSize: Boolean = false,
    content: @Composable () -> Unit,
) {
    val emitter = LocalSettingsLazyEmitter.current
    if (emitter != null) {
        emitter.registerItem(key, fillParentMaxSize, content)
    } else {
        content()
    }
}

class SettingsLazyEmitter internal constructor() {
    private sealed interface Entry {
        data class Item(
            val key: String,
            val content: @Composable () -> Unit,
            val fillParentMaxSize: Boolean = false,
        ) : Entry

        data class GroupedCard(
            val keyPrefix: String,
            val coordinator: SettingsCardGroupCoordinator,
            val selectableGroup: Boolean,
        ) : Entry
    }

    private var collecting = false
    private val pendingEntries = mutableListOf<Entry>()
    private val entries = mutableListOf<Entry>()
    private var autoCardKeyCounter = 0

    internal fun beginCollect() {
        pendingEntries.clear()
        collecting = true
        autoCardKeyCounter = 0
    }

    internal fun endCollect() {
        collecting = false
        entries.clear()
        entries.addAll(pendingEntries)
    }

    internal fun registerItem(
        key: String,
        fillParentMaxSize: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        if (!collecting) return
        pendingEntries += Entry.Item(key, content, fillParentMaxSize)
    }

    internal fun registerGroupedCard(
        keyPrefix: String?,
        coordinator: SettingsCardGroupCoordinator,
        selectableGroup: Boolean = false,
    ) {
        if (!collecting || coordinator.rowCount == 0) return
        val prefix = keyPrefix ?: "settings-card-${autoCardKeyCounter++}"
        pendingEntries += Entry.GroupedCard(prefix, coordinator, selectableGroup)
    }

    internal fun emitTo(scope: LazyListScope, bottomInset: Dp) {
        entries.forEach { entry ->
            when (entry) {
                is Entry.Item -> scope.item(key = entry.key) {
                    if (entry.fillParentMaxSize) {
                        Box(Modifier.fillParentMaxSize()) {
                            entry.content()
                        }
                    } else {
                        entry.content()
                    }
                }
                is Entry.GroupedCard -> scope.emitGroupedCard(entry)
            }
        }
        scope.item(key = "settings-bottom-inset") {
            Spacer(Modifier.height(bottomInset))
        }
    }

    private fun LazyListScope.emitGroupedCard(entry: Entry.GroupedCard) {
        emitCoordinatorGroupedCard(
            keyPrefix = entry.keyPrefix,
            coordinator = entry.coordinator,
            selectableGroup = entry.selectableGroup,
        )
    }
}

@Composable
internal fun rememberSettingsLazyEmitter(): SettingsLazyEmitter = remember { SettingsLazyEmitter() }

@Composable
internal fun CollectSettingsLazyContent(
    emitter: SettingsLazyEmitter,
    content: @Composable () -> Unit,
) {
    emitter.beginCollect()
    CompositionLocalProvider(LocalSettingsLazyEmitter provides emitter) {
        content()
    }
    emitter.endCollect()
}
