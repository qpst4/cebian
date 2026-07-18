package com.slideindex.app.search

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.graphics.Bitmap
import com.slideindex.app.R
import com.slideindex.app.autofill.OtpAutoInputNodeHelper
import com.slideindex.app.overlay.FloatBallTextPick
import com.slideindex.app.service.LaunchTrampolineActivity
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.util.PackageActivityResolver
import com.slideindex.app.util.TaskManagerUtil

object SearchEngineLauncher {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun launchImageShare(context: Context, engine: SearchEngineConfig, bitmap: Bitmap): Boolean {
        if (engine.engineType != SearchEngineType.SHARE_IMAGE_TO_APP) return false
        val pkg = engine.targetPackage?.takeIf { it.isNotBlank() } ?: return false
        val activity = engine.targetActivity?.takeIf { it.isNotBlank() } ?: return false
        return FloatBallTextPick.shareScreenshotTo(
            context,
            bitmap,
            ComponentName(pkg, activity),
        )
    }

    fun launch(context: Context, engine: SearchEngineConfig, query: String): Boolean {
        val text = query.trim()
        if (text.isBlank()) {
            Toast.makeText(context, R.string.search_engine_query_empty, Toast.LENGTH_SHORT).show()
            return false
        }
        return when (engine.engineType) {
            SearchEngineType.DIRECT_LINK -> launchDirectLink(context, engine, text)
            SearchEngineType.EXTERN_JUMP_LINK -> launchExternJump(context, engine, text)
            SearchEngineType.JUMP_TO_ACTIVITY -> launchJumpActivity(context, engine, text)
            SearchEngineType.SHARE_TO_APP,
            SearchEngineType.SHARE_IMAGE_TO_APP,
            -> false
        }
    }

    private fun launchDirectLink(context: Context, engine: SearchEngineConfig, query: String): Boolean {
        val template = engine.searchLink?.takeIf { it.isNotBlank() } ?: return false
        val url = formatQuery(template, query)
        val intent = buildViewIntent(url, engine.targetPackage) ?: return false
        return startActivity(context, intent, useTrampoline = isIntentUri(url))
    }

    private fun launchExternJump(context: Context, engine: SearchEngineConfig, query: String): Boolean {
        val template = engine.externJumpLink?.takeIf { it.isNotBlank() } ?: return false
        val url = formatQuery(template, query)
        val intent = parseIntentUri(url, engine.externJumpPackage) ?: return false
        return startActivity(context, intent, useTrampoline = true)
    }

    private fun launchJumpActivity(context: Context, engine: SearchEngineConfig, query: String): Boolean {
        val pkg = engine.targetPackage?.takeIf { it.isNotBlank() }
        val activity = engine.targetActivity?.takeIf { it.isNotBlank() }
        if (!engine.searchLink.isNullOrBlank()) {
            if (launchDirectLink(context, engine, query)) return true
        }
        if (pkg == null) return false
        if (activity != null) {
            if (PackageActivityResolver.isActivityExported(context, pkg, activity)) {
                val intent = Intent()
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .setComponent(ComponentName(pkg, activity))
                val started = startActivity(context, intent)
                if (started) {
                    if (engine.autoInputEnter) {
                        scheduleAutoInput(query)
                    }
                    return true
                }
            }
            return launchNonExportedActivity(
                context,
                pkg,
                activity,
                query,
                engine.autoInputEnter,
            )
        }
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return false
        val started = startActivity(context, intent)
        if (started && engine.autoInputEnter) {
            scheduleAutoInput(query)
        }
        return started
    }

    private fun launchNonExportedActivity(
        context: Context,
        pkg: String,
        activity: String,
        query: String,
        autoInputEnter: Boolean,
    ): Boolean {
        if (!TaskManagerUtil.hasPermission()) {
            Toast.makeText(context, R.string.search_engine_shizuku_required, Toast.LENGTH_LONG).show()
            return false
        }
        if (autoInputEnter && !SlideIndexAccessibilityService.isConnected()) {
            Toast.makeText(context, R.string.search_engine_accessibility_required, Toast.LENGTH_LONG).show()
            return false
        }
        if (!NonExportedActivityLauncher.launch(pkg, activity)) {
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            return false
        }
        if (autoInputEnter) {
            scheduleAutoInput(query, initialDelayMs = 700L, retryDelaysMs = listOf(600L, 900L))
        }
        return true
    }

    private fun scheduleAutoInput(
        query: String,
        initialDelayMs: Long = 450L,
        retryDelaysMs: List<Long> = emptyList(),
    ) {
        val delays = buildList {
            add(initialDelayMs)
            var accumulated = initialDelayMs
            retryDelaysMs.forEach { step ->
                accumulated += step
                add(accumulated)
            }
        }
        delays.forEach { delayMs ->
            mainHandler.postDelayed({
                val service = SlideIndexAccessibilityService.accessibilityInstance() ?: return@postDelayed
                val root = service.rootInActiveWindow ?: return@postDelayed
                OtpAutoInputNodeHelper.performAutoInput(
                    root = root,
                    code = query,
                    autoEnter = true,
                )
                root.recycle()
            }, delayMs)
        }
    }

    private fun formatQuery(template: String, query: String): String {
        val encoded = Uri.encode(query)
        return template
            .replace("%q", encoded)
            .replace("%s", encoded)
    }

    private fun isIntentUri(url: String): Boolean =
        url.startsWith("intent://") || url.startsWith("intent:")

    private fun buildViewIntent(url: String, packageName: String? = null): Intent? {
        if (isIntentUri(url)) {
            return parseIntentUri(url, packageName)
        }
        return Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            packageName?.takeIf { it.isNotBlank() }?.let { setPackage(it) }
        }
    }

    private fun parseIntentUri(uri: String, overridePackage: String? = null): Intent? =
        runCatching {
            Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                overridePackage?.takeIf { it.isNotBlank() }?.let { setPackage(it) }
            }
        }.getOrNull()

    private fun startActivity(context: Context, intent: Intent, useTrampoline: Boolean = false): Boolean {
        return runCatching {
            val launchIntent = if (useTrampoline) {
                LaunchTrampolineActivity.createIntent(context, intent)
            } else {
                intent
            }
            context.startActivity(launchIntent)
            true
        }.getOrElse {
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }
}
