package com.slideindex.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.overlay.LayoutPreviewContent
import com.slideindex.app.service.OverlayService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.ExcludedAppsScreen
import com.slideindex.app.ui.FreeWindowPreviewScreen
import com.slideindex.app.ui.FreeWindowSettingsScreen
import com.slideindex.app.ui.HiddenAppsScreen
import com.slideindex.app.ui.LayoutSettingsScreen
import com.slideindex.app.ui.MainScreen
import com.slideindex.app.ui.QuickLauncherEditorScreen
import com.slideindex.app.ui.SettingsDestination
import com.slideindex.app.ui.SideGestureSettingsScreen
import com.slideindex.app.ui.theme.SlideIndexTheme
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private var overlayGranted by mutableStateOf(false)
    private var notificationGranted by mutableStateOf(true)
    private var usageAccessGranted by mutableStateOf(false)
    private var shizukuGranted by mutableStateOf(false)
    private var accessibilityGranted by mutableStateOf(false)

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        shizukuGranted = grantResult == PackageManager.PERMISSION_GRANTED
        if (shizukuGranted) {
            TaskManagerUtil.warmUp()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationGranted = granted || PermissionHelper.hasNotificationPermission(this)
        refreshServiceState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        enableEdgeToEdge()
        refreshPermissionState()

        val app = application as SlideIndexApp
        setContent {
            val settings by app.settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings(),
            )
            var destination by remember { mutableStateOf(SettingsDestination.Main) }
            SlideIndexTheme(
                seedColor = androidx.compose.ui.graphics.Color(settings.themeColorArgb),
            ) {
                when (destination) {
                    SettingsDestination.Main -> MainScreen(
                        settings = settings,
                        overlayGranted = overlayGranted,
                        notificationGranted = notificationGranted,
                        shizukuGranted = shizukuGranted,
                        accessibilityGranted = accessibilityGranted,
                        onRequestOverlay = {
                            startActivity(PermissionHelper.overlaySettingsIntent(this))
                        },
                        onRequestNotification = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onRequestShizuku = {
                            TaskManagerUtil.requestPermission()
                        },
                        onRequestAccessibility = {
                            startActivity(PermissionHelper.accessibilitySettingsIntent())
                        },
                        onServiceEnabledChange = { enabled ->
                            lifecycleScope.launch {
                                app.settingsRepository.setServiceEnabled(enabled)
                                refreshServiceState()
                            }
                        },
                        onHapticEnabledChange = { enabled ->
                            lifecycleScope.launch {
                                app.settingsRepository.setHapticEnabled(enabled)
                                if (enabled) {
                                    val latest = app.settingsRepository.settings.first()
                                    HapticHelper.preview(window.decorView, latest.copy(hapticEnabled = true))
                                }
                            }
                        },
                        onHapticStrengthChange = { level ->
                            lifecycleScope.launch {
                                app.settingsRepository.setHapticStrengthLevel(level)
                                val latest = app.settingsRepository.settings.first()
                                HapticHelper.preview(
                                    window.decorView,
                                    latest.copy(hapticEnabled = true, hapticStrengthLevel = level),
                                )
                            }
                        },
                        onOpenLayoutSettings = {
                            destination = SettingsDestination.Layout
                        },
                        onOpenFreeWindowSettings = {
                            destination = SettingsDestination.FreeWindow
                        },
                        onOpenHiddenAppsSettings = {
                            destination = SettingsDestination.HiddenApps
                        },
                        onOpenExcludedAppsSettings = {
                            destination = SettingsDestination.ExcludedApps
                        },
                        onOpenSideGesturesLeft = {
                            destination = SettingsDestination.SideGesturesLeft
                        },
                        onOpenSideGesturesRight = {
                            destination = SettingsDestination.SideGesturesRight
                        },
                        onThemeColorChange = { color ->
                            lifecycleScope.launch { app.settingsRepository.setThemeColor(color) }
                        },
                    )

                    SettingsDestination.Layout -> LayoutSettingsScreen(
                        settings = settings,
                        serviceEnabled = settings.serviceEnabled,
                        onBack = {
                            sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
                            destination = SettingsDestination.Main
                        },
                        onLeftEdgeChange = { enabled ->
                            lifecycleScope.launch { app.settingsRepository.setLeftEdgeEnabled(enabled) }
                        },
                        onRightEdgeChange = { enabled ->
                            lifecycleScope.launch { app.settingsRepository.setRightEdgeEnabled(enabled) }
                        },
                        onIndexHeightChange = { value ->
                            lifecycleScope.launch { app.settingsRepository.setIndexHeightFraction(value) }
                        },
                        onAppsPerRowChange = { value ->
                            lifecycleScope.launch { app.settingsRepository.setAppsPerRow(value) }
                        },
                        onPanelOpacityChange = { value ->
                            lifecycleScope.launch { app.settingsRepository.setPanelOpacity(value) }
                        },
                        onLayoutPreviewStart = {
                            sendOverlayPreviewIntent(
                                OverlayService.ACTION_PREVIEW_START,
                                LayoutPreviewContent.INDEX_ONLY,
                            )
                        },
                        onLayoutPreviewStop = {
                            sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
                        },
                    )

                    SettingsDestination.HiddenApps -> HiddenAppsScreen(
                        settings = settings,
                        onBack = { destination = SettingsDestination.Main },
                        onHideApp = { packageName ->
                            lifecycleScope.launch {
                                app.settingsRepository.addHiddenApp(packageName)
                            }
                        },
                        onUnhideApp = { packageName ->
                            lifecycleScope.launch {
                                app.settingsRepository.removeHiddenApp(packageName)
                            }
                        },
                    )

                    SettingsDestination.ExcludedApps -> ExcludedAppsScreen(
                        settings = settings,
                        usageAccessGranted = usageAccessGranted,
                        onBack = { destination = SettingsDestination.Main },
                        onRequestUsageAccess = {
                            startActivity(PermissionHelper.usageAccessSettingsIntent())
                        },
                        onExcludeApp = { packageName ->
                            lifecycleScope.launch {
                                app.settingsRepository.addExcludedTriggerApp(packageName)
                            }
                        },
                        onRemoveExcludedApp = { packageName ->
                            lifecycleScope.launch {
                                app.settingsRepository.removeExcludedTriggerApp(packageName)
                            }
                        },
                    )

                    SettingsDestination.FreeWindow -> FreeWindowSettingsScreen(
                        settings = settings,
                        onBack = { destination = SettingsDestination.Main },
                        onEnabledChange = { enabled ->
                            lifecycleScope.launch {
                                app.settingsRepository.setFreeWindowEnabled(enabled)
                            }
                        },
                        onLaunchPolicyChange = { policyId ->
                            lifecycleScope.launch {
                                app.settingsRepository.setAppLaunchPolicyId(policyId)
                            }
                        },
                        onLongPressDurationChange = { durationMs ->
                            lifecycleScope.launch {
                                app.settingsRepository.setLongPressLaunchDurationMs(durationMs)
                            }
                        },
                        onModeChange = { modeId ->
                            lifecycleScope.launch {
                                app.settingsRepository.setFreeWindowModeId(modeId)
                            }
                        },
                        onOpenPreview = { destination = SettingsDestination.FreeWindowPreview },
                    )

                    SettingsDestination.FreeWindowPreview -> FreeWindowPreviewScreen(
                        settings = settings,
                        onBack = { destination = SettingsDestination.FreeWindow },
                        onSave = { width, height, left, top ->
                            lifecycleScope.launch {
                                app.settingsRepository.setFreeWindowLayout(width, height, left, top)
                            }
                        },
                    )

                    SettingsDestination.SideGesturesLeft -> SideGestureSettingsScreen(
                        side = PanelSide.LEFT,
                        settings = settings,
                        serviceEnabled = settings.serviceEnabled,
                        onBack = {
                            sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
                            destination = SettingsDestination.Main
                        },
                        onSlotConfigChange = { trigger, action, mode ->
                            lifecycleScope.launch {
                                app.settingsRepository.setSlotConfig(
                                    PanelSide.LEFT,
                                    trigger,
                                    action,
                                    mode,
                                )
                            }
                        },
                        onDefaultTriggerModeChange = { mode ->
                            lifecycleScope.launch {
                                app.settingsRepository.setDefaultTriggerMode(PanelSide.LEFT, mode)
                            }
                        },
                        onShortSwipeDistanceChange = { value ->
                            lifecycleScope.launch {
                                app.settingsRepository.setShortSwipeDistanceDp(value)
                            }
                        },
                        onLongSwipeDistanceChange = { value ->
                            lifecycleScope.launch {
                                app.settingsRepository.setLongSwipeDistanceDp(value)
                            }
                        },
                        onOpenQuickLauncherEditor = {
                            destination = SettingsDestination.QuickLauncherLeft
                        },
                        onEdgeWidthChange = { value ->
                            lifecycleScope.launch {
                                app.settingsRepository.setEdgeTriggerWidthDp(PanelSide.LEFT, value)
                            }
                        },
                        onTriggerVerticalRangeChange = { top, bottom ->
                            lifecycleScope.launch {
                                app.settingsRepository.setTriggerVerticalRange(PanelSide.LEFT, top, bottom)
                            }
                        },
                        onAlignHandlesChange = { enabled ->
                            lifecycleScope.launch {
                                app.settingsRepository.setAlignHandlesEnabled(enabled)
                            }
                        },
                        onInterceptBackChange = { enabled ->
                            lifecycleScope.launch {
                                app.settingsRepository.setInterceptSystemBackGesture(enabled)
                            }
                        },
                        onLimitInterceptLengthChange = { enabled ->
                            lifecycleScope.launch {
                                app.settingsRepository.setLimitMaxInterceptLength(enabled)
                            }
                        },
                        onLayoutPreviewStart = {
                            sendOverlayPreviewIntent(
                                OverlayService.ACTION_PREVIEW_START,
                                LayoutPreviewContent.TRIGGER_ONLY,
                            )
                        },
                        onLayoutPreviewStop = {
                            sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
                        },
                    )

                    SettingsDestination.SideGesturesRight -> SideGestureSettingsScreen(
                        side = PanelSide.RIGHT,
                        settings = settings,
                        serviceEnabled = settings.serviceEnabled,
                        onBack = {
                            sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
                            destination = SettingsDestination.Main
                        },
                        onSlotConfigChange = { trigger, action, mode ->
                            lifecycleScope.launch {
                                app.settingsRepository.setSlotConfig(
                                    PanelSide.RIGHT,
                                    trigger,
                                    action,
                                    mode,
                                )
                            }
                        },
                        onDefaultTriggerModeChange = { mode ->
                            lifecycleScope.launch {
                                app.settingsRepository.setDefaultTriggerMode(PanelSide.RIGHT, mode)
                            }
                        },
                        onShortSwipeDistanceChange = { value ->
                            lifecycleScope.launch {
                                app.settingsRepository.setShortSwipeDistanceDp(value)
                            }
                        },
                        onLongSwipeDistanceChange = { value ->
                            lifecycleScope.launch {
                                app.settingsRepository.setLongSwipeDistanceDp(value)
                            }
                        },
                        onOpenQuickLauncherEditor = {
                            destination = SettingsDestination.QuickLauncherRight
                        },
                        onEdgeWidthChange = { value ->
                            lifecycleScope.launch {
                                app.settingsRepository.setEdgeTriggerWidthDp(PanelSide.RIGHT, value)
                            }
                        },
                        onTriggerVerticalRangeChange = { top, bottom ->
                            lifecycleScope.launch {
                                app.settingsRepository.setTriggerVerticalRange(PanelSide.RIGHT, top, bottom)
                            }
                        },
                        onAlignHandlesChange = { enabled ->
                            lifecycleScope.launch {
                                app.settingsRepository.setAlignHandlesEnabled(enabled)
                            }
                        },
                        onInterceptBackChange = { enabled ->
                            lifecycleScope.launch {
                                app.settingsRepository.setInterceptSystemBackGesture(enabled)
                            }
                        },
                        onLimitInterceptLengthChange = { enabled ->
                            lifecycleScope.launch {
                                app.settingsRepository.setLimitMaxInterceptLength(enabled)
                            }
                        },
                        onLayoutPreviewStart = {
                            sendOverlayPreviewIntent(
                                OverlayService.ACTION_PREVIEW_START,
                                LayoutPreviewContent.TRIGGER_ONLY,
                            )
                        },
                        onLayoutPreviewStop = {
                            sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
                        },
                    )

                    SettingsDestination.QuickLauncherLeft -> QuickLauncherEditorScreen(
                        side = PanelSide.LEFT,
                        settings = settings,
                        onBack = { destination = SettingsDestination.SideGesturesLeft },
                        onSaveItems = { items ->
                            lifecycleScope.launch {
                                app.settingsRepository.setQuickLauncherItems(PanelSide.LEFT, items)
                            }
                        },
                    )

                    SettingsDestination.QuickLauncherRight -> QuickLauncherEditorScreen(
                        side = PanelSide.RIGHT,
                        settings = settings,
                        onBack = { destination = SettingsDestination.SideGesturesRight },
                        onSaveItems = { items ->
                            lifecycleScope.launch {
                                app.settingsRepository.setQuickLauncherItems(PanelSide.RIGHT, items)
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
        refreshServiceState()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }

    override fun onPause() {
        sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
        super.onPause()
    }

    private fun sendOverlayPreviewIntent(
        action: String,
        content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY,
    ) {
        if (!overlayGranted) return
        val intent = Intent(this, OverlayService::class.java)
            .setAction(action)
            .putExtra(OverlayService.EXTRA_PREVIEW_CONTENT, content.name)
        startService(intent)
    }

    private fun refreshPermissionState() {
        overlayGranted = PermissionHelper.canDrawOverlays(this)
        notificationGranted = PermissionHelper.hasNotificationPermission(this)
        usageAccessGranted = PermissionHelper.hasUsageAccess(this)
        shizukuGranted = TaskManagerUtil.hasPermission()
        accessibilityGranted = PermissionHelper.isAccessibilityServiceEnabled(this)
        if (shizukuGranted) {
            TaskManagerUtil.warmUp()
        }
    }

    private fun refreshServiceState() {
        lifecycleScope.launch {
            val app = application as SlideIndexApp
            val settings = app.settingsRepository.settings.first()
            val shouldRun = settings.serviceEnabled && overlayGranted && notificationGranted
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
