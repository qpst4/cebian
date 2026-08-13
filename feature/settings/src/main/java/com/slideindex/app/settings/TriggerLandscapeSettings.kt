package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.overlay.PanelSide

/** 横屏触钮布局与手势是否已从竖屏完成一次性初始化。 */
fun AppSettings.hasLandscapeTriggerProfile(): Boolean = landscapeTriggersInitialized

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

/** 横屏编辑态：布局 + 手势规则均映射到主字段，与竖屏编辑互不干扰。 */
fun AppSettings.forLandscapeEditing(): AppSettings = forLandscapeHandleEditing().copy(
    gestureRules = gestureRulesLandscape,
    leftDefaultTriggerMode = leftDefaultTriggerModeLandscape,
    rightDefaultTriggerMode = rightDefaultTriggerModeLandscape,
    bottomDefaultTriggerMode = bottomDefaultTriggerModeLandscape,
    topDefaultTriggerMode = topDefaultTriggerModeLandscape,
)

/** 把编辑后的主字段写回横屏存储（布局 + 手势）。 */
fun AppSettings.mergeLandscapeEdits(edited: AppSettings): AppSettings = copy(
    leftTriggerHandlesLandscape = edited.leftTriggerHandles,
    rightTriggerHandlesLandscape = edited.rightTriggerHandles,
    bottomTriggerHandlesLandscape = edited.bottomTriggerHandles,
    topTriggerHandlesLandscape = edited.topTriggerHandles,
    gestureRulesLandscape = edited.gestureRules,
    leftDefaultTriggerModeLandscape = edited.leftDefaultTriggerMode,
    rightDefaultTriggerModeLandscape = edited.rightDefaultTriggerMode,
    bottomDefaultTriggerModeLandscape = edited.bottomDefaultTriggerMode,
    topDefaultTriggerModeLandscape = edited.topDefaultTriggerMode,
)

/** @deprecated 仅布局；新手势请用 [mergeLandscapeEdits]。 */
fun AppSettings.mergeLandscapeHandleEdits(edited: AppSettings): AppSettings = mergeLandscapeEdits(edited)

/** 首次进横屏：复制竖屏布局（均匀分布）与手势规则，此后与竖屏无任何同步。 */
fun AppSettings.withLandscapeCopiedFromPortrait(): AppSettings = copy(
    landscapeTriggersInitialized = true,
    leftTriggerHandlesLandscape = redistributeLandscapeSideHandles(leftTriggerHandles),
    rightTriggerHandlesLandscape = redistributeLandscapeSideHandles(rightTriggerHandles),
    bottomTriggerHandlesLandscape = redistributeLandscapeSideHandles(bottomTriggerHandles),
    topTriggerHandlesLandscape = redistributeLandscapeSideHandles(topTriggerHandles),
    gestureRulesLandscape = gestureRules.map { it.copy() },
    leftDefaultTriggerModeLandscape = leftDefaultTriggerMode,
    rightDefaultTriggerModeLandscape = rightDefaultTriggerMode,
    bottomDefaultTriggerModeLandscape = bottomDefaultTriggerMode,
    topDefaultTriggerModeLandscape = topDefaultTriggerMode,
)

/** 已有横屏布局但未迁移手势存储时，从竖屏复制一份手势（仅当横屏手势为空）。 */
fun AppSettings.withLandscapeGesturesMigratedIfNeeded(): AppSettings {
    if (!landscapeTriggersInitialized && !hasStoredLandscapeTriggerHandles()) return this
    if (gestureRulesLandscape.isNotEmpty()) return this
    return copy(
        landscapeTriggersInitialized = true,
        gestureRulesLandscape = gestureRules.map { it.copy() },
        leftDefaultTriggerModeLandscape = leftDefaultTriggerMode,
        rightDefaultTriggerModeLandscape = rightDefaultTriggerMode,
        bottomDefaultTriggerModeLandscape = bottomDefaultTriggerMode,
        topDefaultTriggerModeLandscape = topDefaultTriggerMode,
    )
}

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

/** 横屏运行态：已初始化则用横屏布局与手势（允许空列表），否则回退竖屏。 */
fun AppSettings.withRuntimeLandscapeSettings(isLandscape: Boolean): AppSettings {
    if (!isLandscape || !landscapeTriggersInitialized) return this
    return copy(
        leftTriggerHandles = leftTriggerHandlesLandscape,
        rightTriggerHandles = rightTriggerHandlesLandscape,
        bottomTriggerHandles = bottomTriggerHandlesLandscape,
        topTriggerHandles = topTriggerHandlesLandscape,
        gestureRules = gestureRulesLandscape,
        leftDefaultTriggerMode = leftDefaultTriggerModeLandscape,
        rightDefaultTriggerMode = rightDefaultTriggerModeLandscape,
        bottomDefaultTriggerMode = bottomDefaultTriggerModeLandscape,
        topDefaultTriggerMode = topDefaultTriggerModeLandscape,
    )
}

fun AppSettings.withRuntimeTriggerHandles(isLandscape: Boolean): AppSettings =
    withRuntimeLandscapeSettings(isLandscape)

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
    if (!isLandscape || !landscapeTriggersInitialized) return triggerHandles(side)
    return storedLandscapeTriggerHandles(side).filter { it.enabled }
}

fun AppSettings.runtimeAllTriggerHandles(side: PanelSide, isLandscape: Boolean): List<TriggerHandle> {
    if (!isLandscape || !landscapeTriggersInitialized) return allTriggerHandles(side)
    return storedLandscapeTriggerHandles(side)
}

fun AppSettings.runtimeTriggerHandle(
    side: PanelSide,
    handleId: String,
    isLandscape: Boolean,
): TriggerHandle? = runtimeAllTriggerHandles(side, isLandscape).firstOrNull { it.id == handleId }

fun AppSettings.runtimeGestureRules(isLandscape: Boolean): List<GestureRule> {
    if (!isLandscape || !landscapeTriggersInitialized) return gestureRules
    return gestureRulesLandscape
}

fun AppSettings.runtimeDefaultTriggerMode(side: PanelSide, isLandscape: Boolean): GestureTriggerMode {
    if (!isLandscape || !landscapeTriggersInitialized) return defaultTriggerModeFor(side)
    return when (side) {
        PanelSide.LEFT -> leftDefaultTriggerModeLandscape
        PanelSide.RIGHT -> rightDefaultTriggerModeLandscape
        PanelSide.BOTTOM -> bottomDefaultTriggerModeLandscape
        PanelSide.TOP -> topDefaultTriggerModeLandscape
    }
}
