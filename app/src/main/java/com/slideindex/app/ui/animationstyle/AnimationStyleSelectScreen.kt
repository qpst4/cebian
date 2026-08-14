@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.animationstyle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import com.slideindex.app.ui.HomeLeadingIcons
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AnimationStyleLimits
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.GestureHintStyle
import com.slideindex.app.settings.HomeMainSettings
import com.slideindex.app.settings.gestureHintStyle
import com.slideindex.app.ui.SettingSwitchNavigationRow
import com.slideindex.app.ui.SettingsScreenScaffold
import com.slideindex.app.ui.SettingsSliderRow
import com.slideindex.app.ui.gestureHintStyleLabel
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsHintText
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt

@Composable
fun SettingsCardScope.GestureAnimationSettingsRows(
    settings: HomeMainSettings,
    enabled: Boolean,
    outlinedLeadingIcons: Boolean = false,
    onGestureHintEnabledChange: (Boolean) -> Unit,
    onOpenAnimationStyleSelect: () -> Unit,
) {
    SettingSwitchNavigationRow(
        title = stringResource(R.string.gesture_animation_title),
        subtitle = gestureHintStyleLabel(settings.gestureHintStyle()),
        icon = { label ->
            Icon(HomeLeadingIcons.gestureAnimation(outlinedLeadingIcons), contentDescription = label)
        },
        checked = settings.gestureHintEnabled,
        enabled = enabled,
        onCheckedChange = onGestureHintEnabledChange,
        onNavigate = onOpenAnimationStyleSelect,
    )
}

@Composable
fun SettingsCardScope.GestureAnimationSettingsRows(
    settings: AppSettings,
    enabled: Boolean,
    onGestureHintEnabledChange: (Boolean) -> Unit,
    onOpenAnimationStyleSelect: () -> Unit,
) {
    SettingSwitchNavigationRow(
        title = stringResource(R.string.gesture_animation_title),
        subtitle = gestureHintStyleLabel(settings.gestureHintStyle()),
        icon = { label ->
            Icon(HomeLeadingIcons.gestureAnimation(true), contentDescription = label)
        },
        checked = settings.gestureHintEnabled,
        enabled = enabled,
        onCheckedChange = onGestureHintEnabledChange,
        onNavigate = onOpenAnimationStyleSelect,
    )
}

@Composable
fun AnimationStyleSelectScreen(
    settings: AppSettings,
    enabled: Boolean,
    onBack: () -> Unit,
    onStyleSelected: (GestureHintStyle) -> Unit,
    onOpenStyleConfig: (GestureHintStyle) -> Unit,
    onGestureHintFingerOffsetDpChange: (Float) -> Unit,
) {
    val selected = settings.gestureHintStyle()
    val selectHint = stringResource(R.string.animation_style_select_hint)
    val styleTitle = stringResource(R.string.gesture_hint_style_title)
    val gestureTitle = stringResource(R.string.gesture_animation_title)
    val fingerOffsetHint = stringResource(R.string.gesture_hint_finger_offset_hint)

    SettingsScreenScaffold(
        title = stringResource(R.string.gesture_hint_style_title),
        subtitle = stringResource(R.string.animation_style_select_desc),
        onBack = onBack,
    ) {
        settingsLazyHint(key = "animation-style-select-hint", text = selectHint)
        settingsLazySmallTitle(key = "animation-style-cards-title", title = styleTitle)
        LazySettingsItem(key = "animation-style-cards") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GestureHintStyle.entries.forEach { style ->
                    val isSelected = selected == style
                    AnimationStyleCard(
                        title = gestureHintStyleLabel(style),
                        description = animationStyleDescription(style),
                        selected = isSelected,
                        preview = {
                            AnimationStylePreview(
                                style = style,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                        trailing = if (isSelected) {
                            {
                                AnimationStyleSettingsButton(
                                    onClick = { onOpenStyleConfig(style) },
                                )
                            }
                        } else {
                            null
                        },
                        onClick = {
                            if (enabled) onStyleSelected(style)
                        },
                    )
                }
            }
        }
        settingsLazySmallTitle(key = "gesture-animation-offset", title = gestureTitle, sectionTop = true)
        groupedCardItems(
            keyPrefix = "gesture-finger-offset",
            items = buildList {
                add(
                    settingsCardScopeItem("finger-offset") {
                        SettingsSliderRow(
                            title = stringResource(R.string.gesture_hint_finger_offset_title),
                            value = settings.gestureHintFingerOffsetDp,
                            valueRange = AnimationStyleLimits.MIN_GESTURE_HINT_FINGER_OFFSET_DP
                                ..AnimationStyleLimits.MAX_GESTURE_HINT_FINGER_OFFSET_DP,
                            enabled = enabled,
                            label = "${settings.gestureHintFingerOffsetDp.roundToInt()} dp",
                            commitOnFinish = true,
                            startLabel = stringResource(R.string.animation_style_small),
                            endLabel = stringResource(R.string.animation_style_large),
                            onValueChange = onGestureHintFingerOffsetDpChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("finger-offset-hint") {
                        SettingsHintText(fingerOffsetHint)
                    },
                )
            },
        )
    }
}

@Composable
private fun animationStyleDescription(style: GestureHintStyle): String = when (style) {
    GestureHintStyle.WAVE -> stringResource(R.string.gesture_hint_style_wave_desc)
    GestureHintStyle.CAPSULE -> stringResource(R.string.gesture_hint_style_capsule_desc)
    GestureHintStyle.BUBBLE -> stringResource(R.string.gesture_hint_style_bubble_desc)
}
