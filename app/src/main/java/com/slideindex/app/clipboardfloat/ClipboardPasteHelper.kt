package com.slideindex.app.clipboardfloat

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.slideindex.app.autofill.OtpAutoInputNodeHelper
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardWriter
import com.slideindex.app.settings.ClipboardFloatEntryClickAction

object ClipboardPasteHelper {

    fun performEntryAction(
        service: AccessibilityService,
        context: android.content.Context,
        entry: ClipboardEntry,
        action: ClipboardFloatEntryClickAction,
    ): Boolean {
        return when (action) {
            ClipboardFloatEntryClickAction.COPY -> {
                ClipboardWriter.write(context, entry)
                true
            }
            ClipboardFloatEntryClickAction.PASTE -> pasteEntry(service, context, entry)
            ClipboardFloatEntryClickAction.COPY_AND_PASTE -> {
                ClipboardWriter.write(context, entry)
                pasteViaClipboardAction(service)
            }
        }
    }

    private fun pasteEntry(
        service: AccessibilityService,
        context: android.content.Context,
        entry: ClipboardEntry,
    ): Boolean {
        val plainText = entry.text.trim()
        if (plainText.isNotEmpty() && pasteViaFocusedNode(service, plainText)) {
            return true
        }
        ClipboardWriter.write(context, entry)
        return pasteViaClipboardAction(service)
    }

    private fun pasteViaFocusedNode(service: AccessibilityService, text: String): Boolean {
        if (text.isEmpty()) return false
        val root = service.rootInActiveWindow ?: return false
        return try {
            OtpAutoInputNodeHelper.performAutoInput(
                root = root,
                code = text,
                autoEnter = false,
                inputIntervalMs = 0L,
            ).success
        } finally {
            recycleNode(root)
        }
    }

    private fun pasteViaClipboardAction(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        return try {
            val focused = findFocusedEditableNode(root) ?: return false
            try {
                focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            } finally {
                recycleNode(focused)
            }
        } finally {
            recycleNode(root)
        }
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
