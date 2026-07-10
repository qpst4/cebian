package com.slideindex.app.ui.navigation

import android.content.Intent
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
    ) {
        activity.sendOverlayPreviewIntent(action, content)
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
