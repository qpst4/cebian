package com.slideindex.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue

class NavPermissionStates(
    val notificationGranted: MutableState<Boolean>,
    val usageAccessGranted: MutableState<Boolean>,
    val shizukuGranted: MutableState<Boolean>,
    val accessibilityGranted: MutableState<Boolean>,
    val batteryOptimizationExempt: MutableState<Boolean>,
    val writeSecureSettingsGranted: MutableState<Boolean>,
    val notificationListenerEnabled: MutableState<Boolean>,
)

data class NavPermissionSnapshot(
    val notificationGranted: Boolean,
    val usageAccessGranted: Boolean,
    val shizukuGranted: Boolean,
    val accessibilityGranted: Boolean,
    val batteryOptimizationExempt: Boolean,
    val writeSecureSettingsGranted: Boolean,
    val notificationListenerEnabled: Boolean,
)

@Composable
fun NavPermissionStates.collect(): NavPermissionSnapshot {
    val notificationGranted by notificationGranted
    val usageAccessGranted by usageAccessGranted
    val shizukuGranted by shizukuGranted
    val accessibilityGranted by accessibilityGranted
    val batteryOptimizationExempt by batteryOptimizationExempt
    val writeSecureSettingsGranted by writeSecureSettingsGranted
    val notificationListenerEnabled by notificationListenerEnabled
    return NavPermissionSnapshot(
        notificationGranted = notificationGranted,
        usageAccessGranted = usageAccessGranted,
        shizukuGranted = shizukuGranted,
        accessibilityGranted = accessibilityGranted,
        batteryOptimizationExempt = batteryOptimizationExempt,
        writeSecureSettingsGranted = writeSecureSettingsGranted,
        notificationListenerEnabled = notificationListenerEnabled,
    )
}
