package com.slideindex.app.util

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import com.slideindex.app.service.OverlayService
import com.slideindex.app.service.SlideIndexAccessibilityService

/**
 * 从无障碍窗口树解析当前前台宿主包名；比单条 WINDOW_STATE_CHANGED 更可靠（游戏/全屏视频）。
 */
object AccessibilityForegroundResolver {
    fun resolve(context: Context): String? {
        val service = context as? AccessibilityService
            ?: SlideIndexAccessibilityService.accessibilityInstance()
        return service?.let(::resolveHostPackage) ?: OverlayService.foregroundPackage
    }

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
