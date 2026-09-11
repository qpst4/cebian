@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.messagestyle

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.MessageSettings.Companion.C_NOTICE_MAX_RETAINED_CONVERSATIONS
import com.slideindex.app.ui.SettingsSliderRow
import com.slideindex.app.ui.SettingSwitchRow
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SETTINGS_SLIDER_PERCENT_KEY_POINTS_01
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

@Composable
fun cNoticeSettingsCardItems(
    settings: MessageSettings,
    enabled: Boolean,
    onOpacityChange: (Float) -> Unit,
    onDimmedOpacityChange: (Float) -> Unit,
    onGhostOpacityChange: (Float) -> Unit,
    onIconSizeDpChange: (Float) -> Unit,
    onMaxCountChange: (Int) -> Unit,
    onDefaultCollapsedChange: (Boolean) -> Unit,
    onPeekBannerEnabledChange: (Boolean) -> Unit,
    onLandscapeEnabledChange: (Boolean) -> Unit,
    onPreviewChange: (MessageSettings) -> Unit = {},
    onPreviewCommit: () -> Unit = {},
): List<CardItem> = buildList {
    add(
        settingsCardScopeItem("size") {
            SettingsSliderRow(
                title = stringResource(R.string.message_style_c_notice_icon_size),
                value = settings.cNoticeIconSizeDp,
                valueRange = 32f..64f,
                steps = 31,
                enabled = enabled,
                label = "${settings.cNoticeIconSizeDp.toInt()} dp",
                formatLabel = { "${it.toInt()} dp" },
                triggersLayoutPreview = true,
                onLayoutPreviewValueChange = { sizeDp ->
                    onPreviewChange(settings.copy(cNoticeIconSizeDp = sizeDp))
                },
                onValueChange = { sizeDp ->
                    onPreviewCommit()
                    onIconSizeDpChange(sizeDp)
                },
            )
        },
    )
    add(
        settingsCardScopeItem("opacity") {
            SettingsSliderRow(
                title = stringResource(R.string.message_style_c_notice_opacity),
                value = settings.cNoticeOpacity,
                valueRange = 0f..1f,
                enabled = enabled,
                label = "${(settings.cNoticeOpacity * 100).toInt()}%",
                formatLabel = { "${(it * 100).toInt()}%" },
                keyPoints = SETTINGS_SLIDER_PERCENT_KEY_POINTS_01,
                triggersLayoutPreview = true,
                onLayoutPreviewValueChange = { opacity ->
                    onPreviewChange(settings.copy(cNoticeOpacity = opacity))
                },
                onValueChange = { opacity ->
                    onPreviewCommit()
                    onOpacityChange(opacity)
                },
            )
        },
    )
    add(
        settingsCardScopeItem("dimmed-opacity") {
            SettingsSliderRow(
                title = stringResource(R.string.message_style_c_notice_dimmed_opacity),
                value = settings.cNoticeDimmedOpacity,
                valueRange = 0f..1f,
                enabled = enabled,
                label = "${(settings.cNoticeDimmedOpacity * 100).toInt()}%",
                formatLabel = { "${(it * 100).toInt()}%" },
                keyPoints = SETTINGS_SLIDER_PERCENT_KEY_POINTS_01,
                onValueChange = onDimmedOpacityChange,
            )
        },
    )
    add(
        settingsCardScopeItem("ghost-opacity") {
            SettingsSliderRow(
                title = stringResource(R.string.message_style_c_notice_ghost_opacity),
                value = settings.cNoticeGhostOpacity,
                valueRange = 0f..1f,
                enabled = enabled,
                label = "${(settings.cNoticeGhostOpacity * 100).toInt()}%",
                formatLabel = { "${(it * 100).toInt()}%" },
                keyPoints = SETTINGS_SLIDER_PERCENT_KEY_POINTS_01,
                onValueChange = onGhostOpacityChange,
            )
        },
    )
    add(
        settingsCardScopeItem("max-count") {
            SettingsSliderRow(
                title = stringResource(R.string.message_style_c_notice_max_count),
                value = settings.cNoticeMaxCount.toFloat(),
                valueRange = 1f..C_NOTICE_MAX_RETAINED_CONVERSATIONS.toFloat(),
                steps = C_NOTICE_MAX_RETAINED_CONVERSATIONS - 1,
                enabled = enabled,
                label = settings.cNoticeMaxCount.toString(),
                formatLabel = { it.toInt().toString() },
                onValueChange = { onMaxCountChange(it.toInt()) },
            )
        },
    )
    add(
        settingsCardScopeItem("default-collapsed") {
            SettingSwitchRow(
                title = stringResource(R.string.message_style_c_notice_default_collapsed),
                subtitle = stringResource(R.string.message_style_c_notice_default_collapsed_desc),
                checked = settings.cNoticeDefaultCollapsed,
                enabled = enabled,
                onCheckedChange = onDefaultCollapsedChange,
            )
        },
    )
    add(
        settingsCardScopeItem("peek-banner") {
            SettingSwitchRow(
                title = stringResource(R.string.message_style_c_notice_peek_banner),
                subtitle = stringResource(R.string.message_style_c_notice_peek_banner_desc),
                checked = settings.cNoticePeekBannerEnabled,
                enabled = enabled,
                onCheckedChange = onPeekBannerEnabledChange,
            )
        },
    )
    add(
        settingsCardScopeItem("landscape") {
            SettingSwitchRow(
                title = stringResource(R.string.message_style_c_notice_landscape),
                subtitle = stringResource(R.string.message_style_c_notice_landscape_desc),
                checked = settings.cNoticeLandscapeEnabled,
                enabled = enabled,
                onCheckedChange = onLandscapeEnabledChange,
            )
        },
    )
}

fun LazyListScope.cNoticeSettingsSection(
    items: List<CardItem>,
    sectionTitle: String,
) {
    settingsLazySmallTitle(key = "message-c-notice-settings", title = sectionTitle)
    groupedCardItems(
        keyPrefix = "message-c-notice-settings",
        items = items,
    )
}
