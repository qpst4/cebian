@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.messagestyle

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.MessageThemeCatalog
import com.slideindex.app.message.MessageThemeSpec
import com.slideindex.app.message.messageThemeBackground
import com.slideindex.app.ui.AnimatedFullScreenOverlay
import com.slideindex.app.ui.SettingLinkRow
import com.slideindex.app.ui.SettingRadioRow
import com.slideindex.app.ui.SettingsCard
import com.slideindex.app.ui.SettingsRadioPickerScreen
import com.slideindex.app.ui.SettingsSectionTitle
import com.slideindex.app.ui.SettingsSliderRow

@Composable
internal fun PrimaryDisplaySettings(
    settings: MessageSettings,
    enabled: Boolean,
    maxLines: Int,
    opacity: Float,
    opacityTitleRes: Int,
    onOpacityChange: (Float) -> Unit,
    onMaxLinesChange: (Int) -> Unit,
    onAutoDismissSecondsChange: (Int) -> Unit,
    onPickSideCount: (() -> Unit)? = null,
    sideMaxCount: Int = 3,
    opacitySteps: Int = 7,
    opacityRange: ClosedFloatingPointRange<Float> = 0.2f..1f,
) {
    SettingsSectionTitle(stringResource(R.string.message_style_section_display))
    SettingsCard {
        SettingsSliderRow(
            title = stringResource(opacityTitleRes),
            value = opacity,
            valueRange = opacityRange,
            steps = opacitySteps,
            enabled = enabled,
            label = "${(opacity * 100).toInt()}%",
            formatLabel = { "${(it * 100).toInt()}%" },
            onValueChange = onOpacityChange,
        )
        SettingsSliderRow(
            title = stringResource(R.string.message_style_max_lines),
            value = maxLines.toFloat(),
            valueRange = 1f..3f,
            steps = 1,
            enabled = enabled,
            label = maxLines.toString(),
            formatLabel = { it.toInt().toString() },
            onValueChange = { onMaxLinesChange(it.toInt()) },
        )
        if (onPickSideCount != null) {
            SettingLinkRow(
                title = stringResource(R.string.message_style_side_count),
                subtitle = stringResource(R.string.message_style_side_count_option, sideMaxCount),
                enabled = enabled,
                onClick = { if (enabled) onPickSideCount() },
            )
        }
        val autoDismissOffLabel = stringResource(R.string.message_reminder_auto_dismiss_off)
        SettingsSliderRow(
            title = stringResource(R.string.message_reminder_auto_dismiss),
            value = settings.autoDismissSeconds.toFloat(),
            valueRange = 0f..30f,
            steps = 29,
            enabled = enabled,
            label = if (settings.autoDismissSeconds <= 0) {
                autoDismissOffLabel
            } else {
                stringResource(R.string.message_reminder_auto_dismiss_seconds, settings.autoDismissSeconds)
            },
            formatLabel = { value ->
                val seconds = value.toInt()
                if (seconds <= 0) autoDismissOffLabel else "$seconds s"
            },
            onValueChange = { onAutoDismissSecondsChange(it.toInt()) },
        )
    }
}

@Composable
internal fun SideBubbleCountPickerOverlay(
    visible: Boolean,
    selectedCount: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AnimatedFullScreenOverlay(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
    ) {
        SettingsRadioPickerScreen(
            title = stringResource(R.string.message_style_side_count),
            onBack = onDismiss,
        ) {
            (9 downTo 1).forEach { count ->
                SettingRadioRow(
                    title = stringResource(R.string.message_style_side_count_option, count),
                    selected = selectedCount == count,
                    onClick = { onSelect(count) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MessageThemeGrid(
    themes: List<MessageThemeSpec>,
    selectedThemeId: String,
    enabled: Boolean,
    onThemeSelected: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val normalizedSelectedId = MessageThemeCatalog.normalizeThemeId(selectedThemeId)
        themes.forEach { theme ->
            val selected = theme.id == normalizedSelectedId
            val borderColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            }
            Column(
                modifier = Modifier
                    .size(width = 108.dp, height = 88.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled) { onThemeSelected(theme.id) }
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .then(
                            if (theme.cornerRadiusDp > 0f) {
                                Modifier.clip(RoundedCornerShape(theme.cornerRadiusDp.dp))
                            } else {
                                Modifier
                            },
                        )
                        .messageThemeBackground(theme),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = "Aa",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = Color(theme.titleColorArgb),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    text = stringResource(theme.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
