package com.slideindex.app.settings



import com.slideindex.app.gesture.GestureAction



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

        fun clampVerticalEdgeWidthDp(value: Float): Float = value.coerceIn(0f, 120f)

        fun clampVerticalEdgeHeightDp(value: Float): Float = value.coerceIn(0f, 200f)

        fun clampHorizontalEdgeWidthDp(value: Float): Float = value.coerceIn(0f, 160f)

        fun clampHorizontalEdgeHeightDp(value: Float): Float = value.coerceIn(0f, 160f)

        fun clampTriggerSlopDp(value: Float): Float = value.coerceIn(24f, 96f)

        fun clampOuterDiameterDp(value: Float): Float = value.coerceIn(200f, 420f)

        fun clampInnerDiameterDp(value: Float, outer: Float): Float =

            value.coerceIn(40f, (outer - 40f).coerceAtLeast(48f))

        fun clampBubbleSizeDp(value: Float): Float = value.coerceIn(12f, 28f)

    }

}


