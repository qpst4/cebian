package com.slideindex.app.clipboardfloat

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.slideindex.app.clipboard.ClipboardBlockKind
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardImageLabel
import com.slideindex.app.clipboard.ClipboardImageStore
import com.slideindex.app.clipboard.ClipboardWriter
import com.slideindex.app.clipboard.hasImageContent
import com.slideindex.app.clipboard.isPureImageEntry
import com.slideindex.app.clipboard.resolvedContentBlocks
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
            ClipboardFloatEntryClickAction.PASTE -> {
                pasteIntoFocusedField(
                    service = service,
                    context = context,
                    entry = entry,
                    clipboardAlreadyPrepared = false,
                )
            }
            ClipboardFloatEntryClickAction.COPY_AND_PASTE -> {
                ClipboardWriter.write(context, entry)
                pasteIntoFocusedField(
                    service = service,
                    context = context,
                    entry = entry,
                    clipboardAlreadyPrepared = true,
                )
            }
        }
    }

    private fun pasteIntoFocusedField(
        service: AccessibilityService,
        context: Context,
        entry: ClipboardEntry,
        clipboardAlreadyPrepared: Boolean,
    ): PasteResult {
        val root = service.rootInActiveWindow ?: return PasteResult.Failure(PasteFailureReason.NO_ACTIVE_WINDOW)
        return try {
            val focused = findFocusedEditableNode(root) ?: return PasteResult.Failure(
                PasteFailureReason.NO_EDITABLE_FOCUS,
            )
            try {
                pasteIntoNode(
                    context = context,
                    node = focused,
                    entry = entry,
                    clipboardAlreadyPrepared = clipboardAlreadyPrepared,
                )
            } finally {
                recycleNode(focused)
            }
        } finally {
            recycleNode(root)
        }
    }

    private fun pasteIntoNode(
        context: Context,
        node: AccessibilityNodeInfo,
        entry: ClipboardEntry,
        clipboardAlreadyPrepared: Boolean,
    ): PasteResult {
        ensureNodeFocused(node)
        val entryText = resolveEntryPasteText(entry)

        if (!entry.hasImageContent()) {
            // Try ACTION_PASTE first for text entries to avoid hint text issues
            // (e.g. Telegram puts hint in node.text instead of hintText).
            if (supportsPaste(node)) {
                if (!clipboardAlreadyPrepared) {
                    ClipboardWriter.writeForPaste(context, entry)
                }
                if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                    return PasteResult.Success
                }
            }
            // Fall back to SET_TEXT with enhanced hint detection.
            if (entryText != null && supportsSetText(node)) {
                return insertViaSetText(node, entryText)
            }
            return PasteResult.Failure(PasteFailureReason.PASTE_AND_INSERT_FAILED)
        }

        if (!clipboardAlreadyPrepared) {
            try {
                ClipboardWriter.writeForPaste(context, entry)
            } catch (_: RuntimeException) {
                // TransactionTooLargeException fallback: try text-only paste.
                if (entryText != null && supportsSetText(node)) {
                    return insertViaSetText(node, entryText)
                }
                return PasteResult.Failure(PasteFailureReason.PASTE_AND_INSERT_FAILED)
            }
        }

        if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
            return PasteResult.Success
        }

        if (entryText != null && supportsSetText(node)) {
            return insertViaSetText(node, entryText)
        }
        return PasteResult.Failure(PasteFailureReason.PASTE_AND_INSERT_FAILED)
    }

    private fun insertViaSetText(
        node: AccessibilityNodeInfo,
        clipText: String,
    ): PasteResult {
        ensureNodeFocused(node)
        val hint = readHintText(node)
        val snapshot = ClipboardPasteTextLogic.snapshotEditableText(node.text, hint)
        val merged = if (snapshot.content.isEmpty() || isLikelyHintText(node, hint)) {
            clipText
        } else {
            val rawStart = readSelectionStart(node)
            val rawEnd = readSelectionEnd(node)
            val start = (rawStart - snapshot.leadingPlaceholderLength).coerceIn(0, snapshot.content.length)
            val end = (rawEnd - snapshot.leadingPlaceholderLength).coerceIn(start, snapshot.content.length)
            ClipboardPasteTextLogic.mergeClipAtSelection(
                currentText = snapshot.content,
                clipText = clipText,
                selectionStart = start,
                selectionEnd = end,
            )
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

    private fun resolveEntryPasteText(entry: ClipboardEntry): String? {
        if (entry.isPureImageEntry()) return null
        val imageSources = ClipboardImageStore.collectImageSourcesForEntry(entry)
        val blocks = ClipboardImageLabel.blocksForClipboardWrite(
            blocks = entry.resolvedContentBlocks(),
            imageSources = imageSources,
            uri = entry.uri,
        )
        val textBlocks = blocks.filter { it.kind == ClipboardBlockKind.TEXT }
        if (textBlocks.isNotEmpty()) {
            return textBlocks.joinToString("\n") { it.text.trim() }
                .trim()
                .takeIf { it.isNotEmpty() }
        }
        return entry.text.trim().takeIf { it.isNotEmpty() }
    }

    private fun readHintText(node: AccessibilityNodeInfo): CharSequence? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText
        } else {
            null
        }
    }

    private fun ensureNodeFocused(node: AccessibilityNodeInfo) {
        if (node.isFocused) return
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        node.refresh()
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
        return findBestEditableNode(root)
    }

    private fun findBestEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            val owned = node !== root
            try {
                if (canPaste(node)) {
                    candidates.add(copyNode(node))
                }
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let { stack.add(it) }
                }
            } finally {
                if (owned) recycleNode(node)
            }
        }
        return candidates.maxWithOrNull(
            compareByDescending<AccessibilityNodeInfo> { it.isFocused }
                .thenByDescending { supportsSetText(it) }
                .thenByDescending { it.className?.toString().orEmpty().contains("EditText", ignoreCase = true) },
        )
    }

    private fun canPaste(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser || !node.isEnabled) return false
        return node.isEditable || supportsSetText(node) || supportsPaste(node)
    }

    private fun supportsSetText(node: AccessibilityNodeInfo): Boolean {
        return node.actionList.any { action ->
            action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT.id
        }
    }

    private fun supportsPaste(node: AccessibilityNodeInfo): Boolean {
        return node.actionList.any { action ->
            action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_PASTE.id
        }
    }

    private fun copyNode(source: AccessibilityNodeInfo): AccessibilityNodeInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AccessibilityNodeInfo(source)
        } else {
            @Suppress("DEPRECATION")
            AccessibilityNodeInfo.obtain(source)
        }

    private fun recycleNode(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        @Suppress("DEPRECATION")
        node.recycle()
    }

    /**
     * Detect placeholder/hint text that some apps (e.g. Telegram) put in
     * [AccessibilityNodeInfo.getText] instead of [AccessibilityNodeInfo.getHintText].
     */
    private fun isLikelyHintText(node: AccessibilityNodeInfo, hintText: CharSequence?): Boolean {
        // If standard hintText was present, snapshotEditableText already handled it.
        if (!hintText.isNullOrEmpty()) return false
        val text = node.text?.toString() ?: return false
        if (text.isEmpty()) return false
        // Read raw selection positions without fallback.
        val rawStart = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            node.textSelectionStart
        } else {
            AccessibilityNodeInfoCompat.wrap(node).textSelectionStart
        }
        val rawEnd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            node.textSelectionEnd
        } else {
            AccessibilityNodeInfoCompat.wrap(node).textSelectionEnd
        }
        // No selection set (both -1): the "text" is likely a placeholder/hint.
        if (rawStart < 0 && rawEnd < 0) return true
        // Text matches contentDescription — another common hint pattern.
        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrEmpty() && desc == text) return true
        return false
    }
}
