@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, kotlinx.coroutines.FlowPreview::class)

package com.slideindex.app

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.overlay.LayoutPreviewContent
import com.slideindex.app.overlay.LayoutPreviewFocus
import com.slideindex.app.overlay.WidgetPickerOverlayWindow
import com.slideindex.app.service.OverlayService
import com.slideindex.app.service.OverlayServiceController
import com.slideindex.app.service.QuickLauncherAddTrampoline
import com.slideindex.app.service.ShellCommandEditorTrampoline
import com.slideindex.app.service.ShellCommandPanelTrampoline
import com.slideindex.app.service.ShellCommandPanelTrampolineActivity
import com.slideindex.app.service.ShellCommandResultTrampoline
import com.slideindex.app.service.WidgetBindTrampolineActivity
import com.slideindex.app.service.WidgetPickerTrampoline
import com.slideindex.app.service.ToggleGestureTrampolineActivity
import com.slideindex.app.ui.navigation.MainNavHost
import com.slideindex.app.ui.navigation.NavPermissionStates
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.TaskManagerUtil
import com.slideindex.app.di.AppDependencies
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var deps: AppDependencies

    internal val permissionStates = NavPermissionStates(
        overlayGranted = mutableStateOf(false),
        notificationGranted = mutableStateOf(true),
        usageAccessGranted = mutableStateOf(false),
        shizukuGranted = mutableStateOf(false),
        accessibilityGranted = mutableStateOf(false),
        batteryOptimizationExempt = mutableStateOf(false),
        writeSecureSettingsGranted = mutableStateOf(false),
        notificationListenerEnabled = mutableStateOf(false),
    )

    private val currentIntentAction = mutableStateOf<String?>(null)
    private lateinit var overlayServiceController: OverlayServiceController
    private val permissionRefreshHandler = Handler(Looper.getMainLooper())
    private var accessibilitySettingsObserver: ContentObserver? = null

    private val permissionRefreshRetryRunnable = object : Runnable {
        private var retryIndex = 0

        fun reset() {
            retryIndex = 0
        }

        override fun run() {
            refreshPermissionState()
            refreshServiceState()
            if (retryIndex < PERMISSION_REFRESH_RETRY_DELAYS_MS.lastIndex) {
                retryIndex++
                permissionRefreshHandler.postDelayed(
                    this,
                    PERMISSION_REFRESH_RETRY_DELAYS_MS[retryIndex],
                )
            }
        }
    }

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
        currentIntentAction.value = intent?.action
        reportShortcutUsageIfNeeded(intent?.action)
        overlayServiceController = OverlayServiceController(
            context = this,
            permissionStates = permissionStates,
            scope = lifecycleScope,
            settingsRepository = deps.settingsRepository,
        )
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        enableEdgeToEdge()
        refreshPermissionState()

        setContent {
            MainNavHost(
                activity = this@MainActivity,
                deps = deps,
                permissionStates = permissionStates,
                initialIntentAction = currentIntentAction.value,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntentAction.value = intent.action
        reportShortcutUsageIfNeeded(intent.action)
    }

    private fun reportShortcutUsageIfNeeded(action: String?) {
        val shortcutId = when (action) {
            "com.slideindex.app.action.TOGGLE_GESTURE" -> "toggle_gesture"
            "com.slideindex.app.action.OPEN_NOTIFICATION_HISTORY" -> "notification_hub"
            "com.slideindex.app.action.OPEN_SHELL_PANEL" -> "shell_panel"
            else -> null
        }
        shortcutId?.let { ShortcutManagerCompat.reportShortcutUsed(this, it) }
    }

    private fun setupDynamicShortcuts() {
        val toggleGestureShortcut = ShortcutInfoCompat.Builder(this, "toggle_gesture")
            .setShortLabel(getString(R.string.shortcut_toggle_gesture))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_launcher)) // fallback icon
            .setIntent(Intent(this, ToggleGestureTrampolineActivity::class.java).setAction("com.slideindex.app.action.TOGGLE_GESTURE"))
            .build()

        val notificationHubShortcut = ShortcutInfoCompat.Builder(this, "notification_hub")
            .setShortLabel(getString(R.string.shortcut_notification_hub))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_launcher))
            .setIntent(Intent(this, MainActivity::class.java).setAction("com.slideindex.app.action.OPEN_NOTIFICATION_HISTORY"))
            .build()

        val shellPanelShortcut = ShortcutInfoCompat.Builder(this, "shell_panel")
            .setShortLabel(getString(R.string.shortcut_shell_panel))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_launcher))
            .setIntent(ShellCommandPanelTrampolineActivity.createIntent(this).setAction("com.slideindex.app.action.OPEN_SHELL_PANEL"))
            .build()

        ShortcutManagerCompat.setDynamicShortcuts(
            this,
            listOf(toggleGestureShortcut, notificationHubShortcut, shellPanelShortcut)
        )
    }

    override fun onStart() {
        super.onStart()
        registerAccessibilitySettingsObserver()
    }

    override fun onResume() {
        super.onResume()
        setupDynamicShortcuts()
        refreshPermissionState()
        schedulePermissionRefreshRetries()
        refreshServiceState()
        com.slideindex.app.widget.WidgetPopupHost.startListening(this)
        lifecycleScope.launch {
            applyHideFromRecents(deps.settingsRepository.settings.first().hideFromRecents)
        }
    }

    override fun onDestroy() {
        cancelPermissionRefreshRetries()
        unregisterAccessibilitySettingsObserver()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }

    override fun onPause() {
        cancelPermissionRefreshRetries()
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
            overlayServiceController.stopPreviewOnPause()
        }
        super.onPause()
    }

    override fun onStop() {
        unregisterAccessibilitySettingsObserver()
        super.onStop()
    }

    private fun schedulePermissionRefreshRetries() {
        cancelPermissionRefreshRetries()
        permissionRefreshRetryRunnable.reset()
        permissionRefreshHandler.postDelayed(
            permissionRefreshRetryRunnable,
            PERMISSION_REFRESH_RETRY_DELAYS_MS[0],
        )
    }

    private fun cancelPermissionRefreshRetries() {
        permissionRefreshHandler.removeCallbacks(permissionRefreshRetryRunnable)
        permissionRefreshRetryRunnable.reset()
    }

    private fun registerAccessibilitySettingsObserver() {
        if (accessibilitySettingsObserver != null) return
        val observer = object : ContentObserver(permissionRefreshHandler) {
            override fun onChange(selfChange: Boolean) {
                refreshPermissionState()
                refreshServiceState()
            }
        }
        val resolver = contentResolver
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED),
            false,
            observer,
        )
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            observer,
        )
        accessibilitySettingsObserver = observer
    }

    private fun unregisterAccessibilitySettingsObserver() {
        accessibilitySettingsObserver?.let { observer ->
            runCatching { contentResolver.unregisterContentObserver(observer) }
        }
        accessibilitySettingsObserver = null
    }

    internal fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    internal fun sendOverlayPreviewIntent(
        action: String,
        content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY,
        focus: LayoutPreviewFocus? = null,
    ) {
        overlayServiceController.sendPreviewIntent(action, content, focus)
    }

    internal fun refreshPermissionState() {
        overlayServiceController.refreshPermissionState()
    }

    internal fun applyHideFromRecents(hide: Boolean) {
        getSystemService(ActivityManager::class.java)
            ?.appTasks
            ?.firstOrNull()
            ?.setExcludeFromRecents(hide)
    }

    internal fun refreshServiceState() {
        overlayServiceController.refreshServiceState()
    }

    private companion object {
        /** Gaps after resume; first tick is relative to scheduling (see [schedulePermissionRefreshRetries]). */
        private val PERMISSION_REFRESH_RETRY_DELAYS_MS = longArrayOf(300L, 500L)
    }
}
