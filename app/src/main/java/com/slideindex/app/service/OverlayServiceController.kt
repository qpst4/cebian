package com.slideindex.app.service

import android.content.Context
import com.slideindex.app.settings.SettingsRepository
import android.content.Intent
import com.slideindex.app.overlay.FloatingPointerAreaPreviewOverlay
import com.slideindex.app.overlay.LayoutPreviewContent
import com.slideindex.app.overlay.LayoutPreviewFocus
import com.slideindex.app.ui.navigation.toNavSide
import com.slideindex.app.ui.navigation.NavPermissionStates
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.SecureSettingsHelper
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OverlayServiceController(
    private val context: Context,
    private val permissionStates: NavPermissionStates,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
) {
    fun sendPreviewIntent(
        action: String,
        content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY,
        focus: LayoutPreviewFocus? = null,
    ) {
        if (!permissionStates.accessibilityGranted.value) return
        val intent = Intent(context, OverlayService::class.java)
            .setAction(action)
            .putExtra(OverlayService.EXTRA_PREVIEW_CONTENT, content.name)
        if (focus != null) {
            intent.putExtra(OverlayService.EXTRA_PREVIEW_FOCUS_SIDE, focus.side.toNavSide())
            intent.putExtra(OverlayService.EXTRA_PREVIEW_HANDLE_ID, focus.handleId)
            intent.putExtra(OverlayService.EXTRA_PREVIEW_SHOW_SWIPE_DISTANCES, focus.showSwipeDistances)
        }
        context.startService(intent)
    }

    fun stopPreviewOnPause() {
        sendPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
        FloatingPointerAreaPreviewOverlay.hide()
    }

    fun refreshPermissionState() {
        permissionStates.notificationGranted.value = PermissionHelper.hasNotificationPermission(context)
        permissionStates.usageAccessGranted.value = PermissionHelper.hasUsageAccess(context)
        permissionStates.shizukuGranted.value = TaskManagerUtil.hasPermission()
        permissionStates.accessibilityGranted.value =
            PermissionHelper.isAccessibilityServiceEnabled(context)
        permissionStates.batteryOptimizationExempt.value =
            PermissionHelper.isBatteryOptimizationExempt(context)
        permissionStates.writeSecureSettingsGranted.value =
            SecureSettingsHelper.hasWriteSecureSettings(context)
        permissionStates.notificationListenerEnabled.value =
            com.slideindex.app.util.MediaSessionHelper.isNotificationListenerEnabled(context)
        if (permissionStates.shizukuGranted.value) {
            TaskManagerUtil.warmUp()
        }
    }

    fun refreshServiceState() {
        scope.launch {
            val settings = settingsRepository.settings.first()
            if (settings.accessibilityKeepAliveEnabled &&
                permissionStates.writeSecureSettingsGranted.value &&
                settings.serviceEnabled
            ) {
                SecureSettingsHelper.ensureAccessibilityEnabled(context)
                permissionStates.accessibilityGranted.value =
                    PermissionHelper.isAccessibilityServiceEnabled(context)
            }
            val shouldRun = settings.serviceEnabled &&
                permissionStates.accessibilityGranted.value &&
                permissionStates.notificationGranted.value
            val serviceIntent = Intent(context, OverlayService::class.java)
            if (shouldRun) {
                context.startForegroundService(serviceIntent)
            } else {
                context.stopService(serviceIntent)
            }
        }
    }
}
