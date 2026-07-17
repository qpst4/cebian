package com.slideindex.app.service

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.R
import com.slideindex.app.di.AppDependencies
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Receives [Intent.ACTION_SEND] images from other apps, then shows the float-ball pick-result
 * overlay and runs on-device OCR. Requires the accessibility overlay host to be connected.
 */
@AndroidEntryPoint
class ShareImageOcrTrampolineActivity : ComponentActivity() {

    @Inject
    lateinit var deps: AppDependencies

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleHandleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        scheduleHandleShareIntent(intent)
    }

    private fun scheduleHandleShareIntent(shareIntent: Intent?) {
        if (
            shareIntent?.action != Intent.ACTION_SEND &&
            shareIntent?.action != Intent.ACTION_SEND_MULTIPLE
        ) {
            finish()
            return
        }
        window.decorView.post {
            lifecycleScope.launch {
                handleShareIntent(shareIntent)
                if (!isFinishing) {
                    finish()
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                }
            }
        }
    }

    private suspend fun handleShareIntent(shareIntent: Intent) {
        if (!SlideIndexAccessibilityService.isConnected()) {
            Toast.makeText(this, R.string.share_image_ocr_service_required, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val uri = ShareImageOcrCoordinator.resolveImageUri(shareIntent)
        if (uri == null) {
            Toast.makeText(this, R.string.share_image_ocr_image_load_failed, Toast.LENGTH_SHORT).show()
            return
        }
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val modelId = deps.settingsRepository.readSnapshot().floatBallOcrModelId
        ShareImageOcrCoordinator.handleSharedImage(
            context = this@ShareImageOcrTrampolineActivity,
            uri = uri,
            modelId = modelId,
        )
    }
}
