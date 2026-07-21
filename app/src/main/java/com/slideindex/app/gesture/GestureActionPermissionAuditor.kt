package com.slideindex.app.gesture

import android.content.Context
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatingPointerEdgeSide
import com.slideindex.app.ui.gesturepicker.gestureActionDescriptionText
import com.slideindex.app.ui.gesturepicker.gestureActionLabelText
import com.slideindex.app.ui.gesturepicker.gestureActionPermissionHintText
import com.slideindex.app.ui.gesturepicker.isGestureActionEnabledOnDevice
import com.slideindex.app.ui.gesturepicker.requestPermissionForAdjustAction

data class MissingGesturePermission(
    val action: GestureAction,
    val actionLabel: String,
    val actionDescription: String?,
    val permissionHint: String,
)

object GestureActionPermissionAuditor {
    fun collectConfiguredActions(settings: AppSettings): List<GestureAction> {
        val actions = linkedSetOf<GestureAction>()
        fun add(action: GestureAction) {
            if (action.type != GestureActionType.NONE) {
                actions += action
            }
        }

        settings.gestureRules.filter { it.enabled }.forEach { add(it.action) }
        settings.floatBallGestureActions.values.forEach(::add)

        val shake = settings.shakeGestureSettings
        if (shake.enabled) {
            shake.basicActions.values.forEach(::add)
            if (shake.lockScreenShakeEnabled) {
                shake.lockScreenActions.values.forEach(::add)
            }
            if (shake.independentAppShakeEnabled) {
                shake.perAppActions.values.forEach { perApp ->
                    perApp.values.forEach(::add)
                }
            }
        }

        if (settings.faceDownGestureSettings.enabled) {
            add(settings.faceDownGestureSettings.action)
        }

        add(settings.floatingPointerJoystickLongPressAction)
        settings.floatingPointerRadialSlotActions.forEach(::add)
        val edgeConfig = settings.floatingPointerEdgeActionsConfig
        FloatingPointerEdgeSide.entries.forEach { side ->
            val bar = edgeConfig.bar(side)
            if (bar.enabled) {
                bar.layoutSlots().forEach { slot -> add(slot.action) }
            }
        }

        settings.quickLauncher
            .filter { it.type == QuickLauncherItemType.ACTION }
            .forEach { item ->
                QuickLauncherItemCodec.parseActionPayload(item.payload)?.let(::add)
            }

        return actions.toList()
    }

    fun auditMissingPermissions(context: Context, settings: AppSettings): List<MissingGesturePermission> {
        return collectConfiguredActions(settings)
            .filter { isGestureActionEnabledOnDevice(it) }
            .mapNotNull { action ->
                val hint = gestureActionPermissionHintText(context, action) ?: return@mapNotNull null
                MissingGesturePermission(
                    action = action,
                    actionLabel = gestureActionLabelText(context, action),
                    actionDescription = gestureActionDescriptionText(context, action),
                    permissionHint = hint,
                )
            }
            .distinctBy { it.permissionHint to it.actionLabel }
    }

    fun requestPermission(context: Context, action: GestureAction) {
        requestPermissionForAdjustAction(context, action)
    }
}
