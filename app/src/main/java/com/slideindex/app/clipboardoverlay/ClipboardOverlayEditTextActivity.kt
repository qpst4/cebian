package com.slideindex.app.clipboardoverlay

import android.os.Bundle
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardWriter

class ClipboardOverlayEditTextActivity : AppCompatActivity() {

    private lateinit var editText: EditText
    private var original: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.clipboard_overlay_edit_text)
        original = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        editText = findViewById(R.id.clipboard_overlay_edit_text)
        editText.setText(original)
        editText.setSelection(editText.text.length)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    saveIfChanged()
                    finish()
                }
            },
        )
    }

    override fun onPause() {
        saveIfChanged()
        super.onPause()
    }

    private fun saveIfChanged() {
        val text = editText.text?.toString() ?: return
        if (text == original) return
        ClipboardWriter.write(
            this,
            ClipboardEntry(
                id = "overlay-edit",
                text = text,
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
        original = text
    }

    companion object {
        const val EXTRA_TEXT = "clipboard_overlay_edit_text"
    }
}
