package com.slideindex.app.ui.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key

data class SegmentPosition(
    val index: Int,
    val count: Int,
)

/** Marker scope for settings row helpers inside [settingsCardItems]. */
class SettingsCardScope internal constructor()

val LocalSettingsCardScope = compositionLocalOf<SettingsCardScope?> { null }

internal val LocalSettingsCardGroupCoordinator = compositionLocalOf<SettingsCardGroupCoordinator?> { null }

internal val LocalSettingsCardSegmentMode = compositionLocalOf { false }

@Composable
internal fun SettingsCardScope.SettingsCardRow(
    key: Any,
    content: @Composable (SegmentPosition) -> Unit,
) {
    val coordinator = LocalSettingsCardGroupCoordinator.current
    if (coordinator != null) {
        coordinator.register(key, content)
    } else {
        content(SegmentPosition(index = 0, count = 1))
    }
}

internal class SettingsCardGroupCoordinator {
    private val rows = mutableListOf<RegisteredRow>()

    internal data class RegisteredRow(
        val key: Any,
        val content: @Composable (SegmentPosition) -> Unit,
    )

    internal val rowCount: Int get() = rows.size

    internal fun rowsSnapshot(): List<RegisteredRow> = rows.toList()

    fun clear() {
        rows.clear()
    }

    fun register(key: Any, content: @Composable (SegmentPosition) -> Unit) {
        rows.add(RegisteredRow(key, content))
    }

    @Composable
    fun RenderRows() {
        val count = rows.size
        rows.forEachIndexed { index, row ->
            key(row.key) {
                row.content(SegmentPosition(index, count))
            }
        }
    }
}
