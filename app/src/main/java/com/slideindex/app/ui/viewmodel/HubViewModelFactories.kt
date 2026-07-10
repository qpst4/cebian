package com.slideindex.app.ui.viewmodel

import com.slideindex.app.ui.navigation.MainNavContext
import com.slideindex.app.util.SecureSettingsHelper

class MainNavHomeEffects(
    private val ctx: MainNavContext,
) : HomeScreenEffects {
    override fun refreshServiceState() = ctx.refreshServiceState()

    override fun requestNotificationPermission() = ctx.requestNotificationPermission()

    override fun requestShizuku() = ctx.requestShizuku()

    override fun openAccessibilitySettings() = ctx.openAccessibilitySettings()

    override fun previewHaptic(enabled: Boolean, strengthLevel: Int?) {
        ctx.previewHaptic(enabled, strengthLevel)
    }
}

class MainNavKeepAliveEffects(
    private val ctx: MainNavContext,
) : KeepAliveScreenEffects {
    override fun applyHideFromRecents(enabled: Boolean) {
        ctx.activity.applyHideFromRecents(enabled)
    }

    override fun onAccessibilityKeepAliveEnabled() {
        SecureSettingsHelper.ensureAccessibilityEnabled(ctx.activity)
        ctx.refreshPermissionState()
        ctx.refreshServiceState()
    }
}
