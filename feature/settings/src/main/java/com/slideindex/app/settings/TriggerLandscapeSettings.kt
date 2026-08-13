package com.slideindex.app.settings

import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.overlay.PanelSide

/** 横屏触钮布局存储与编辑/运行时解析。 */
fun AppSettings.hasStoredLandscapeTriggerHandles(): Boolean =
    leftTriggerHandlesLandscape.isNotEmpty() ||
        rightTriggerHandlesLandscape.isNotEmpty() ||
        bottomTriggerHandlesLandscape.isNotEmpty() ||
        topTriggerHandlesLandscape.isNotEmpty()

fun AppSettings.storedLandscapeTriggerHandles(side: PanelSide): List<TriggerHandle> = when (side) {
    PanelSide.LEFT -> leftTriggerHandlesLandscape
    PanelSide.RIGHT -> rightTriggerHandlesLandscape
    PanelSide.BOTTOM -> bottomTriggerHandlesLandscape
    PanelSide.TOP -> topTriggerHandlesLandscape
}

/** 将横屏存储映射到主 handle 字段，复用现有编辑/变更逻辑。 */
fun AppSettings.forLandscapeHandleEditing(): AppSettings = copy(
    leftTriggerHandles = storedLandscapeTriggerHandles(PanelSide.LEFT)
        .ifEmpty { leftTriggerHandles },
    rightTriggerHandles = storedLandscapeTriggerHandles(PanelSide.RIGHT)
        .ifEmpty { rightTriggerHandles },
    bottomTriggerHandles = storedLandscapeTriggerHandles(PanelSide.BOTTOM)
        .ifEmpty { bottomTriggerHandles },
    topTriggerHandles = storedLandscapeTriggerHandles(PanelSide.TOP)
        .ifEmpty { topTriggerHandles },
)

/** 把编辑后的主 handle 字段写回横屏存储字段。 */
fun AppSettings.mergeLandscapeHandleEdits(edited: AppSettings): AppSettings = copy(
    leftTriggerHandlesLandscape = edited.leftTriggerHandles,
    rightTriggerHandlesLandscape = edited.rightTriggerHandles,
    bottomTriggerHandlesLandscape = edited.bottomTriggerHandles,
    topTriggerHandlesLandscape = edited.topTriggerHandles,
)

fun AppSettings.withLandscapeHandlesCopiedFromPortrait(): AppSettings = copy(
    leftTriggerHandlesLandscape = redistributeLandscapeSideHandles(leftTriggerHandles),
    rightTriggerHandlesLandscape = redistributeLandscapeSideHandles(rightTriggerHandles),
    bottomTriggerHandlesLandscape = redistributeLandscapeSideHandles(bottomTriggerHandles),
    topTriggerHandlesLandscape = redistributeLandscapeSideHandles(topTriggerHandles),
)

/** 若横屏触钮区间重叠，按数量等分短边后写回（保留 id/外观/手势相关字段）。 */
fun AppSettings.withRepairedLandscapeHandleLayoutIfOverlapping(): AppSettings {
    fun repair(stored: List<TriggerHandle>, portrait: List<TriggerHandle>): List<TriggerHandle> {
        val source = stored.ifEmpty { portrait }
        if (source.size <= 1 || !source.hasOverlappingSpans()) return stored
        return redistributeLandscapeSideHandles(source)
    }
    return copy(
        leftTriggerHandlesLandscape = repair(leftTriggerHandlesLandscape, leftTriggerHandles),
        rightTriggerHandlesLandscape = repair(rightTriggerHandlesLandscape, rightTriggerHandles),
        bottomTriggerHandlesLandscape = repair(bottomTriggerHandlesLandscape, bottomTriggerHandles),
        topTriggerHandlesLandscape = repair(topTriggerHandlesLandscape, topTriggerHandles),
    )
}

private const val LANDSCAPE_SPAN_MIN = 0.05f
private const val LANDSCAPE_SPAN_MAX = 0.95f
private const val LANDSCAPE_USABLE = LANDSCAPE_SPAN_MAX - LANDSCAPE_SPAN_MIN
private const val LANDSCAPE_SLOT_GAP = 0.02f
private const val LANDSCAPE_MIN_HEIGHT_FRACTION = 0.12f
private const val LANDSCAPE_MAX_HEIGHT_FRACTION = 0.36f

/**
 * 将同侧多个触钮按比例分布在可用边长上，避免横屏短边高度不足时叠在一起。
 * 保持原有顺序与 id，仅调整 topFraction / heightFraction。
 */
internal fun redistributeLandscapeSideHandles(handles: List<TriggerHandle>): List<TriggerHandle> {
    if (handles.isEmpty()) return emptyList()
    if (handles.size == 1) {
        val only = handles.first()
        val height = only.heightFraction.coerceIn(LANDSCAPE_MIN_HEIGHT_FRACTION, LANDSCAPE_MAX_HEIGHT_FRACTION)
        val top = (0.5f - height / 2f).coerceIn(LANDSCAPE_SPAN_MIN, LANDSCAPE_SPAN_MAX - height)
        return listOf(only.copy(topFraction = top, heightFraction = height))
    }
    val count = handles.size
    val totalGap = LANDSCAPE_SLOT_GAP * (count - 1)
    val span = ((LANDSCAPE_USABLE - totalGap) / count)
        .coerceIn(LANDSCAPE_MIN_HEIGHT_FRACTION, LANDSCAPE_MAX_HEIGHT_FRACTION)
    val groupHeight = span * count + totalGap
    val start = LANDSCAPE_SPAN_MIN + (LANDSCAPE_USABLE - groupHeight) / 2f
    return handles.mapIndexed { index, handle ->
        val top = start + index * (span + LANDSCAPE_SLOT_GAP)
        handle.copy(
            topFraction = top.coerceIn(LANDSCAPE_SPAN_MIN, LANDSCAPE_SPAN_MAX - span),
            heightFraction = span,
        )
    }
}

private fun List<TriggerHandle>.hasOverlappingSpans(): Boolean {
    if (size <= 1) return false
    val sorted = sortedBy { it.topFraction }
    for (i in 1 until sorted.size) {
        if (sorted[i].topFraction < sorted[i - 1].bottomFraction - 0.001f) return true
    }
    return false
}

fun AppSettings.runtimeTriggerHandles(side: PanelSide, isLandscape: Boolean): List<TriggerHandle> {
    if (!isLandscape) return triggerHandles(side)
    val stored = storedLandscapeTriggerHandles(side)
    val effective = if (stored.isNotEmpty()) stored else allTriggerHandles(side)
    return effective.filter { it.enabled }
}

fun AppSettings.runtimeAllTriggerHandles(side: PanelSide, isLandscape: Boolean): List<TriggerHandle> {
    if (!isLandscape) return allTriggerHandles(side)
    val stored = storedLandscapeTriggerHandles(side)
    return if (stored.isNotEmpty()) stored else allTriggerHandles(side)
}

fun AppSettings.runtimeTriggerHandle(
    side: PanelSide,
    handleId: String,
    isLandscape: Boolean,
): TriggerHandle? = runtimeAllTriggerHandles(side, isLandscape).firstOrNull { it.id == handleId }

fun AppSettings.withRuntimeTriggerHandles(isLandscape: Boolean): AppSettings {
    if (!isLandscape) return this
    return copy(
        leftTriggerHandles = runtimeAllTriggerHandles(PanelSide.LEFT, true),
        rightTriggerHandles = runtimeAllTriggerHandles(PanelSide.RIGHT, true),
        bottomTriggerHandles = runtimeAllTriggerHandles(PanelSide.BOTTOM, true),
        topTriggerHandles = runtimeAllTriggerHandles(PanelSide.TOP, true),
    )
}
