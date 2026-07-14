package com.slideindex.app.ui.navigation

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavBackStack
import com.slideindex.app.MainActivity
import com.slideindex.app.R
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.overlay.LayoutPreviewContent
import com.slideindex.app.overlay.LayoutPreviewFocus
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.service.OverlayService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.KeepAliveHelper
import com.slideindex.app.util.MediaSessionHelper
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.SecureSettingsHelper
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Stable
class MainNavContext(
    val activity: MainActivity,
    val deps: AppDependencies,
    val backStack: NavBackStack<AppNavKey>,
    val permissionStates: NavPermissionStates,
    val floatingPointerAreaPreviewEnabledState: MutableState<Boolean>,
    val rootBottomContentPadding: Dp,
) {
    @Composable
    fun collectAppSettings(): AppSettings {
        val settings by deps.settingsRepository.settings.collectAsStateWithLifecycle(
            initialValue = AppSettings(),
        )
        return settings
    }

    @Composable
    fun collectPermissions(): NavPermissionSnapshot = permissionStates.collect()

    @Composable
    fun collectAreaPreviewEnabled(): Boolean {
        val enabled by floatingPointerAreaPreviewEnabledState
        return enabled
    }

    fun setFloatingPointerAreaPreviewEnabled(enabled: Boolean) {
        floatingPointerAreaPreviewEnabledState.value = enabled
    }

    fun gestureActive(settings: AppSettings, permissions: NavPermissionSnapshot): Boolean =
        settings.serviceEnabled && permissions.accessibilityGranted && permissions.notificationGranted

    fun navigate(key: AppNavKey) = backStack.navigate(key)

    fun navigateBackTo(key: AppNavKey) = backStack.navigateBackTo(key)

    fun replaceRoot(key: AppNavKey) = backStack.replaceRoot(key)

    fun launch(block: suspend () -> Unit) {
        activity.lifecycleScope.launch { block() }
    }

    fun sendOverlayPreviewIntent(
        action: String,
        content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY,
        focus: LayoutPreviewFocus? = null,
    ) {
        activity.sendOverlayPreviewIntent(action, content, focus)
    }

    fun startFocusedTriggerPreview(side: PanelSide, handleId: String) {
        retainFocusedTriggerPreview(
            LayoutPreviewFocus(side = side, handleId = handleId, showSwipeDistances = false),
        )
    }

    fun startSwipeDistancePreview(side: PanelSide, handleId: String) {
        retainFocusedTriggerPreview(
            LayoutPreviewFocus(side = side, handleId = handleId, showSwipeDistances = true),
        )
    }

    fun releaseFocusedTriggerPreview() {
        cancelPendingTriggerPreviewStop()
        focusedTriggerPreviewRetainCount = (focusedTriggerPreviewRetainCount - 1).coerceAtLeast(0)
        if (focusedTriggerPreviewRetainCount > 0) return
        scheduleTriggerPreviewStop()
    }

    fun stopTriggerPreview() {
        cancelPendingTriggerPreviewStop()
        focusedTriggerPreviewRetainCount = 0
        sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
    }

    private fun retainFocusedTriggerPreview(focus: LayoutPreviewFocus) {
        cancelPendingTriggerPreviewStop()
        focusedTriggerPreviewRetainCount++
        sendOverlayPreviewIntent(
            action = OverlayService.ACTION_PREVIEW_START,
            content = LayoutPreviewContent.TRIGGER_ONLY,
            focus = focus,
        )
    }

    private fun scheduleTriggerPreviewStop() {
        cancelPendingTriggerPreviewStop()
        pendingTriggerPreviewStop = Runnable {
            if (focusedTriggerPreviewRetainCount == 0) {
                sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
            }
        }
        triggerPreviewHandler.postDelayed(pendingTriggerPreviewStop!!, TRIGGER_PREVIEW_HANDOFF_MS)
    }

    private fun cancelPendingTriggerPreviewStop() {
        pendingTriggerPreviewStop?.let(triggerPreviewHandler::removeCallbacks)
        pendingTriggerPreviewStop = null
    }

    companion object {
        private const val TRIGGER_PREVIEW_HANDOFF_MS = 80L
        private val triggerPreviewHandler = Handler(Looper.getMainLooper())
        private var focusedTriggerPreviewRetainCount = 0
        private var pendingTriggerPreviewStop: Runnable? = null
    }

    fun startActivity(intent: Intent) {
        activity.startActivity(intent)
    }

    fun requestNotificationPermission() {
        activity.requestNotificationPermission()
    }

    fun requestShizuku() {
        TaskManagerUtil.requestPermission()
    }

    fun refreshServiceState() {
        activity.refreshServiceState()
    }

    fun refreshPermissionState() {
        activity.refreshPermissionState()
    }

    fun previewHaptic(enabled: Boolean = true, strengthLevel: Int? = null) {
        launch {
            val latest = deps.settingsRepository.settings.first()
            HapticHelper.preview(
                activity.window.decorView,
                latest.copy(
                    hapticEnabled = enabled,
                    hapticStrengthLevel = strengthLevel ?: latest.hapticStrengthLevel,
                ),
            )
        }
    }

    fun openAccessibilitySettings() {
        startActivity(PermissionHelper.accessibilitySettingsIntent())
    }

    fun openOverlaySettings() {
        startActivity(PermissionHelper.overlaySettingsIntent(activity))
    }

    fun openNotificationListenerSettings() {
        startActivity(MediaSessionHelper.notificationListenerSettingsIntent())
    }

    fun openUsageAccessSettings() {
        startActivity(PermissionHelper.usageAccessSettingsIntent())
    }

    fun requestBatteryOptimization() {
        if (!PermissionHelper.requestBatteryOptimizationAccess(activity)) {
            deps.userMessageBus.showError(
                activity.getString(R.string.battery_optimization_request_failed),
            )
        }
    }

    fun openAutoStartSettings() {
        KeepAliveHelper.gotoSettings(activity)
    }

    fun requestSecureSettingsGrant(): Boolean {
        val granted = SecureSettingsHelper.grantViaShizuku(activity)
        refreshPermissionState()
        val messageRes = if (granted) {
            R.string.secure_settings_grant_success
        } else {
            R.string.secure_settings_grant_failed
        }
        val message = activity.getString(messageRes)
        if (granted) {
            deps.userMessageBus.showSuccess(message)
        } else {
            deps.userMessageBus.showError(message)
        }
        return granted
    }
}
