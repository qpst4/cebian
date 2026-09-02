package com.slideindex.app.service

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import android.view.accessibility.AccessibilityEvent
import com.slideindex.app.overlay.EdgeOverlayHost
import com.slideindex.app.util.AccessibilityForegroundResolver

internal class SlideIndexAccessibilityForegroundTracker(
    private val service: SlideIndexAccessibilityService,
    private val overlayHost: () -> EdgeOverlayHost?,
    private val onMaybeOtp: () -> Unit,
    private val onSyncLockScreen: () -> Unit,
    /** 用户配置的“切换上一应用”黑名单包名。 */
    private val excludedPackageProvider: () -> Set<String>,
) {
    var prevPackageName: String? = null
        private set
    var currPackageName: String? = null
        private set
    var currClassName: String? = null
        private set

    /** 系统已启用输入法的包名；输入法窗口不应进入“上一应用”历史。 */
    private val imePackages: Set<String> by lazy { detectImePackages() }

    fun handleWindowStateChanged(event: AccessibilityEvent) {
        onSyncLockScreen()
        val selfPackage = service.applicationContext.packageName
        val resolvedPackage = AccessibilityForegroundResolver.resolveHostPackage(service)
        val eventPackage = event.packageName?.toString()?.takeIf { it.isNotBlank() }
        val packageName = resolvedPackage ?: eventPackage
        if (packageName.isNullOrBlank() || packageName == selfPackage) {
            overlayHost()?.refreshOverlaySuppression()
            return
        }
        val className = event.className?.toString()?.takeIf { it.isNotBlank() } ?: ""
        if (!isImePackageOrClass(packageName, className)) {
            currClassName = className
            com.slideindex.app.overlay.ForegroundActivityInspectorOverlayWindow.onForegroundWindowStateChanged(packageName, className)
        }

        overlayHost()?.updateForegroundPackage(packageName)
        when (val update = computeWindowStatePackageUpdate(
                packageName = packageName,
                selfPackageName = service.applicationContext.packageName,
                prevPackageName = prevPackageName,
                currPackageName = currPackageName,
                hasLaunchIntent = hasLaunchIntent(packageName),
                excludedPackages = excludedPackages(),
            )) {
            null,
            is WindowStatePackageUpdate.ForegroundOnly,
            -> return
            WindowStatePackageUpdate.SamePackage -> onMaybeOtp()
            is WindowStatePackageUpdate.Tracked -> {
                prevPackageName = update.prevPackageName
                currPackageName = update.currPackageName
                onMaybeOtp()
            }
        }
    }

    fun handleWindowsChanged() {
        onSyncLockScreen()
        val resolvedPackage = AccessibilityForegroundResolver.resolveHostPackage(service)
        if (resolvedPackage != null) {
            overlayHost()?.updateForegroundPackage(resolvedPackage)
        } else {
            overlayHost()?.refreshOverlaySuppression()
        }
        if (com.slideindex.app.overlay.ForegroundActivityInspectorOverlayWindow.isShowing) {
            val rootNode = service.rootInActiveWindow
            val activePkg = rootNode?.packageName?.toString()
            val activeCls = rootNode?.className?.toString()
            if (!activePkg.isNullOrBlank() && activePkg != service.applicationContext.packageName && !isImePackageOrClass(activePkg, activeCls.orEmpty())) {
                currClassName = activeCls.orEmpty()
                com.slideindex.app.overlay.ForegroundActivityInspectorOverlayWindow.onForegroundWindowStateChanged(activePkg, activeCls.orEmpty())
            }
        }
    }

    fun launchPreviousApp(): Boolean {
        val plan = computeLaunchPreviousAppPlan(
            prevPackageName = prevPackageName,
            currPackageName = currPackageName,
            activePackageName = service.rootInActiveWindow?.packageName?.toString(),
            excludedPackages = excludedPackages(),
        ) ?: return false
        return when (plan) {
            is LaunchPreviousAppPlan.LaunchCurrent -> launchPackage(plan.packageName)
            is LaunchPreviousAppPlan.SwapToPrevious -> {
                if (launchPackage(plan.targetPackage)) {
                    prevPackageName = plan.newPrevPackageName
                    currPackageName = plan.newCurrPackageName
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun launchPackage(packageName: String): Boolean {
        val intent = service.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            service.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun hasLaunchIntent(packageName: String): Boolean =
        service.packageManager.getLaunchIntentForPackage(packageName) != null

    private fun excludedPackages(): Set<String> {
        val userExcluded = excludedPackageProvider()
        if (userExcluded.isEmpty()) return imePackages
        return userExcluded + imePackages
    }

    private fun detectImePackages(): Set<String> {
        val inputMethodManager = runCatching {
            service.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        }.getOrNull() ?: return emptySet()
        return runCatching {
            inputMethodManager.enabledInputMethodList.map { it.packageName }.toSet()
        }.getOrDefault(emptySet())
    }

    private fun isImePackageOrClass(packageName: String, className: String): Boolean {
        if (packageName.isBlank()) return false
        if (packageName in imePackages) return true
        if (className.contains("inputmethod", ignoreCase = true) ||
            className.contains("InputMethodService", ignoreCase = true) ||
            className.startsWith("android.inputmethodservice.") ||
            className == "android.widget.PopupWindow" ||
            className == "android.widget.Toast"
        ) {
            return true
        }
        val imm = runCatching { service.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager }.getOrNull()
        val dynamicImes = runCatching { imm?.enabledInputMethodList?.map { it.packageName }?.toSet() }.getOrNull()
        if (dynamicImes?.contains(packageName) == true) {
            return true
        }
        return false
    }
}

internal sealed interface WindowStatePackageUpdate {
    data class ForegroundOnly(val packageName: String) : WindowStatePackageUpdate
    data object SamePackage : WindowStatePackageUpdate
    data class Tracked(
        val prevPackageName: String?,
        val currPackageName: String?,
        val packageName: String,
    ) : WindowStatePackageUpdate
}

internal fun computeWindowStatePackageUpdate(
    packageName: String,
    selfPackageName: String,
    prevPackageName: String?,
    currPackageName: String?,
    hasLaunchIntent: Boolean,
    excludedPackages: Set<String> = emptySet(),
): WindowStatePackageUpdate? {
    if (packageName.isBlank() || packageName == selfPackageName) return null
    if (packageName in excludedPackages) return null
    if (!hasLaunchIntent) {
        return WindowStatePackageUpdate.ForegroundOnly(packageName)
    }
    if (currPackageName == packageName) {
        return WindowStatePackageUpdate.SamePackage
    }
    var newPrev = currPackageName
    val newCurr = packageName
    if (newPrev == null) {
        newPrev = newCurr
    }
    return WindowStatePackageUpdate.Tracked(
        prevPackageName = newPrev,
        currPackageName = newCurr,
        packageName = packageName,
    )
}

internal sealed interface LaunchPreviousAppPlan {
    data class LaunchCurrent(val packageName: String) : LaunchPreviousAppPlan
    data class SwapToPrevious(
        val targetPackage: String,
        val newPrevPackageName: String?,
        val newCurrPackageName: String?,
    ) : LaunchPreviousAppPlan
}

internal fun computeLaunchPreviousAppPlan(
    prevPackageName: String?,
    currPackageName: String?,
    activePackageName: String?,
    excludedPackages: Set<String> = emptySet(),
): LaunchPreviousAppPlan? {
    val prevPkgName = prevPackageName?.takeIf { it !in excludedPackages }
    val curPkgName = currPackageName?.takeIf { it !in excludedPackages }
    if (prevPkgName.isNullOrEmpty() || curPkgName.isNullOrEmpty()) return null
    if (activePackageName != curPkgName) {
        return LaunchPreviousAppPlan.LaunchCurrent(curPkgName)
    }
    if (prevPkgName == curPkgName) return null
    return LaunchPreviousAppPlan.SwapToPrevious(
        targetPackage = prevPkgName,
        newPrevPackageName = curPkgName,
        newCurrPackageName = prevPkgName,
    )
}
