package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.R
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.overlay.FloatBallStashPanel
import com.slideindex.app.overlay.StashPanelInitialTab
import com.slideindex.app.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StashClipboardTrampolineActivity : ComponentActivity() {

    @Inject
    lateinit var deps: AppDependencies

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        val initialTab = resolveInitialTab(intent)
        lifecycleScope.launch {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(this@StashClipboardTrampolineActivity)) {
                toast(R.string.gesture_action_stash_panel_permission)
                startActivity(PermissionHelper.accessibilitySettingsIntent())
                finishTransparent()
                return@launch
            }

            val settings = deps.settingsRepository.settings.first()
            if (!settings.serviceEnabled) {
                toast(R.string.gesture_action_stash_panel_permission)
                finishTransparent()
                return@launch
            }

            OverlayServiceLifecycle.syncFromSettings(
                this@StashClipboardTrampolineActivity,
                deps.settingsRepository,
            )

            val shown = retryShowPanel(initialTab)
            if (shown) {
                reportShortcutUsage(initialTab)
            } else {
                toast(R.string.shortcut_panel_open_failed)
            }
            finishTransparent()
        }
    }

    private suspend fun retryShowPanel(tab: StashPanelInitialTab): Boolean {
        repeat(SHOW_RETRY_ATTEMPTS) { attempt ->
            if (FloatBallStashPanel.show(this, initialTab = tab)) {
                return true
            }
            if (attempt < SHOW_RETRY_ATTEMPTS - 1) {
                delay(SHOW_RETRY_DELAY_MS)
            }
        }
        return false
    }

    private fun reportShortcutUsage(tab: StashPanelInitialTab) {
        val shortcutId = when (tab) {
            StashPanelInitialTab.Stash -> SHORTCUT_ID_STASH
            StashPanelInitialTab.Clipboard -> SHORTCUT_ID_CLIPBOARD
        }
        ShortcutManagerCompat.reportShortcutUsed(this, shortcutId)
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private fun finishTransparent() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val ACTION_OPEN_STASH = "com.slideindex.app.action.OPEN_STASH_PANEL"
        const val ACTION_OPEN_CLIPBOARD = "com.slideindex.app.action.OPEN_CLIPBOARD_PANEL"

        const val SHORTCUT_ID_STASH = "stash_panel"
        const val SHORTCUT_ID_CLIPBOARD = "clipboard_panel"

        private const val SCHEME = "cebian"
        private const val HOST = "open"
        private const val PATH_STASH = "stash"
        private const val PATH_CLIPBOARD = "clipboard"

        private const val SHOW_RETRY_ATTEMPTS = 5
        private const val SHOW_RETRY_DELAY_MS = 150L

        fun uriFor(tab: StashPanelInitialTab): Uri =
            Uri.Builder()
                .scheme(SCHEME)
                .authority(HOST)
                .appendPath(
                    when (tab) {
                        StashPanelInitialTab.Stash -> PATH_STASH
                        StashPanelInitialTab.Clipboard -> PATH_CLIPBOARD
                    },
                )
                .build()

        fun createIntent(context: Context, tab: StashPanelInitialTab): Intent =
            Intent(Intent.ACTION_VIEW, uriFor(tab)).apply {
                setClass(context, StashClipboardTrampolineActivity::class.java)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
            }

        fun createActionIntent(context: Context, tab: StashPanelInitialTab): Intent =
            Intent(context, StashClipboardTrampolineActivity::class.java).apply {
                action = when (tab) {
                    StashPanelInitialTab.Stash -> ACTION_OPEN_STASH
                    StashPanelInitialTab.Clipboard -> ACTION_OPEN_CLIPBOARD
                }
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
            }

        fun resolveInitialTab(intent: Intent?): StashPanelInitialTab {
            intent?.data?.let { uri ->
                if (uri.scheme.equals(SCHEME, ignoreCase = true) && uri.host == HOST) {
                    return when (uri.pathSegments.firstOrNull()?.lowercase()) {
                        PATH_CLIPBOARD -> StashPanelInitialTab.Clipboard
                        PATH_STASH -> StashPanelInitialTab.Stash
                        else -> StashPanelInitialTab.Stash
                    }
                }
            }
            return when (intent?.action) {
                ACTION_OPEN_CLIPBOARD -> StashPanelInitialTab.Clipboard
                else -> StashPanelInitialTab.Stash
            }
        }
    }
}
