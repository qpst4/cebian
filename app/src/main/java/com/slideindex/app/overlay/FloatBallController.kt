package com.slideindex.app.overlay

import android.content.Context
import androidx.core.net.toUri
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Syncs FV-style float ball visibility with accessibility service lifecycle and settings. */
class FloatBallController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
) {
    private var styleAssetMigrationDone = false

    fun apply(settings: AppSettings) {
        if (!PermissionHelper.isAccessibilityServiceEnabled(context)) {
            FloatBallOverlay.dismiss()
            return
        }
        if (!styleAssetMigrationDone) {
            styleAssetMigrationDone = true
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                migrateReadableStyleAssets(settings)
            }
        }
        if (settings.floatBallEnabled) {
            FloatBallOverlay.showOrUpdate(
                context = context,
                settings = settings,
                onPositionPersisted = { xFraction, yFraction ->
                    scope.launch {
                        settingsRepository.setFloatBallPosition(xFraction, yFraction)
                    }
                },
                onActiveSidePersisted = { side ->
                    scope.launch(Dispatchers.IO) {
                        settingsRepository.setFloatBallActiveSide(side)
                    }
                },
            )
        } else {
            FloatBallOverlay.dismiss()
        }
    }

    fun onConfigurationChanged() {
        if (FloatBallOverlay.isShowing) {
            FloatBallOverlay.relayout()
        }
    }

    fun stop() {
        FloatBallOverlay.dismiss()
    }

    private suspend fun migrateReadableStyleAssets(settings: AppSettings) {
        when (settings.floatBallStyleType) {
            com.slideindex.app.settings.FloatBallStyleType.GIF -> {
                val uri = settings.floatBallGifUri
                if (uri.startsWith("content://") && FloatBallStyleAssetStore.canRead(context, uri)) {
                    FloatBallStyleAssetStore.importGif(context, uri.toUri())?.let {
                        settingsRepository.setFloatBallGifUri(it)
                    }
                }
            }
            com.slideindex.app.settings.FloatBallStyleType.CUSTOM_IMAGE -> {
                val uri = settings.floatBallCustomImageUri
                if (uri.startsWith("content://") && FloatBallStyleAssetStore.canRead(context, uri)) {
                    FloatBallStyleAssetStore.importCustomImage(context, uri.toUri())?.let {
                        settingsRepository.setFloatBallCustomImageUri(it)
                    }
                }
            }
            com.slideindex.app.settings.FloatBallStyleType.SLIDESHOW -> {
                val uris = settings.floatBallSlideshowUris
                if (uris.isNotEmpty() && uris.all { it.startsWith("content://") }) {
                    val imported = FloatBallStyleAssetStore.importSlideshow(
                        context,
                        uris.map { it.toUri() },
                    )
                    if (imported.isNotEmpty()) {
                        settingsRepository.setFloatBallSlideshowUris(imported)
                    }
                }
            }
            else -> Unit
        }
    }
}
