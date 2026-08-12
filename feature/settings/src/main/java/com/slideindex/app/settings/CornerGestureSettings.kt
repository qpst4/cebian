package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.SelectedHintMetrics

data class CornerGestureSettings(
    val enabled: Boolean = false,
    val leftEnabled: Boolean = true,
    val rightEnabled: Boolean = true,
    /** 竖边宽度：贴屏幕竖直边缘的触发区厚度（dp） */
    val verticalEdgeWidthDp: Float = 16f,
    /** 竖边高度：竖直触发区向上延伸的长度（dp） */
    val verticalEdgeHeightDp: Float = 147f,
    /** 横边宽度：水平触发区沿底边延伸的长度（dp） */
    val horizontalEdgeWidthDp: Float = 98f,
    /** 横边高度：贴屏幕水平边缘的触发区厚度（dp） */
    val horizontalEdgeHeightDp: Float = 13f,
    val triggerSlopDp: Float = 40f,
    val hideInLandscape: Boolean = true,
    val landscapePreventFalseTouch: Boolean = true,
    val overrideSystemNav: Boolean = false,
    val outerDiameterDp: Float = 320f,
    val innerDiameterDp: Float = 56f,
    val bubbleSizeDp: Float = 17f,
    val cancelOutsideWheel: Boolean = true,
    val progressiveLayers: Boolean = true,
    /** 手指移到新槽位时是否震动（仍受全局触感反馈总开关约束） */
    val slotHapticEnabled: Boolean = true,
    /** 高亮槽位时在屏幕上部显示图标与名称（类似蜂窝启动）。 */
    val showSelectedName: Boolean = true,
    val selectedHintIconSizeDp: Int = SelectedHintMetrics.DEFAULT_ICON_SIZE_DP,
    /**
     * 轮盘背景：
     * [BACKGROUND_NONE] 透明；
     * [BACKGROUND_BLUR] 实时跨窗口高斯模糊（FLAG_BLUR_BEHIND）；
     * [BACKGROUND_BLACK] 纯色遮罩。
     */
    val backgroundStyle: Int = BACKGROUND_NONE,
    val blurDp: Int = DEFAULT_BLUR_DP,
    val dimPercent: Int = DEFAULT_DIM_PERCENT,
    /** 左右轮盘共用同一套槽位配置。关闭后可分别配置左/右轮盘。 */
    val unifiedSlots: Boolean = true,
    val innerZoneAction: GestureAction = GestureAction.CornerInnerCancel,
    val leftSlots: List<GestureAction> = CornerRadialMenuCodec.defaultLeftSlots(),
    val rightSlots: List<GestureAction> = CornerRadialMenuCodec.defaultRightSlots(),
) {
    fun hasActiveTriggerZone(): Boolean =
        (verticalEdgeWidthDp > 0f && verticalEdgeHeightDp > 0f) ||
            (horizontalEdgeWidthDp > 0f && horizontalEdgeHeightDp > 0f)

    fun isActiveInCurrentOrientation(landscape: Boolean): Boolean {
        if (!enabled) return false
        if (landscape && hideInLandscape) return false
        return leftEnabled || rightEnabled
    }

    companion object {
        const val BACKGROUND_NONE = 0
        const val BACKGROUND_BLUR = 1
        const val BACKGROUND_BLACK = 2

        const val DEFAULT_BLUR_DP = 36
        const val MIN_BLUR_DP = 0
        const val MAX_BLUR_DP = 72

        const val DEFAULT_DIM_PERCENT = 22
        const val MIN_DIM_PERCENT = 0
        const val MAX_DIM_PERCENT = 60

        fun clampVerticalEdgeWidthDp(value: Float): Float = value.coerceIn(0f, 120f)
        fun clampVerticalEdgeHeightDp(value: Float): Float = value.coerceIn(0f, 200f)
        fun clampHorizontalEdgeWidthDp(value: Float): Float = value.coerceIn(0f, 160f)
        fun clampHorizontalEdgeHeightDp(value: Float): Float = value.coerceIn(0f, 160f)
        fun clampTriggerSlopDp(value: Float): Float = value.coerceIn(24f, 96f)
        fun clampOuterDiameterDp(value: Float): Float = value.coerceIn(200f, 420f)
        fun clampInnerDiameterDp(value: Float, outer: Float): Float =
            value.coerceIn(40f, (outer - 40f).coerceAtLeast(48f))
        fun clampBubbleSizeDp(value: Float): Float = value.coerceIn(12f, 28f)
        fun clampBlurDp(value: Int): Int = value.coerceIn(MIN_BLUR_DP, MAX_BLUR_DP)
        fun clampDimPercent(value: Int): Int = value.coerceIn(MIN_DIM_PERCENT, MAX_DIM_PERCENT)
        fun clampBackgroundStyle(value: Int): Int = when (value) {
            BACKGROUND_BLUR, BACKGROUND_BLACK -> value
            else -> BACKGROUND_NONE
        }
    }
}
