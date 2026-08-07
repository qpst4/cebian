package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.ui.graphics.vector.ImageVector

internal fun homeLeadingIcon(outlined: Boolean, filled: ImageVector, outlinedIcon: ImageVector): ImageVector =
    if (outlined) outlinedIcon else filled

internal object HomeLeadingIcons {
    fun gesture(outlined: Boolean) = homeLeadingIcon(outlined, Icons.Default.Gesture, Icons.Outlined.Gesture)
    fun batteryKeepAlive(outlined: Boolean) =
        if (outlined) HomeCustomIcons.BatteryKeepAliveOutlined else Icons.Default.BatteryChargingFull
    fun cornerWheel(outlined: Boolean) = HomeCustomIcons.CornerWheel
    fun triggerCollection(outlined: Boolean) =
        homeLeadingIcon(outlined, Icons.Default.TouchApp, Icons.Outlined.TouchApp)
    fun floatBall(outlined: Boolean) =
        homeLeadingIcon(outlined, Icons.Default.RadioButtonChecked, Icons.Outlined.Circle)
    fun gestureAngle(outlined: Boolean) = homeLeadingIcon(outlined, Icons.Default.Tune, Icons.Outlined.Tune)
    fun gestureAnimation(outlined: Boolean) =
        homeLeadingIcon(outlined, Icons.Default.Animation, Icons.Outlined.Animation)
    fun excludedApps(outlined: Boolean) = homeLeadingIcon(outlined, Icons.Default.Block, Icons.Outlined.Block)
    fun hideTriggerLandscape(outlined: Boolean) =
        homeLeadingIcon(outlined, Icons.Default.ScreenRotation, Icons.Outlined.ScreenRotation)
    fun hideTriggerLock(outlined: Boolean) = homeLeadingIcon(outlined, Icons.Default.Lock, Icons.Outlined.Lock)
    fun hideTriggerLauncher(outlined: Boolean) = homeLeadingIcon(outlined, Icons.Default.Home, Icons.Outlined.Home)
    fun freeWindow(outlined: Boolean) =
        homeLeadingIcon(outlined, Icons.Default.PictureInPictureAlt, Icons.Outlined.PictureInPicture)
    fun haptic(outlined: Boolean) = homeLeadingIcon(outlined, Icons.Default.Vibration, Icons.Outlined.Vibration)
    fun themeMode(outlined: Boolean) =
        homeLeadingIcon(outlined, Icons.Default.BrightnessMedium, Icons.Outlined.Brightness6)
    fun themeSeedColor(outlined: Boolean) = homeLeadingIcon(outlined, Icons.Default.Colorize, Icons.Outlined.Colorize)
    fun themePalette(outlined: Boolean) = homeLeadingIcon(outlined, Icons.Default.Style, Icons.Outlined.Style)
    fun themeColorSpec(outlined: Boolean) = homeLeadingIcon(outlined, Icons.Default.Contrast, Icons.Outlined.Contrast)
}
