package com.slideindex.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.showsShellCommandBadge
import com.slideindex.app.overlay.ShellCommandBadgeOverlay
import com.slideindex.app.overlay.corner.CornerSlotIconBitmap
import com.slideindex.app.settings.AppSettings

@Composable
fun GestureSlotActionIcon(
    action: GestureAction,
    settings: AppSettings,
    contentDescription: String?,
) {
    when (action) {
        is GestureAction.LaunchApp -> {
            Md3PickerPackageLeading(
                packageName = action.packageName,
                contentDescription = contentDescription,
            )
        }
        is GestureAction.LaunchShortcut -> {
            Md3PickerLaunchShortcutLeading(
                action = action,
                activityShortcuts = settings.activityShortcuts,
            )
        }
        is GestureAction.ExecuteShellCommand -> {
            GestureSlotShellCommandIcon(
                action = action,
                settings = settings,
                contentDescription = contentDescription,
            )
        }
        else -> {
            Icon(
                imageVector = gestureActionIcon(action),
                contentDescription = contentDescription,
            )
        }
    }
}

@Composable
private fun GestureSlotShellCommandIcon(
    action: GestureAction.ExecuteShellCommand,
    settings: AppSettings,
    contentDescription: String?,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val iconSize = 28.dp
    val iconPx = with(density) { iconSize.roundToPx() }.coerceAtLeast(12)
    val bitmap by produceState<android.graphics.Bitmap?>(null, action, settings.shellCommands) {
        value = CornerSlotIconBitmap.get(
            context = context,
            action = action,
            sizePx = iconPx,
            tintArgb = android.graphics.Color.WHITE,
            activityShortcuts = settings.activityShortcuts,
            shellCommands = settings.shellCommands,
        )
    }
    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
            )
        } else {
            Icon(
                imageVector = gestureActionIcon(action),
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
            )
        }
        if (action.showsShellCommandBadge(settings.shellCommands)) {
            ShellCommandBadgeOverlay(iconSize = iconSize)
        }
    }
}
