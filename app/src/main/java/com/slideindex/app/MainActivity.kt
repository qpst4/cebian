@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, kotlinx.coroutines.FlowPreview::class)

package com.slideindex.app

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.overlay.FloatingPointerAreaPreviewOverlay
import com.slideindex.app.overlay.LayoutPreviewContent
import com.slideindex.app.overlay.WidgetPickerOverlayWindow
import com.slideindex.app.service.OverlayService
import com.slideindex.app.service.QuickLauncherAddTrampoline
import com.slideindex.app.service.ShellCommandEditorTrampoline
import com.slideindex.app.service.ShellCommandPanelTrampoline
import com.slideindex.app.service.ShellCommandResultTrampoline
import com.slideindex.app.service.WidgetBindTrampolineActivity
import com.slideindex.app.service.WidgetPickerTrampoline
import com.slideindex.app.ui.navigation.MainNavHost
import com.slideindex.app.ui.navigation.NavPermissionStates
import com.slideindex.app.util.MediaSessionHelper
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.SecureSettingsHelper
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    internal val permissionStates = NavPermissionStates(
        notificationGranted = mutableStateOf(true),
        usageAccessGranted = mutableStateOf(false),
        shizukuGranted = mutableStateOf(false),
        accessibilityGranted = mutableStateOf(false),
        batteryOptimizationExempt = mutableStateOf(false),
        writeSecureSettingsGranted = mutableStateOf(false),
        notificationListenerEnabled = mutableStateOf(false),
    )

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        permissionStates.shizukuGranted.value = grantResult == PackageManager.PERMISSION_GRANTED
        if (permissionStates.shizukuGranted.value) {
            TaskManagerUtil.warmUp()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionStates.notificationGranted.value =
            granted || PermissionHelper.hasNotificationPermission(this)
        refreshServiceState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        enableEdgeToEdge()
        refreshPermissionState()

        val app = application as SlideIndexApp
        setContent {
            MainNavHost(
                activity = this@MainActivity,
                app = app,
                permissionStates = permissionStates,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
        refreshServiceState()
        com.slideindex.app.widget.WidgetPopupHost.startListening(this)
        val app = application as SlideIndexApp
        lifecycleScope.launch {
            applyHideFromRecents(app.settingsRepository.settings.first().hideFromRecents)
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }

    override fun onPause() {
        if (!WidgetBindTrampolineActivity.isActive() &&
            !WidgetPickerOverlayWindow.isShowing
        ) {
            com.slideindex.app.widget.WidgetPopupHost.stopListening(this)
        }
        if (!QuickLauncherAddTrampoline.isActive() &&
            !ShellCommandPanelTrampoline.isActive() &&
            !ShellCommandEditorTrampoline.isActive() &&
            !ShellCommandResultTrampoline.isActive()
        ) {
            sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
            FloatingPointerAreaPreviewOverlay.hide()
        }
        super.onPause()
    }

    internal fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    internal fun sendOverlayPreviewIntent(
        action: String,
        content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY,
    ) {
        if (!permissionStates.accessibilityGranted.value) return
        val intent = Intent(this, OverlayService::class.java)
            .setAction(action)
            .putExtra(OverlayService.EXTRA_PREVIEW_CONTENT, content.name)
        startService(intent)
    }

    internal fun refreshPermissionState() {
        permissionStates.notificationGranted.value = PermissionHelper.hasNotificationPermission(this)
        permissionStates.usageAccessGranted.value = PermissionHelper.hasUsageAccess(this)
        permissionStates.shizukuGranted.value = TaskManagerUtil.hasPermission()
        permissionStates.accessibilityGranted.value = PermissionHelper.isAccessibilityServiceEnabled(this)
        permissionStates.batteryOptimizationExempt.value = PermissionHelper.isBatteryOptimizationExempt(this)
        permissionStates.writeSecureSettingsGranted.value = SecureSettingsHelper.hasWriteSecureSettings(this)
        permissionStates.notificationListenerEnabled.value = MediaSessionHelper.isNotificationListenerEnabled(this)
        if (permissionStates.shizukuGranted.value) {
            TaskManagerUtil.warmUp()
        }
    }

    internal fun applyHideFromRecents(hide: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        getSystemService(ActivityManager::class.java)
            ?.appTasks
            ?.firstOrNull()
            ?.setExcludeFromRecents(hide)
    }

    internal fun refreshServiceState() {
        lifecycleScope.launch {
            val app = application as SlideIndexApp
            val settings = app.settingsRepository.settings.first()
            if (settings.accessibilityKeepAliveEnabled &&
                permissionStates.writeSecureSettingsGranted.value &&
                settings.serviceEnabled
            ) {
                SecureSettingsHelper.ensureAccessibilityEnabled(this@MainActivity)
                permissionStates.accessibilityGranted.value =
                    PermissionHelper.isAccessibilityServiceEnabled(this@MainActivity)
            }
            val shouldRun = settings.serviceEnabled &&
                permissionStates.accessibilityGranted.value &&
                permissionStates.notificationGranted.value
            val serviceIntent = Intent(this@MainActivity, OverlayService::class.java)
            if (shouldRun) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } else {
                stopService(serviceIntent)
            }
        }
    }
}
