package com.slideindex.app.settings

/** 取词面板呈现风格。 */
enum class PickResultPanelStyle(val storageKey: String) {
    /** 同屏卡片流（推荐）：图文同屏展示，图片与文本各自拥有专属圆角底座卡片与操作栏。 */
    INTEGRATED_BOTTOM_BAR("integrated_bottom_bar"),
    /** 左右分页 Tab：文本与截图独立分页跟手滑屏。 */
    TAB_PAGED("tab_paged"),
    ;

    companion object {
        fun fromStorageKey(key: String?): PickResultPanelStyle =
            when (key) {
                "integrated_bottom_bar", "integrated_scroll", "classic_stack" -> INTEGRATED_BOTTOM_BAR
                "tab_paged" -> TAB_PAGED
                else -> INTEGRATED_BOTTOM_BAR
            }
    }
}

