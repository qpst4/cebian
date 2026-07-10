package com.slideindex.app.autofill

import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

object OtpAutoInputNodeHelper {
    data class Result(
        val success: Boolean,
        val strategy: String,
        val reason: String,
        val windowPackage: String = "",
    )

    fun performAutoInput(
        root: AccessibilityNodeInfo?,
        code: String,
        autoEnter: Boolean,
        inputIntervalMs: Long = 0L,
    ): Result {
        if (root == null) {
            return Result(false, "none", "no_active_window")
        }
        val windowPackage = root.packageName?.toString().orEmpty()
        findFocusedEditableNode(root)?.let { focused ->
            val result = setNodeText(focused, code, autoEnter, inputIntervalMs)
            releaseNode(focused)
            if (result.success) {
                return result.copy(strategy = "focused_node", windowPackage = windowPackage)
            }
        }
        val editableNodes = collectEditableNodes(root)
        if (editableNodes.isEmpty()) {
            return Result(false, "none", "no_editable_node", windowPackage)
        }
        try {
            val groupResult = fillEditableGroup(editableNodes, code, autoEnter, inputIntervalMs)
            if (groupResult.success) {
                return groupResult.copy(windowPackage = windowPackage)
            }
            val best = editableNodes.first()
            val singleResult = setNodeText(best, code, autoEnter, inputIntervalMs)
            if (singleResult.success) {
                return singleResult.copy(strategy = "best_editable_node", windowPackage = windowPackage)
            }
            return groupResult.copy(windowPackage = windowPackage)
        } finally {
            editableNodes.forEach { releaseNode(it) }
        }
    }

    private fun findFocusedEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val inputFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (inputFocus != null && canSetText(inputFocus)) return copyNode(inputFocus)
        val accessibilityFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (accessibilityFocus != null && canSetText(accessibilityFocus)) return copyNode(accessibilityFocus)
        return null
    }

    private fun collectEditableNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val found = mutableListOf<AccessibilityNodeInfo>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            val owned = node !== root
            try {
                if (canSetText(node)) {
                    found.add(copyNode(node))
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { stack.add(it) }
                }
            } finally {
                if (owned) releaseNode(node)
            }
        }
        val seen = HashSet<String>()
        return found
            .filter { seen.add(buildNodeIdentity(it)) }
            .sortedWith(
                compareByDescending<AccessibilityNodeInfo> { it.isFocused }
                    .thenBy { nodeTop(it) }
                    .thenBy { nodeLeft(it) },
            )
    }

    private fun fillEditableGroup(
        nodes: List<AccessibilityNodeInfo>,
        code: String,
        autoEnter: Boolean,
        inputIntervalMs: Long,
    ): Result {
        val length = code.length
        if (length !in 4..8) {
            return Result(false, "group_nodes", "group_strategy_not_applicable")
        }
        val singleCharNodes = nodes.filter { nodeTextLength(it) <= 1 }
        if (singleCharNodes.size < length) {
            return Result(false, "group_nodes", "not_enough_editable_nodes")
        }
        for (index in code.indices) {
            val char = code[index].toString()
            val enter = autoEnter && index == code.lastIndex
            val result = setNodeText(singleCharNodes[index], char, enter, inputIntervalMs)
            if (!result.success) {
                return Result(false, "group_nodes", "group_set_text_failed")
            }
        }
        return Result(true, "group_nodes", "ok")
    }

    private fun setNodeText(
        node: AccessibilityNodeInfo,
        text: String,
        autoEnter: Boolean,
        inputIntervalMs: Long,
    ): Result {
        return try {
            if (!node.isFocused) {
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            }
            node.refresh()
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                return Result(false, "set_text", "action_set_text_failed")
            }
            if (inputIntervalMs > 0) {
                Thread.sleep(inputIntervalMs.coerceAtMost(200L))
            }
            if (autoEnter) {
                tryImeEnter(node)
            }
            Result(true, "set_text", "ok")
        } catch (t: Throwable) {
            Result(false, "set_text", "set_text_exception")
        }
    }

    private fun tryImeEnter(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val actionId = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
        val supported = node.actionList.any { it.id == actionId }
        if (supported) {
            node.performAction(actionId)
        }
    }

    private fun canSetText(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser || !node.isEnabled) return false
        if (node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }) return true
        if (node.isEditable) return true
        return node.className?.toString().orEmpty().contains("EditText", ignoreCase = true)
    }

    private fun buildNodeIdentity(node: AccessibilityNodeInfo): String {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return listOf(
            node.viewIdResourceName.orEmpty(),
            node.className?.toString().orEmpty(),
            node.packageName?.toString().orEmpty(),
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
        ).joinToString("|")
    }

    private fun nodeTop(node: AccessibilityNodeInfo): Int {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return bounds.top
    }

    private fun nodeLeft(node: AccessibilityNodeInfo): Int {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return bounds.left
    }

    private fun nodeTextLength(node: AccessibilityNodeInfo): Int {
        val hintLength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.length
        } else {
            null
        }
        return node.text?.length ?: hintLength ?: 0
    }

    private fun copyNode(source: AccessibilityNodeInfo): AccessibilityNodeInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AccessibilityNodeInfo(source)
        } else {
            @Suppress("DEPRECATION")
            AccessibilityNodeInfo.obtain(source)
        }

    private fun releaseNode(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        @Suppress("DEPRECATION")
        node.recycle()
    }
}
