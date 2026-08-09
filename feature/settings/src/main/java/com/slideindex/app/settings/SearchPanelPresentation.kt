package com.slideindex.app.settings

/** How the search panel is presented over other apps. */
enum class SearchPanelPresentationMode {
    /** Current bottom sheet style. */
    BOTTOM_SHEET,

    /** Near-fullscreen overlay; search bar can pin to screen top/bottom. */
    FULLSCREEN,
    ;

    companion object {
        fun fromId(id: String?): SearchPanelPresentationMode =
            entries.firstOrNull { it.name == id } ?: BOTTOM_SHEET
    }
}

/** Search field placement within the chosen presentation. */
enum class SearchPanelBarPosition {
    TOP,
    BOTTOM,
    ;

    companion object {
        fun fromId(id: String?): SearchPanelBarPosition =
            entries.firstOrNull { it.name == id } ?: TOP
    }
}

/** Candidate section order: apps at top (top-down) or apps at bottom (bottom-up). */
enum class SearchPanelListOrder {
    /** Apps first, then other candidate types downward. */
    TOP_DOWN,

    /** Other types above, apps last (near engines when search bar is bottom). */
    BOTTOM_UP,
    ;

    companion object {
        fun fromId(id: String?): SearchPanelListOrder = when (id) {
            TOP_DOWN.name, "GROW_FROM_TOP" -> TOP_DOWN
            BOTTOM_UP.name, "GROW_FROM_BOTTOM" -> BOTTOM_UP
            else -> TOP_DOWN
        }

        /** Prefer new key; fall back to legacy one-handed boolean. */
        fun fromPrefs(orderId: String?, oneHandedLegacy: Boolean?): SearchPanelListOrder {
            if (orderId != null) return fromId(orderId)
            return if (oneHandedLegacy == true) BOTTOM_UP else TOP_DOWN
        }
    }
}
