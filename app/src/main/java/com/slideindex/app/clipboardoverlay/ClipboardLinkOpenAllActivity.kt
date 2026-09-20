package com.slideindex.app.clipboardoverlay

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Browser
import android.util.Log
import androidx.core.net.toUri
import com.slideindex.app.overlay.pickresult.PickResultUrl

class ClipboardLinkOpenAllActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        val urls = intent.getStringArrayListExtra(EXTRA_URLS).orEmpty()
            .mapNotNull { PickResultUrl.normalizeOpenableUrl(it) ?: it.trim().takeIf(String::isNotBlank) }
            .distinct()
        if (urls.isEmpty()) {
            finishImmediate()
            return
        }
        launchNext(urls, 0)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun launchNext(urls: List<String>, index: Int) {
        if (isFinishing) return
        if (index >= urls.size) {
            finishImmediate()
            return
        }
        val viewIntent = Intent(Intent.ACTION_VIEW, urls[index].toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_CREATE_NEW_TAB, true)
            putExtra(Browser.EXTRA_APPLICATION_ID, packageName)
        }
        runCatching {
            startActivity(viewIntent, launchOptions())
        }.onFailure { error ->
            Log.w(TAG, "open url failed: ${urls[index]}", error)
        }
        val delay = if (index == urls.lastIndex) FINISH_DELAY_MS else INTERVAL_MS
        handler.postDelayed({ launchNext(urls, index + 1) }, delay)
    }

    private fun launchOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        val options = ActivityOptions.makeBasic()
        val backgroundStartMode = when {
            Build.VERSION.SDK_INT >= 36 -> ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
            else -> @Suppress("DEPRECATION") ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
        options.pendingIntentBackgroundActivityStartMode = backgroundStartMode
        return options.toBundle()
    }

    private fun finishImmediate() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val TAG = "ClipboardLinkOpenAll"
        private const val EXTRA_URLS = "urls"
        private const val EXTRA_CREATE_NEW_TAB = "create_new_tab"
        private const val INTERVAL_MS = 250L
        private const val FINISH_DELAY_MS = 200L

        fun start(context: Context, urls: List<String>) {
            val distinct = urls.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            if (distinct.isEmpty()) return
            val intent = Intent(context, ClipboardLinkOpenAllActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putStringArrayListExtra(EXTRA_URLS, ArrayList(distinct))
            }
            context.startActivity(intent)
        }
    }
}
