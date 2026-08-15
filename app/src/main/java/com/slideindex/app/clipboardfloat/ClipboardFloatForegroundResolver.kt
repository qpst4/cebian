package com.slideindex.app.clipboardfloat

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import com.slideindex.app.service.OverlayService
import com.slideindex.app.service.SlideIndexAccessibilityService

internal object ClipboardFloatForegroundResolver {

    fun resolveHostPackage(service: AccessibilityService): String? {
        val selfPackage = service.packageName
        resolveFromActiveRoot(service, selfPackage)?.let { return it }
        resolveFromApplicationWindows(service, selfPackage, requireFocused = true)?.let { return it }
        resolveFromApplicationWindows(service, selfPackage, requireFocused = false)?.let { return it }
        val tracked = OverlayService.foregroundPackage
            ?: SlideIndexAccessibilityService.currentForegroundPackage()
        return tracked?.takeIf { isUsableHostPackage(service, it, selfPackage) }
    }

    private fun resolveFromActiveRoot(service: AccessibilityService, selfPackage: String): String? {
        val root = service.rootInActiveWindow ?: return null
        return try {
            root.packageName?.toString()
                ?.takeIf { isUsableHostPackage(service, it, selfPackage) }
        } finally {
            recycleNode(root)
        }
    }

    private fun resolveFromApplicationWindows(
        service: AccessibilityService,
        selfPackage: String,
        requireFocused: Boolean,
    ): String? {
        for (window in service.windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            if (requireFocused && !window.isFocused && !window.isActive) continue
            val root = window.root ?: continue
            val packageName = try {
                root.packageName?.toString()
                    ?.takeIf { isUsableHostPackage(service, it, selfPackage) }
            } finally {
                recycleNode(root)
            }
            if (packageName != null) return packageName
        }
        return null
    }

    private fun isUsableHostPackage(
        service: AccessibilityService,
        packageName: String,
        selfPackage: String,
    ): Boolean {
        if (packageName.isBlank() || packageName == selfPackage) return false
        return !isInputMethodPackage(service, packageName)
    }

    private fun isInputMethodPackage(service: AccessibilityService, packageName: String): Boolean {
        if (packageName.contains("inputmethod", ignoreCase = true)) return true
        val imm = service.getSystemService(InputMethodManager::class.java) ?: return false
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    private fun recycleNode(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        @Suppress("DEPRECATION")
        node.recycle()
    }
}
