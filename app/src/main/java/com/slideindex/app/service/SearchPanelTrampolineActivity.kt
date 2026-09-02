package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.R
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.overlay.searchpanel.SearchPanelOverlayWindow
import com.slideindex.app.overlay.searchpanel.SearchPanelQueryBridge
import com.slideindex.app.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchPanelTrampolineActivity : ComponentActivity() {

    @Inject
    lateinit var deps: AppDependencies

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        handleOpenIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenIntent(intent)
    }

    private fun handleOpenIntent(intent: Intent?) {
        val searchQuery = StashClipboardTrampolineActivity.resolveSearchQuery(intent)
        lifecycleScope.launch {
            if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(this@SearchPanelTrampolineActivity)) {
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
                this@SearchPanelTrampolineActivity,
                deps.settingsRepository,
            )

            searchQuery?.let { SearchPanelQueryBridge.rememberQuery(this@SearchPanelTrampolineActivity, it) }

            val shown = retryShowPanel()
            if (!shown) {
                toast(R.string.shortcut_panel_open_failed)
            }
            finishTransparent()
        }
    }

    private suspend fun retryShowPanel(): Boolean {
        repeat(SHOW_RETRY_ATTEMPTS) { attempt ->
            if (SearchPanelOverlayWindow.show(this)) {
                return true
            }
            if (attempt < SHOW_RETRY_ATTEMPTS - 1) {
                delay(SHOW_RETRY_DELAY_MS)
            }
        }
        return false
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
        const val ACTION_OPEN_SEARCH_PANEL = "com.slideindex.app.action.OPEN_SEARCH_PANEL"

        private const val SCHEME = "cebian"
        private const val HOST = "open"
        private const val PATH_SEARCH_PANEL = "search-panel"
        private const val QUERY_PARAM = "q"

        private const val SHOW_RETRY_ATTEMPTS = 5
        private const val SHOW_RETRY_DELAY_MS = 150L

        fun uriFor(query: String? = null): Uri =
            Uri.Builder()
                .scheme(SCHEME)
                .authority(HOST)
                .appendPath(PATH_SEARCH_PANEL)
                .apply {
                    query?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        appendQueryParameter(QUERY_PARAM, it)
                    }
                }
                .build()

        fun createIntent(context: Context, query: String? = null): Intent =
            Intent(Intent.ACTION_VIEW, uriFor(query)).apply {
                setClass(context, SearchPanelTrampolineActivity::class.java)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
            }

        fun createActionIntent(context: Context, query: String? = null): Intent =
            Intent(context, SearchPanelTrampolineActivity::class.java).apply {
                action = ACTION_OPEN_SEARCH_PANEL
                query?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    putExtra(QUERY_PARAM, it)
                }
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
            }
    }
}
