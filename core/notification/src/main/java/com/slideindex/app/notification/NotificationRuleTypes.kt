package com.slideindex.app.notification

enum class AppMatchMode {
    INCLUDE,
    EXCLUDE,
    ALL,
}

enum class TextMatchMode {
    ALL,
    CONTAIN_ANY,
    NOT_CONTAIN_ANY,
    CONTAIN_ALL,
    NOT_CONTAIN_ALL,
    CONTAIN_AND_NOT_CONTAIN,
    REGEX,
    ADVANCED,
}

enum class ScreenMode {
    BOTH,
    ON,
    OFF,
}

enum class NotificationRuleActionType {
    HIDE,
    MUTE,
    LATER,
    REPLACE,
    CHANGE_SOUND,
    CALL_NOTIFY,
    TTS,
    CLICK_BUTTON,
    OPEN,
    WEBHOOK,
}

data class AppTarget(
    val packageName: String,
    val userId: Int = 0,
)

data class AdvancedFilterNode(
    val field: String,
    val regex: String,
)

data class AdvancedFilter(
    val matchType: String = "ALL",
    val nodes: List<AdvancedFilterNode> = emptyList(),
)

data class RuleActionEntry(
    val type: NotificationRuleActionType,
    val delayTimeMs: Long = 0,
    val includeOngoing: Boolean = false,
    val laterTimesMs: List<Int> = emptyList(),
    val soundUri: String? = null,
    val replaceTitle: String? = null,
    val replaceMessage: String? = null,
    val buttonNames: List<String> = emptyList(),
    val buttonSemantic: Int = 0,
    val ttsTemplate: String? = null,
    val ttsBypassDnd: Boolean = true,
    val webhookUrl: String? = null,
    val webhookMethod: Int = 1,
    val webhookHeaders: String? = null,
    val webhookBody: String? = null,
    val webhookDistinct: Boolean = true,
    val notifyScreenOn: Int = 0,
    val notifyScreenOff: Int = 0,
)

object NotificationRuleChargeMask {
    const val BATTERY = 1
    const val AC = 2
    const val USB = 4
    const val WIRED = 6
    const val WIRELESS = 8
    const val ALL = 15
}

object NotificationRuleWeekDays {
    val ALL: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)
}
