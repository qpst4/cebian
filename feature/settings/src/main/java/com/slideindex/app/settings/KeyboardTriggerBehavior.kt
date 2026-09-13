package com.slideindex.app.settings

/** 软键盘弹出时对触发器（悬浮球 + 侧边触钮）的统一策略。 */
enum class KeyboardTriggerBehavior(val storageKey: String) {
    /** 保持原位置与尺寸（默认）。 */
    OVERLAY("overlay"),
    /** 移到键盘上方。 */
    MOVE_UP("move_up"),
    /** 收窄触发区，减少误触。 */
    NARROW("narrow"),
    /** 键盘弹出时禁用触发器。 */
    DISABLE("disable"),
    ;

    companion object {
        val selectable: List<KeyboardTriggerBehavior> = listOf(OVERLAY, MOVE_UP, NARROW, DISABLE)

        fun fromStorageKey(key: String?): KeyboardTriggerBehavior =
            entries.firstOrNull { it.storageKey == key } ?: OVERLAY
    }
}

fun AppSettings.keyboardTriggerBehavior(isLandscape: Boolean): KeyboardTriggerBehavior =
    if (isLandscape) {
        edgeTrigger.keyboardTriggerBehaviorLandscape
    } else {
        edgeTrigger.keyboardTriggerBehaviorPortrait
    }
