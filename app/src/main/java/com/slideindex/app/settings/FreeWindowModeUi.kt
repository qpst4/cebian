package com.slideindex.app.settings

import com.slideindex.app.R

val FreeWindowMode.titleRes: Int
    get() = when (this) {
        FreeWindowMode.STANDARD -> R.string.free_window_mode_standard
        FreeWindowMode.COLOROS -> R.string.free_window_mode_coloros
        FreeWindowMode.MAGICOS -> R.string.free_window_mode_magicos
        FreeWindowMode.ORIGINOS -> R.string.free_window_mode_originos
        FreeWindowMode.FLYME -> R.string.free_window_mode_flyme
        FreeWindowMode.NUBIA -> R.string.free_window_mode_nubia
    }

val FreeWindowMode.descRes: Int
    get() = when (this) {
        FreeWindowMode.STANDARD -> R.string.free_window_mode_standard_desc
        FreeWindowMode.COLOROS -> R.string.free_window_mode_coloros_desc
        FreeWindowMode.MAGICOS -> R.string.free_window_mode_magicos_desc
        FreeWindowMode.ORIGINOS -> R.string.free_window_mode_originos_desc
        FreeWindowMode.FLYME -> R.string.free_window_mode_flyme_desc
        FreeWindowMode.NUBIA -> R.string.free_window_mode_nubia_desc
    }
