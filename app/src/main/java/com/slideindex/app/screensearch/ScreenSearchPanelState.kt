package com.slideindex.app.screensearch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slideindex.app.screensearch.ScreenSearchCapture.ScrollDirection

class ScreenSearchPanelState {
    var keyword by mutableStateOf("")
    var accessibilityMode by mutableStateOf(true)
    var scrollDirection by mutableStateOf(ScrollDirection.DOWN)
    var isSearching by mutableStateOf(false)
}
