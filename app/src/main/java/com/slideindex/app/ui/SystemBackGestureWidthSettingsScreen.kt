package com.slideindex.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixSettingsTipCard
import com.slideindex.app.ui.miuix.MiuixSliderRow
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingLinkRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.util.SecureSettingsHelper
import com.slideindex.app.util.SystemBackGestureConflictHelper
import com.slideindex.app.util.SystemBackGestureInsetHelper
import com.slideindex.app.xposed.bridge.ModuleBridgeStatusProbe
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SystemBackGestureWidthSettingsScreen(
    takeoverTop: Boolean,
    takeoverSides: Boolean,
    takeoverBottom: Boolean,
    onTakeoverTopChange: (Boolean) -> Unit,
    onTakeoverSidesChange: (Boolean) -> Unit,
    onTakeoverBottomChange: (Boolean) -> Unit,
    writeSecureSettingsGranted: Boolean,
    privilegedAccessGranted: Boolean,
    onBack: () -> Unit,
    onRequestSecureSettingsGrant: () -> Boolean,
    onRefreshPermissions: () -> Unit = {},
) {
    val context = LocalContext.current
    val gestureNavigation = remember(context) { SystemBackGestureConflictHelper.isGestureNavigation(context) }
    var leftScale by remember { mutableFloatStateOf(SystemBackGestureInsetHelper.DEFAULT_SCALE) }
    var rightScale by remember { mutableFloatStateOf(SystemBackGestureInsetHelper.DEFAULT_SCALE) }
    var showAdbDialog by remember { mutableStateOf(false) }
    var moduleState by remember { mutableStateOf<ModuleBridgeStatusProbe.Status?>(null) }
    var moduleDetail by remember { mutableStateOf<String?>(null) }
    var moduleChecking by remember { mutableStateOf(false) }
    val adbCommand = remember { SecureSettingsHelper.adbGrantCommand(context) }
    val copiedMessage = stringResource(R.string.secure_settings_adb_copied)
    val canWrite = writeSecureSettingsGranted || privilegedAccessGranted

    LaunchedEffect(Unit) {
        leftScale = SystemBackGestureInsetHelper.readLeftScale(context)
        rightScale = SystemBackGestureInsetHelper.readRightScale(context)
    }

    fun runModuleProbe() {
        if (moduleChecking) return
        moduleChecking = true
        ModuleBridgeStatusProbe.probe(context.applicationContext) { status, detail ->
            moduleChecking = false
            moduleState = status
            moduleDetail = detail
        }
    }

    LaunchedEffect(Unit) {
        runModuleProbe()
    }

    fun refreshScales() {
        leftScale = SystemBackGestureInsetHelper.readLeftScale(context)
        rightScale = SystemBackGestureInsetHelper.readRightScale(context)
    }

    fun writeScale(side: BackGestureSide, value: Float): Boolean {
        val success = when (side) {
            BackGestureSide.LEFT -> SystemBackGestureInsetHelper.writeLeftScale(context, value)
            BackGestureSide.RIGHT -> SystemBackGestureInsetHelper.writeRightScale(context, value)
        }
        if (success) {
            refreshScales()
        }
        return success
    }

    fun onScaleChange(side: BackGestureSide, value: Float) {
        if (!gestureNavigation || !canWrite) return
        val snapped = (value * 20f).roundToInt() / 20f
        when (side) {
            BackGestureSide.LEFT -> leftScale = snapped
            BackGestureSide.RIGHT -> rightScale = snapped
        }
        if (!writeScale(side, snapped)) {
            showAdbDialog = true
        }
    }

    fun requestWriteAccess(): Boolean {
        if (writeSecureSettingsGranted) return true
        if (privilegedAccessGranted) {
            val granted = onRequestSecureSettingsGrant()
            onRefreshPermissions()
            if (granted) return true
        }
        showAdbDialog = true
        return false
    }

    val backGestureSectionTitle = stringResource(R.string.system_back_gesture_width_section)
    val takeoverSectionTitle = stringResource(R.string.system_gesture_takeover_section)

    SettingsScreenScaffold(
        title = stringResource(R.string.system_back_gesture_width_title),
        subtitle = stringResource(R.string.system_back_gesture_width_desc),
        onBack = onBack,
    ) {
        // 段一：系统返回手势宽度（本页原有功能，放在最上面）
        settingsLazySmallTitle(
            key = "system-back-gesture-section",
            title = backGestureSectionTitle,
        )
        LazySettingsItem(key = "system-back-gesture-tip") {
            MiuixSettingsTipCard(
                text = stringResource(R.string.system_back_gesture_width_experimental_tip),
            )
        }
        groupedCardItems(
            keyPrefix = "system-back-gesture-width",
            items = buildList {
                if (!gestureNavigation) {
                    add(
                        settingsCardScopeItem("not-gesture-nav") {
                            MiuixHintText(stringResource(R.string.system_back_gesture_width_not_gesture_nav))
                        },
                    )
                } else if (!canWrite) {
                    add(
                        settingsCardScopeItem("write-permission") {
                            SettingLinkRow(
                                title = stringResource(R.string.secure_settings_title),
                                subtitle = stringResource(R.string.system_back_gesture_width_write_permission_desc),
                                onClick = { requestWriteAccess() },
                            )
                        },
                    )
                }
                add(
                    settingsCardScopeItem("left-scale") {
                        MiuixSliderRow(
                            title = stringResource(R.string.system_back_gesture_width_left),
                            value = leftScale,
                            valueRange = SystemBackGestureInsetHelper.MIN_SCALE..SystemBackGestureInsetHelper.MAX_SCALE,
                            steps = 20,
                            enabled = gestureNavigation && canWrite,
                            formatLabel = { "${(it * 100).roundToInt()}%" },
                            onValueChange = { onScaleChange(BackGestureSide.LEFT, it) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("right-scale") {
                        MiuixSliderRow(
                            title = stringResource(R.string.system_back_gesture_width_right),
                            value = rightScale,
                            valueRange = SystemBackGestureInsetHelper.MIN_SCALE..SystemBackGestureInsetHelper.MAX_SCALE,
                            steps = 20,
                            enabled = gestureNavigation && canWrite,
                            formatLabel = { "${(it * 100).roundToInt()}%" },
                            onValueChange = { onScaleChange(BackGestureSide.RIGHT, it) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("reset-defaults") {
                        SettingLinkRow(
                            title = stringResource(R.string.system_back_gesture_width_reset),
                            subtitle = stringResource(R.string.system_back_gesture_width_reset_desc),
                            enabled = gestureNavigation && canWrite,
                            onClick = {
                                if (!gestureNavigation) return@SettingLinkRow
                                if (!canWrite && !requestWriteAccess()) return@SettingLinkRow
                                if (SystemBackGestureInsetHelper.resetDefaults(context)) {
                                    refreshScales()
                                } else {
                                    showAdbDialog = true
                                }
                            },
                        )
                    },
                )
            },
        )
        // 段二：LSPosed 接管范围
        settingsLazySmallTitle(
            key = "system-gesture-takeover-section",
            title = takeoverSectionTitle,
        )
        LazySettingsItem(key = "system-gesture-takeover-hint") {
            MiuixSettingsTipCard(
                text = stringResource(R.string.system_gesture_takeover_requires_module),
            )
        }
        groupedCardItems(
            keyPrefix = "system-gesture-takeover",
            items = buildList {
                add(
                    settingsCardScopeItem("takeover-top") {
                        SettingSwitchRow(
                            title = stringResource(R.string.system_gesture_takeover_top),
                            subtitle = stringResource(R.string.system_gesture_takeover_top_desc),
                            checked = takeoverTop,
                            enabled = true,
                            onCheckedChange = onTakeoverTopChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("takeover-sides") {
                        SettingSwitchRow(
                            title = stringResource(R.string.system_gesture_takeover_sides),
                            subtitle = stringResource(R.string.system_gesture_takeover_sides_desc),
                            checked = takeoverSides,
                            enabled = true,
                            onCheckedChange = onTakeoverSidesChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("takeover-bottom") {
                        val bottomSubtitle = if (gestureNavigation) {
                            stringResource(R.string.system_gesture_takeover_bottom_desc)
                        } else {
                            stringResource(R.string.system_gesture_takeover_bottom_desc) + "\n" +
                                stringResource(R.string.system_gesture_takeover_bottom_three_button_nav)
                        }
                        SettingSwitchRow(
                            title = stringResource(R.string.system_gesture_takeover_bottom),
                            subtitle = bottomSubtitle,
                            checked = takeoverBottom,
                            enabled = true,
                            onCheckedChange = onTakeoverBottomChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("module-status") {
                        SettingLinkRow(
                            title = stringResource(R.string.system_gesture_takeover_module_status),
                            subtitle = when {
                                moduleChecking -> stringResource(R.string.system_gesture_takeover_module_checking)
                                moduleState == ModuleBridgeStatusProbe.Status.Ready ->
                                    stringResource(R.string.system_gesture_takeover_module_ready)
                                moduleState == ModuleBridgeStatusProbe.Status.Armed ->
                                    stringResource(R.string.system_gesture_takeover_module_armed) +
                                        moduleDetailSuffix(moduleDetail)
                                moduleState == ModuleBridgeStatusProbe.Status.SwitchedOff ->
                                    stringResource(R.string.system_gesture_takeover_module_switched_off) +
                                        moduleDetailSuffix(moduleDetail)
                                moduleState == ModuleBridgeStatusProbe.Status.NotReady ->
                                    stringResource(R.string.system_gesture_takeover_module_not_ready) +
                                        moduleDetailSuffix(moduleDetail)
                                else -> stringResource(R.string.system_gesture_takeover_module_tap_hint)
                            },
                            onClick = { runModuleProbe() },
                        )
                    },
                )
            },
        )
        LazySettingsItem(key = "system-gesture-takeover-tip") {
            MiuixSettingsTipCard(
                text = stringResource(R.string.system_gesture_takeover_tip),
            )
        }
    }

    MiuixConfirmDialog(
        show = showAdbDialog,
        onDismissRequest = { showAdbDialog = false },
        title = stringResource(R.string.secure_settings_adb_dialog_title),
        message = stringResource(R.string.secure_settings_adb_dialog_message, adbCommand),
        confirmText = stringResource(R.string.secure_settings_adb_copy),
        dismissOnConfirm = false,
        onConfirm = {
            copyToClipboard(context, adbCommand)
            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        },
        dismissText = stringResource(R.string.confirm),
    )
}

@Composable
fun SettingsCardScope.SystemBackGestureWidthEntryCard(
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label ->
            Icon(
                HomeLeadingIcons.systemBackGestureWidth(outlinedLeadingIcons),
                contentDescription = label,
            )
        },
        title = stringResource(R.string.system_back_gesture_width_entry_title),
        subtitle = stringResource(R.string.system_back_gesture_width_entry_desc),
        onClick = onClick,
    )
}

private enum class BackGestureSide {
    LEFT,
    RIGHT,
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("adb_command", text))
}

/** 未生效时把模块回传的步骤状态一并显示，便于现场定位（如 filter=fail）。 */
private fun moduleDetailSuffix(detail: String?): String {
    if (detail.isNullOrBlank() || detail == ModuleBridgeStatusProbe.NO_RESPONSE) return ""
    return "\n$detail"
}
