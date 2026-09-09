package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import com.slideindex.app.overlay.OverlayFileChooserSuppressor
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Hosts the system file picker for [WebView] `<input type="file">` from overlay windows.
 *
 * Must stay alive until the picker returns — do not use [android.R.attr.noHistory].
 */
class WebViewFileChooserTrampolineActivity : ComponentActivity() {

    private var resultDelivered = false
    private var pickerLaunched = false

    private val pickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        uris?.forEach { uri ->
            runCatching {
                grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        finishWithResult(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            resultDelivered = savedInstanceState.getBoolean(STATE_RESULT_DELIVERED, false)
            if (resultDelivered) {
                finish()
                return
            }
        }
        val chooserIntent = WebViewFileChooserBridge.takeChooserIntent()
        if (chooserIntent == null) {
            finishWithResult(null)
            return
        }
        runCatching {
            pickerLaunched = true
            pickerLauncher.launch(chooserIntent)
        }.onFailure {
            pickerLaunched = false
            finishWithResult(null)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_RESULT_DELIVERED, resultDelivered)
    }

    override fun onDestroy() {
        if (!resultDelivered && pickerLaunched) {
            WebViewFileChooserBridge.deliver(null)
        }
        super.onDestroy()
    }

    private fun finishWithResult(uris: Array<Uri>?) {
        if (resultDelivered) return
        resultDelivered = true
        WebViewFileChooserBridge.deliver(uris)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val STATE_RESULT_DELIVERED = "result_delivered"

        fun createIntent(context: Context): Intent =
            Intent(context, WebViewFileChooserTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}

object WebViewFileChooserBridge {
    private var pendingCallback: ValueCallback<Array<Uri>>? = null
    private var pendingChooserIntent: Intent? = null

    fun launch(
        context: Context,
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams
    ): Boolean {
        cancelPending()
        pendingCallback = callback
        pendingChooserIntent = params.createIntent().apply {
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        OverlayFileChooserSuppressor.suppressForFileChooser()
        return runCatching {
            context.startActivity(WebViewFileChooserTrampolineActivity.createIntent(context))
        }.onFailure {
            deliver(null)
        }.isSuccess
    }

    internal fun takeChooserIntent(): Intent? {
        val intent = pendingChooserIntent
        pendingChooserIntent = null
        return intent
    }

    internal fun deliver(uris: Array<Uri>?) {
        val callback = pendingCallback
        pendingCallback = null
        pendingChooserIntent = null
        OverlayFileChooserSuppressor.restoreAfterFileChooser()
        callback?.onReceiveValue(uris)
    }

    fun cancelPending() {
        if (pendingCallback == null) return
        deliver(null)
    }
}
