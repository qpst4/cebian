package com.slideindex.app.service

import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.slideindex.app.overlay.EdgeOverlayHost

internal class SlideIndexAccessibilityForegroundTracker(
    private val service: SlideIndexAccessibilityService,
    private val overlayHost: () -> EdgeOverlayHost?,
    private val onMaybeOtp: () -> Unit,
    private val onSyncLockScreen: () -> Unit,
) {
    var prevPackageName: String? = null
        private set
    var currPackageName: String? = null
        private set

    fun handleWindowStateChanged(event: AccessibilityEvent) {
        onSyncLockScreen()
        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: run {
            overlayHost()?.refreshTriggerVisibility()
            return
        }
        if (packageName == service.applicationContext.packageName) {
            overlayHost()?.refreshTriggerVisibility()
            return
        }
        overlayHost()?.updateForegroundPackage(packageName)
        when (val update = computeWindowStatePackageUpdate(
                packageName = packageName,
                selfPackageName = service.applicationContext.packageName,
                prevPackageName = prevPackageName,
                currPackageName = currPackageName,
                hasLaunchIntent = hasLaunchIntent(packageName),
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
        overlayHost()?.refreshTriggerVisibility()
    }

    fun launchPreviousApp(): Boolean {
        val plan = computeLaunchPreviousAppPlan(
            prevPackageName = prevPackageName,
            currPackageName = currPackageName,
            activePackageName = service.rootInActiveWindow?.packageName?.toString(),
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
): WindowStatePackageUpdate? {
    if (packageName.isBlank() || packageName == selfPackageName) return null
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
): LaunchPreviousAppPlan? {
    val prevPkgName = prevPackageName
    val curPkgName = currPackageName
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
