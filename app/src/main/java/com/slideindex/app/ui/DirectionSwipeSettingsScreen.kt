package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.SwipeDirectionFamily
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.gestureConfigSide
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DirectionSwipeSettingsScreen(
    side: PanelSide,
    handleId: String,
    family: SwipeDirectionFamily,
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenSlotConfig: (GestureTriggerType) -> Unit,
) {
    val configSide = settings.gestureConfigSide(side, handleId)
    val straightSectionTitle = stringResource(R.string.side_gestures_direction_straight_section)
    val pauseSectionTitle = stringResource(R.string.side_gestures_direction_pause_section)
    val shortSlotTitle = stringResource(R.string.side_gestures_direction_short_slot)
    val longSlotTitle = stringResource(R.string.side_gestures_direction_long_slot)
    val pauseReleaseTitle = stringResource(R.string.side_gestures_direction_pause_release)

    SettingsScreenScaffold(
        title = swipeDirectionFamilyTitle(side, family),
        subtitle = stringResource(R.string.side_gestures_direction_swipe_subtitle),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "direction-${family.name}-straight",
            title = straightSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "direction-${family.name}-straight",
            items = listOf(
                settingsCardScopeItem("direction-${family.name}-short") {
                    GestureDirectionSlotRow(
                        side = side,
                        trigger = family.shortTrigger,
                        title = shortSlotTitle,
                        subtitle = gestureSlotConfigSubtitle(settings, configSide, handleId, family.shortTrigger),
                        onClick = { onOpenSlotConfig(family.shortTrigger) },
                    )
                },
                settingsCardScopeItem("direction-${family.name}-long") {
                    GestureDirectionSlotRow(
                        side = side,
                        trigger = family.longTrigger,
                        title = longSlotTitle,
                        subtitle = gestureSlotConfigSubtitle(settings, configSide, handleId, family.longTrigger),
                        onClick = { onOpenSlotConfig(family.longTrigger) },
                    )
                },
            ),
        )

        settingsLazySmallTitle(
            key = "direction-${family.name}-pause",
            title = pauseSectionTitle,
            sectionTop = true,
        )
        groupedCardItems(
            keyPrefix = "direction-${family.name}-pause",
            items = listOf(
                settingsCardScopeItem("direction-${family.name}-hover") {
                    GestureDirectionSlotRow(
                        side = side,
                        trigger = family.hoverTrigger,
                        title = pauseReleaseTitle,
                        subtitle = gestureSlotConfigSubtitle(settings, configSide, handleId, family.hoverTrigger),
                        onClick = { onOpenSlotConfig(family.hoverTrigger) },
                    )
                },
            ),
        )
    }
}

@Composable
fun SettingsCardScope.GestureDirectionSlotRow(
    side: PanelSide,
    trigger: GestureTriggerType,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { contentDescription ->
            GestureTriggerIcon(
                side = side,
                trigger = trigger,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp),
            )
        },
        title = title,
        subtitle = subtitle,
        onClick = onClick,
    )
}

@Composable
fun InwardSwipePathPreview(
    side: PanelSide,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GestureTriggerIcon(
            side = side,
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            contentDescription = "",
            modifier = Modifier.size(20.dp),
        )
        InwardPathArrowIcon()
        Icon(
            imageVector = Icons.Default.MoreHoriz,
            contentDescription = stringResource(R.string.side_gestures_path_pause),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        InwardPathArrowIcon()
        GestureTriggerIcon(
            side = side,
            trigger = GestureTriggerType.SHORT_SWIPE_IN_UP,
            contentDescription = "",
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun InwardPathArrowIcon() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
