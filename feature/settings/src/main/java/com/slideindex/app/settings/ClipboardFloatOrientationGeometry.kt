package com.slideindex.app.settings

data class ClipboardFloatOrientationGeometry(
    val panelX: Int = ClipboardFloatWindowMetrics.UNSET_POSITION,
    val panelY: Int = ClipboardFloatWindowMetrics.UNSET_POSITION,
    val panelWidthDp: Int = ClipboardFloatWindowMetrics.DEFAULT_WIDTH_DP,
    val panelHeightDp: Int = ClipboardFloatWindowMetrics.DEFAULT_HEIGHT_DP,
    val chipX: Int = ClipboardFloatWindowMetrics.UNSET_POSITION,
    val chipY: Int = ClipboardFloatWindowMetrics.UNSET_POSITION,
) {
    /** 写入时合并：新值为 UNSET 时保留磁盘已有坐标，避免大窗关窗把 chip/panel 冲成 -1。 */
    fun mergePreservingUnset(existing: ClipboardFloatOrientationGeometry): ClipboardFloatOrientationGeometry {
        val unset = ClipboardFloatWindowMetrics.UNSET_POSITION
        return copy(
            panelX = if (panelX != unset) panelX else existing.panelX,
            panelY = if (panelY != unset) panelY else existing.panelY,
            chipX = if (chipX != unset) chipX else existing.chipX,
            chipY = if (chipY != unset) chipY else existing.chipY,
        )
    }
}
