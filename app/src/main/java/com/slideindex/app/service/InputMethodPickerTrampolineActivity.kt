package com.slideindex.app.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager

/**
 * Fallback when a focusable accessibility overlay cannot be attached.
 * Must wait for [onWindowFocusChanged] — calling IMM from [onResume] is ignored on Flyme
 * (`Ignoring showInputMethodPickerFromClient`) even before/around Displayed.
 */
class InputMethodPickerTrampolineActivity : Activity() {

    private var pickerRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            maybeShowPicker()
            return
        }
        // Picker (or another window) took focus after we requested it — exit.
        if (pickerRequested) {
            window.decorView.postDelayed({ finishIfNeeded() }, FINISH_AFTER_FOCUS_LOSS_MS)
        }
    }

    private fun maybeShowPicker() {
        if (pickerRequested || isFinishing) return
        window.decorView.postDelayed({
            if (pickerRequested || isFinishing || !hasWindowFocus()) return@postDelayed
            pickerRequested = true
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
            Log.i(TAG, "IME picker requested after window focus")
            window.decorView.postDelayed({ finishIfNeeded() }, KEEP_ALIVE_MS)
        }, SHOW_DELAY_MS)
    }

    private fun finishIfNeeded() {
        if (isFinishing) return
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val TAG = "InputMethodPickerTrampoline"
        private const val SHOW_DELAY_MS = 300L
        private const val KEEP_ALIVE_MS = 1_200L
        private const val FINISH_AFTER_FOCUS_LOSS_MS = 200L

        fun createIntent(context: Context): Intent =
            Intent(context, InputMethodPickerTrampolineActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
            }
    }
}
