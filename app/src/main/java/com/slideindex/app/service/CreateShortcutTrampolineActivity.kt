package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.slideindex.app.util.AppShortcutLoader

/**
 * Relays "create shortcut" flows from overlay context: hides overlay windows, starts the host
 * activity for result, then delivers [AppShortcutLoader.CreatedShortcut] back to the caller.
 *
 * Must stay alive until the host activity returns a result — do not use [android.R.attr.noHistory].
 */
class CreateShortcutTrampolineActivity : ComponentActivity() {

    private var pendingHostPackage: String? = null
    private var resultDelivered = false

    private val createLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val hostPackage = pendingHostPackage
        pendingHostPackage = null
        val created = if (result.resultCode == RESULT_OK && hostPackage != null) {
            AppShortcutLoader.parseCreateShortcutResult(hostPackage, result.data)
        } else {
            null
        }
        finishWithResult(created)
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
        val hostPackage = intent.getStringExtra(EXTRA_HOST_PACKAGE)
        val hostClass = intent.getStringExtra(EXTRA_HOST_CLASS)
        if (hostPackage.isNullOrBlank() || hostClass.isNullOrBlank()) {
            finishWithResult(null)
            return
        }
        pendingHostPackage = hostPackage
        runCatching {
            createLauncher.launch(Intent().setClassName(hostPackage, hostClass))
        }.onFailure {
            pendingHostPackage = null
            finishWithResult(null)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_RESULT_DELIVERED, resultDelivered)
    }

    override fun onDestroy() {
        if (!resultDelivered && pendingHostPackage != null) {
            pendingHostPackage = null
            CreateShortcutTrampoline.deliver(null)
        }
        super.onDestroy()
    }

    private fun finishWithResult(created: AppShortcutLoader.CreatedShortcut?) {
        if (resultDelivered) return
        resultDelivered = true
        pendingHostPackage = null
        CreateShortcutTrampoline.deliver(created)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val EXTRA_HOST_PACKAGE = "host_package"
        private const val EXTRA_HOST_CLASS = "host_class"
        private const val STATE_RESULT_DELIVERED = "result_delivered"

        fun createIntent(context: Context, host: AppShortcutLoader.CreateShortcutHost): Intent =
            Intent(context, CreateShortcutTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_HOST_PACKAGE, host.packageName)
                putExtra(EXTRA_HOST_CLASS, host.className)
            }
    }
}

object CreateShortcutTrampoline {
    private var onPrepare: (() -> Unit)? = null
    private var onResult: ((AppShortcutLoader.CreatedShortcut?) -> Unit)? = null
    private var delivered = false

    fun launch(
        context: Context,
        host: AppShortcutLoader.CreateShortcutHost,
        onPrepare: () -> Unit,
        onResult: (AppShortcutLoader.CreatedShortcut?) -> Unit,
    ) {
        cancelPending()
        delivered = false
        this.onPrepare = onPrepare
        this.onResult = onResult
        onPrepare()
        runCatching {
            context.startActivity(CreateShortcutTrampolineActivity.createIntent(context, host))
        }.onFailure {
            deliver(null)
        }
    }

    internal fun deliver(created: AppShortcutLoader.CreatedShortcut?) {
        if (delivered) return
        delivered = true
        val listener = onResult
        onPrepare = null
        onResult = null
        listener?.invoke(created)
    }

    fun cancelPending() {
        delivered = true
        onPrepare = null
        onResult = null
    }
}
