package com.slideindex.app.gesture

/** 侧滑手势配置等非点选槽位场景不使用。 */
enum class SlotPickerKind {
    /** 快速启动器、圆环启动器、蜂窝、音量面板扩展槽、悬浮指针边缘条等 overlay 点选槽位 */
    OverlayTap,
    /** 边角轮盘槽位 */
    CornerWheel,
    /** 摇杆功能环槽位 */
    FloatingPointerRadialSlot,
    /** 摇杆长按动作（允许 [GestureAction.OpenFloatingPointerRadialMenu]） */
    FloatingPointerRadialLongPress,
    /** 指尖环槽位 */
    FingertipRing,
    /** 边角手势内区动作 */
    CornerInnerZone,
}

/** 动作选择页列表策略；[EdgeGesture] 与 [Slot] 必须显式择一，避免漏接槽位过滤。 */
sealed interface ActionPickerCatalogPolicy {
    data class Slot(val kind: SlotPickerKind) : ActionPickerCatalogPolicy

    /** 侧滑手势槽位：保留 requiresContinuousTriggerOnly 等动作。 */
    data object EdgeGesture : ActionPickerCatalogPolicy
}

fun ActionPickerCatalogPolicy.slotPickerKindOrNull(): SlotPickerKind? = when (this) {
    is ActionPickerCatalogPolicy.Slot -> kind
    ActionPickerCatalogPolicy.EdgeGesture -> null
}

fun GestureAction.isEligibleForSlotPicker(kind: SlotPickerKind): Boolean {
    if (requiresContinuousTriggerOnly()) return false
    if (this is GestureAction.FloatingPointer) return false
    // 指尖环仅允许绑定侧滑触钮（ActionPickerCatalogPolicy.EdgeGesture），所有槽位点选场景禁止。
    if (this is GestureAction.FingertipRing) return false

    return when (kind) {
        SlotPickerKind.OverlayTap,
        SlotPickerKind.CornerWheel,
        -> !isCornerInnerZoneOnly() &&
            this !is GestureAction.OpenFloatingPointerRadialMenu

        SlotPickerKind.FloatingPointerRadialSlot ->
            this !is GestureAction.OpenFloatingPointerRadialMenu

        SlotPickerKind.FloatingPointerRadialLongPress -> true

        SlotPickerKind.FingertipRing ->
            this !is GestureAction.OpenFloatingPointerRadialMenu

        SlotPickerKind.CornerInnerZone ->
            this !is GestureAction.OpenFloatingPointerRadialMenu &&
            this !is GestureAction.None
    }
}

fun GestureAction.sanitizeForSlotPicker(kind: SlotPickerKind): GestureAction =
    if (isEligibleForSlotPicker(kind)) {
        this
    } else {
        when (kind) {
            SlotPickerKind.CornerInnerZone -> GestureAction.CornerInnerCancel
            else -> GestureAction.None
        }
    }
