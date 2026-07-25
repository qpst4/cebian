package com.slideindex.app.ui.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

data class SegmentPosition(
    val index: Int,
    val count: Int,
)

internal data class SettingsSegmentItem(
    val key: Any,
    val content: @Composable (SegmentPosition) -> Unit,
)

internal data class SettingsCardDecoration(
    val key: Any,
    val content: @Composable () -> Unit,
)

val LocalSettingsCardScope = compositionLocalOf<SettingsCardScope?> { null }

/**
 * Declarative scope for [SettingsCard]. Call row helpers (e.g. [SettingSwitchRow]) or
 * [decoration] for non-segment content such as hint text.
 *
 * Segment collection uses a plain list (not observable state) because items are gathered and
 * rendered in the same composition pass.
 */
class SettingsCardScope internal constructor() {
    private val _decorations = mutableListOf<SettingsCardDecoration>()
    private val _segments = mutableListOf<SettingsSegmentItem>()

    internal val decorations: List<SettingsCardDecoration> get() = _decorations
    internal val segments: List<SettingsSegmentItem> get() = _segments

    internal fun reset() {
        _decorations.clear()
        _segments.clear()
    }

    fun decoration(key: Any, content: @Composable () -> Unit) {
        _decorations.add(SettingsCardDecoration(key, content))
    }

    internal fun segment(key: Any, content: @Composable (SegmentPosition) -> Unit) {
        _segments.add(SettingsSegmentItem(key, content))
    }
}
