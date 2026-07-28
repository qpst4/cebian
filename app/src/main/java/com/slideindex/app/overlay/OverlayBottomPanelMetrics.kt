package com.slideindex.app.overlay

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val LandscapePanelMaxWidth = 560.dp
private const val LandscapePanelWidthFraction = 0.72f
private const val PortraitPanelMaxHeightFraction = 0.85f
private const val LandscapePanelMaxHeightFraction = 0.92f

/** 覆盖层底部面板：宽 > 高视为横屏。 */
@Composable
fun overlayIsLandscape(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    return size.width > size.height
}

@Composable
fun overlayContainerWidthDp(): Dp {
    val density = LocalDensity.current
    return with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
}

@Composable
fun overlayContainerHeightDp(): Dp {
    val density = LocalDensity.current
    return with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
}

/** 竖屏不限宽（返回 null）；横屏居中限宽。 */
@Composable
fun overlayBottomPanelMaxWidth(): Dp? {
    if (!overlayIsLandscape()) return null
    val screenWidth = overlayContainerWidthDp()
    return minOf(LandscapePanelMaxWidth, screenWidth * LandscapePanelWidthFraction)
}

@Composable
fun overlayBottomPanelMaxHeightFraction(): Float =
    if (overlayIsLandscape()) LandscapePanelMaxHeightFraction else PortraitPanelMaxHeightFraction

@Composable
fun overlayBottomPanelMaxHeight(): Dp =
    overlayContainerHeightDp() * overlayBottomPanelMaxHeightFraction()

@Composable
fun Modifier.overlayBottomPanelWidth(): Modifier {
    val maxWidth = overlayBottomPanelMaxWidth()
    return if (maxWidth != null) {
        fillMaxWidth().widthIn(max = maxWidth)
    } else {
        fillMaxWidth()
    }
}

@Composable
fun Modifier.overlayBottomPanelHeightCap(): Modifier =
    heightIn(max = overlayBottomPanelMaxHeight())
