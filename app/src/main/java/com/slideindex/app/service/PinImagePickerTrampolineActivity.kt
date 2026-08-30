package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.R
import com.slideindex.app.overlay.ScreenPinManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Trampoline activity that launches system photo picker / content picker,
 * decodes the chosen image bitmap and pins it to screen via [ScreenPinManager.pinImage].
 */
class PinImagePickerTrampolineActivity : ComponentActivity() {

    private val pickLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            handlePickedUri(uri)
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            pickLauncher.launch("image/*")
        }.onFailure {
            Toast.makeText(this, R.string.stash_pin_add_failed, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun handlePickedUri(uri: Uri) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                decodeSampledBitmap(uri)
            }
            if (bitmap != null) {
                ScreenPinManager.pinImage(applicationContext, bitmap)
            } else {
                Toast.makeText(this@PinImagePickerTrampolineActivity, R.string.stash_pin_add_failed, Toast.LENGTH_SHORT).show()
            }
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun decodeSampledBitmap(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(stream, null, options)
                val maxDim = 2048
                var sampleSize = 1
                while (options.outWidth / sampleSize > maxDim || options.outHeight / sampleSize > maxDim) {
                    sampleSize *= 2
                }
                contentResolver.openInputStream(uri)?.use { secondStream ->
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    BitmapFactory.decodeStream(secondStream, null, decodeOptions)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, PinImagePickerTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }
}