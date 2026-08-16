package com.slideindex.app.settings

/**
 * Search panel chrome background — same three modes as [HoneycombDisplaySettings].
 */
object SearchPanelBackgroundStyle {
    const val BLUR = HoneycombDisplaySettings.BACKGROUND_BLUR
    const val BLACK = HoneycombDisplaySettings.BACKGROUND_BLACK
    /** System wallpaper decoded + Gaussian blur (not live behind-window blur). */
    const val WALLPAPER_BLUR = HoneycombDisplaySettings.BACKGROUND_WALLPAPER_BLUR

    const val DEFAULT = BLACK

    fun fromPrefs(styleId: Int?, wallpaperBlurLegacy: Boolean?): Int {
        if (styleId != null) {
            return styleId.coerceIn(BLUR, WALLPAPER_BLUR)
        }
        return if (wallpaperBlurLegacy == true) WALLPAPER_BLUR else DEFAULT
    }
}

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

/** Candidate section order: canonical list top-down, or fully reversed. */
enum class SearchPanelListOrder {
    /** Canonical section order from top to bottom. */
    TOP_DOWN,

    /** Canonical section order reversed. */
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

/** How matched apps are shown in the search-panel candidate area. */
enum class SearchPanelAppDisplayStyle {
    /** Horizontal icon strip inside a grouped card. */
    ICONS,

    /** Vertical rows matching other candidate cards. */
    LIST,
    ;

    companion object {
        fun fromId(id: String?): SearchPanelAppDisplayStyle =
            entries.firstOrNull { it.name == id } ?: ICONS
    }
}
