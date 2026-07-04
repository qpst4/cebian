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
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.overlay.LayoutPreviewContent
import com.slideindex.app.service.OverlayService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.ExcludedAppsScreen
import com.slideindex.app.ui.GestureAngleSettingsScreen
import com.slideindex.app.ui.FreeWindowPreviewScreen
import com.slideindex.app.ui.FreeWindowSettingsScreen
import com.slideindex.app.ui.HiddenAppsScreen
import com.slideindex.app.ui.LayoutSettingsScreen
import com.slideindex.app.ui.MainScreen
import com.slideindex.app.ui.QuickLauncherEditorScreen
import com.slideindex.app.ui.SettingsDestination
import com.slideindex.app.ui.ShellCommandPanelScreen
import com.slideindex.app.ui.SideGestureSettingsScreen
import com.slideindex.app.ui.TriggerAppearanceSettingsScreen
import com.slideindex.app.ui.TriggerCollectionScreen
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
            var sideGestureHandleId by remember { mutableStateOf(TriggerHandle.DEFAULT_ID) }
            var appearanceParentSide by remember { mutableStateOf(PanelSide.LEFT) }
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
                        onOpenTriggerCollection = {
                            destination = SettingsDestination.TriggerCollection
                        },
                        onOpenGestureAngle = {
                            destination = SettingsDestination.GestureAngle
                        },
                        onOpenQuickLauncher = {
                            destination = SettingsDestination.QuickLauncher
                        },
                        onOpenShellCommands = {
                            destination = SettingsDestination.ShellCommands
                        },
                        onThemeColorChange = { color ->
                            lifecycleScope.launch { app.settingsRepository.setThemeColor(color) }
                        },
                    )

                    SettingsDestination.Layout -> LayoutSettingsScreen(
                        settings = settings,
                        serviceEnabled = overlayGranted && notificationGranted,
                        onBack = {
                            sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
                            destination = SettingsDestination.Main
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

                    SettingsDestination.TriggerCollection -> TriggerCollectionScreen(
                        settings = settings,
                        serviceEnabled = overlayGranted && notificationGranted,
                        onBack = { destination = SettingsDestination.Main },
                        onOpenLeftTrigger = { handleId ->
                            sideGestureHandleId = handleId
                            destination = SettingsDestination.SideGesturesLeft
                        },
                        onOpenRightTrigger = { handleId ->
                            sideGestureHandleId = handleId
                            destination = SettingsDestination.SideGesturesRight
                        },
                        onAddTriggerPair = {
                            lifecycleScope.launch {
                                app.settingsRepository.addTriggerHandlePair()
                            }
                        },
                        onRemoveTriggerPair = { handleId ->
                            lifecycleScope.launch {
                                app.settingsRepository.removeTriggerHandlePair(handleId)
                            }
                        },
                    )

                    SettingsDestination.SideGesturesLeft -> SideGestureSettingsScreen(
                        side = PanelSide.LEFT,
                        handleId = sideGestureHandleId,
                        settings = settings,
                        serviceEnabled = overlayGranted && notificationGranted,
                        onBack = {
                            sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
                            destination = SettingsDestination.TriggerCollection
                        },
                        onOpenAppearanceSettings = {
                            appearanceParentSide = PanelSide.LEFT
                            destination = SettingsDestination.SideGesturesAppearance
                        },
                        onSlotConfigChange = { handleId, trigger, action, mode ->
                            lifecycleScope.launch {
                                app.settingsRepository.setSlotConfig(
                                    PanelSide.LEFT,
                                    trigger,
                                    action,
                                    mode,
                                    handleId,
                                )
                            }
                        },
                        onDefaultTriggerModeChange = { mode ->
                            lifecycleScope.launch {
                                app.settingsRepository.setDefaultTriggerMode(PanelSide.LEFT, mode)
                            }
                        },
                    )

                    SettingsDestination.SideGesturesRight -> SideGestureSettingsScreen(
                        side = PanelSide.RIGHT,
                        handleId = sideGestureHandleId,
                        settings = settings,
                        serviceEnabled = overlayGranted && notificationGranted,
                        onBack = {
                            sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
                            destination = SettingsDestination.TriggerCollection
                        },
                        onOpenAppearanceSettings = {
                            appearanceParentSide = PanelSide.RIGHT
                            destination = SettingsDestination.SideGesturesAppearance
                        },
                        onSlotConfigChange = { handleId, trigger, action, mode ->
                            lifecycleScope.launch {
                                app.settingsRepository.setSlotConfig(
                                    PanelSide.RIGHT,
                                    trigger,
                                    action,
                                    mode,
                                    handleId,
                                )
                            }
                        },
                        onDefaultTriggerModeChange = { mode ->
                            lifecycleScope.launch {
                                app.settingsRepository.setDefaultTriggerMode(PanelSide.RIGHT, mode)
                            }
                        },
                    )

                    SettingsDestination.SideGesturesAppearance -> TriggerAppearanceSettingsScreen(
                        side = appearanceParentSide,
                        handleId = sideGestureHandleId,
                        settings = settings,
                        serviceEnabled = overlayGranted && notificationGranted,
                        onBack = {
                            sendOverlayPreviewIntent(OverlayService.ACTION_PREVIEW_STOP)
                            destination = when (appearanceParentSide) {
                                PanelSide.LEFT -> SettingsDestination.SideGesturesLeft
                                PanelSide.RIGHT -> SettingsDestination.SideGesturesRight
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
                        onGestureHintEnabledChange = { enabled ->
                            lifecycleScope.launch {
                                app.settingsRepository.setGestureHintEnabled(enabled)
                            }
                        },
                        onGestureHintStyleChange = { style ->
                            lifecycleScope.launch {
                                app.settingsRepository.setGestureHintStyle(style)
                            }
                        },
                        onEdgeWidthChange = { value ->
                            lifecycleScope.launch {
                                app.settingsRepository.setEdgeTriggerWidthDp(appearanceParentSide, value)
                            }
                        },
                        onTriggerVerticalRangeChange = { handleId, top, bottom ->
                            lifecycleScope.launch {
                                app.settingsRepository.setTriggerVerticalRange(
                                    appearanceParentSide,
                                    handleId,
                                    top,
                                    bottom,
                                )
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

                    SettingsDestination.GestureAngle -> GestureAngleSettingsScreen(
                        config = settings.gestureAngleConfig,
                        onBack = { destination = SettingsDestination.Main },
                        onSave = { config ->
                            lifecycleScope.launch {
                                app.settingsRepository.setGestureAngleConfig(config)
                            }
                        },
                    )

                    SettingsDestination.QuickLauncher -> QuickLauncherEditorScreen(
                        settings = settings,
                        onBack = { destination = SettingsDestination.Main },
                        onSaveItems = { items ->
                            lifecycleScope.launch {
                                app.settingsRepository.setQuickLauncherItems(items)
                            }
                        },
                        onColumnsChange = { value ->
                            lifecycleScope.launch {
                                app.settingsRepository.setQuickLauncherColumnsPerPage(value)
                            }
                        },
                        onRowsChange = { value ->
                            lifecycleScope.launch {
                                app.settingsRepository.setQuickLauncherRowsPerPage(value)
                            }
                        },
                    )

                    SettingsDestination.ShellCommands -> ShellCommandPanelScreen(
                        settings = settings,
                        shizukuGranted = shizukuGranted,
                        onBack = { destination = SettingsDestination.Main },
                        onSaveCommands = { items ->
                            lifecycleScope.launch {
                                app.settingsRepository.setShellCommands(items)
                            }
                        },
                        onRequestShizuku = {
                            TaskManagerUtil.requestPermission()
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
            if (overlayGranted && notificationGranted) {
                app.settingsRepository.setServiceEnabled(true)
            }
            val shouldRun = overlayGranted && notificationGranted
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
