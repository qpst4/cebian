package com.slideindex.app.clipboardfloat

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardWriter
import com.slideindex.app.settings.ClipboardFloatEntryClickAction

enum class PasteFailureReason {
    NO_ACTIVE_WINDOW,
    NO_EDITABLE_FOCUS,
    PASTE_AND_INSERT_FAILED,
}

sealed class PasteResult {
    data object Success : PasteResult()
    data class Failure(val reason: PasteFailureReason) : PasteResult()
}

object ClipboardPasteHelper {

    fun performEntryAction(
        service: AccessibilityService,
        context: Context,
        entry: ClipboardEntry,
        action: ClipboardFloatEntryClickAction,
    ): PasteResult {
        return when (action) {
            ClipboardFloatEntryClickAction.COPY -> {
                ClipboardWriter.write(context, entry)
                PasteResult.Success
            }
            ClipboardFloatEntryClickAction.PASTE,
            ClipboardFloatEntryClickAction.COPY_AND_PASTE,
            -> {
                ClipboardWriter.write(context, entry)
                pasteIntoFocusedField(service, context)
            }
        }
    }

    private fun pasteIntoFocusedField(
        service: AccessibilityService,
        context: Context,
    ): PasteResult {
        val root = service.rootInActiveWindow ?: return PasteResult.Failure(PasteFailureReason.NO_ACTIVE_WINDOW)
        return try {
            val focused = findFocusedEditableNode(root) ?: return PasteResult.Failure(
                PasteFailureReason.NO_EDITABLE_FOCUS,
            )
            try {
                if (focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                    PasteResult.Success
                } else {
                    insertViaSetText(context, focused)
                }
            } finally {
                recycleNode(focused)
            }
        } finally {
            recycleNode(root)
        }
    }

    private fun insertViaSetText(
        context: Context,
        node: AccessibilityNodeInfo,
    ): PasteResult {
        val clipText = readPrimaryClipText(context) ?: return PasteResult.Failure(
            PasteFailureReason.PASTE_AND_INSERT_FAILED,
        )
        if (!node.isFocused) {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            node.refresh()
        }
        val currentText = node.text?.toString().orEmpty()
        val selectionStart = readSelectionStart(node).coerceIn(0, currentText.length)
        val selectionEnd = readSelectionEnd(node).coerceIn(selectionStart, currentText.length)
        val merged = buildString {
            append(currentText.substring(0, selectionStart))
            append(clipText)
            append(currentText.substring(selectionEnd))
        }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, merged)
        }
        return if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            PasteResult.Success
        } else {
            PasteResult.Failure(PasteFailureReason.PASTE_AND_INSERT_FAILED)
        }
    }

    private fun readPrimaryClipText(context: Context): String? {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        val description = clip.description
        if (description.mimeTypeCount > 0 && description.getMimeType(0).startsWith("image/")) {
            return null
        }
        return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotEmpty() }
    }

    private fun readSelectionStart(node: AccessibilityNodeInfo): Int {
        val textLength = node.text?.length ?: 0
        val start = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            node.textSelectionStart
        } else {
            AccessibilityNodeInfoCompat.wrap(node).textSelectionStart
        }
        return if (start >= 0) start else textLength
    }

    private fun readSelectionEnd(node: AccessibilityNodeInfo): Int {
        val textLength = node.text?.length ?: 0
        val end = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            node.textSelectionEnd
        } else {
            AccessibilityNodeInfoCompat.wrap(node).textSelectionEnd
        }
        return if (end >= 0) end else textLength
    }

    private fun findFocusedEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val inputFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (inputFocus != null && canPaste(inputFocus)) return inputFocus
        recycleNode(inputFocus)
        val accessibilityFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (accessibilityFocus != null && canPaste(accessibilityFocus)) return accessibilityFocus
        recycleNode(accessibilityFocus)
        return null
    }

    private fun canPaste(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser || !node.isEnabled) return false
        return node.isEditable ||
            node.actionList.any { action ->
                action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_PASTE.id ||
                    action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT.id
            }
    }

    private fun recycleNode(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        @Suppress("DEPRECATION")
        node.recycle()
    }
}
