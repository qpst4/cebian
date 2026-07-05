package com.slideindex.app.ui.animationstyle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.GestureHintStyle
import com.slideindex.app.settings.gestureHintStyle
import com.slideindex.app.ui.SettingSwitchNavigationRow
import com.slideindex.app.ui.SettingsHintText
import com.slideindex.app.ui.SettingsScreenScaffold
import com.slideindex.app.ui.SettingsSectionTitle
import com.slideindex.app.ui.gestureHintStyleLabel

@Composable
fun GestureAnimationSettingsRows(
    settings: AppSettings,
    enabled: Boolean,
    onGestureHintEnabledChange: (Boolean) -> Unit,
    onOpenAnimationStyleSelect: () -> Unit,
) {
    SettingSwitchNavigationRow(
        title = stringResource(R.string.gesture_animation_title),
        subtitle = gestureHintStyleLabel(settings.gestureHintStyle()),
        icon = { Icon(Icons.Default.Animation, contentDescription = null) },
        checked = settings.gestureHintEnabled,
        enabled = enabled,
        onCheckedChange = onGestureHintEnabledChange,
        onNavigate = onOpenAnimationStyleSelect,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimationStyleSelectScreen(
    settings: AppSettings,
    enabled: Boolean,
    onBack: () -> Unit,
    onStyleSelected: (GestureHintStyle) -> Unit,
    onOpenStyleConfig: (GestureHintStyle) -> Unit,
) {
    val selected = settings.gestureHintStyle()
    SettingsScreenScaffold(
        title = stringResource(R.string.gesture_hint_style_title),
        subtitle = stringResource(R.string.animation_style_select_desc),
        onBack = onBack,
    ) {
        SettingsHintText(stringResource(R.string.animation_style_select_hint))
        SettingsSectionTitle(stringResource(R.string.gesture_hint_style_title))
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
}

@Composable
private fun animationStyleDescription(style: GestureHintStyle): String = when (style) {
    GestureHintStyle.WAVE -> stringResource(R.string.gesture_hint_style_wave_desc)
    GestureHintStyle.CAPSULE -> stringResource(R.string.gesture_hint_style_capsule_desc)
    GestureHintStyle.BUBBLE -> stringResource(R.string.gesture_hint_style_bubble_desc)
}
