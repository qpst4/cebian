package com.slideindex.app.settings

/** 取词面板搜索引擎区域默认展开状态。 */
enum class PickResultSearchGridDefaultState(val storageKey: String) {
    /** 记住上次使用状态（推荐）：根据用户上一次是展开还是收起自动记忆。 */
    REMEMBER_LAST("remember_last"),
    /** 始终展开：每次呼出面板默认展开搜索引擎网格。 */
    ALWAYS_EXPANDED("always_expanded"),
    /** 始终收起：每次呼出面板默认收起搜索引擎网格。 */
    ALWAYS_COLLAPSED("always_collapsed"),
    ;

    companion object {
        fun fromStorageKey(key: String?): PickResultSearchGridDefaultState =
            when (key) {
                "always_expanded" -> ALWAYS_EXPANDED
                "always_collapsed" -> ALWAYS_COLLAPSED
                else -> REMEMBER_LAST
            }
    }
}
