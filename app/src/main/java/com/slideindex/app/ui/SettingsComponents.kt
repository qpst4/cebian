package com.slideindex.app.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ui.settings.components.PermissionCard as PermissionCardImpl
import com.slideindex.app.ui.settings.components.SettingLinkRow as SettingLinkRowImpl
import com.slideindex.app.ui.settings.components.SettingNavigationRow as SettingNavigationRowImpl
import com.slideindex.app.ui.settings.components.SettingRadioRow as SettingRadioRowImpl
import com.slideindex.app.ui.settings.components.SettingSwitchNavigationRow as SettingSwitchNavigationRowImpl
import com.slideindex.app.ui.settings.components.SettingSwitchRow as SettingSwitchRowImpl
import com.slideindex.app.ui.settings.components.SettingToggleRow as SettingToggleRowImpl
import com.slideindex.app.ui.settings.components.SettingsEmbeddedContent as SettingsEmbeddedContentImpl
import com.slideindex.app.ui.settings.components.SettingsHintText as SettingsHintTextImpl
import com.slideindex.app.ui.settings.components.SettingsRadioGroup as SettingsRadioGroupImpl
import com.slideindex.app.ui.settings.components.SettingsRangeSliderRow as SettingsRangeSliderRowImpl
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold as SettingsScreenScaffoldImpl
import com.slideindex.app.ui.settings.components.SettingsSectionTitle as SettingsSectionTitleImpl
import com.slideindex.app.ui.settings.components.SettingsSliderRow as SettingsSliderRowImpl
import com.slideindex.app.ui.settings.components.ThemeColorPicker as ThemeColorPickerImpl

@Composable
fun SettingsEmbeddedContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit,
) = SettingsEmbeddedContentImpl(modifier, contentPadding, content)

@Composable
fun SettingsScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    embedded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) = SettingsScreenScaffoldImpl(
    title = title,
    modifier = modifier,
    subtitle = subtitle,
    onBack = onBack,
    embedded = embedded,
    content = content,
)

@Composable
fun SettingsSectionTitle(title: String, modifier: Modifier = Modifier) =
    SettingsSectionTitleImpl(title, modifier)

@Composable
fun SettingsHintText(text: String, modifier: Modifier = Modifier) =
    SettingsHintTextImpl(text, modifier)

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) = SettingSwitchRowImpl(title, subtitle, icon, checked, enabled, onCheckedChange)

@Composable
fun SettingSwitchNavigationRow(
    title: String,
    subtitle: String,
    icon: (@Composable () -> Unit)? = null,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onNavigate: () -> Unit,
) = SettingSwitchNavigationRowImpl(title, subtitle, icon, checked, enabled, onCheckedChange, onNavigate)

@Composable
fun SettingLinkRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = SettingLinkRowImpl(title, subtitle, enabled, onClick)

@Composable
fun SettingToggleRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) = SettingToggleRowImpl(icon, title, subtitle, checked, enabled, onCheckedChange)

@Composable
fun SettingNavigationRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
) = SettingNavigationRowImpl(icon, title, subtitle, enabled, onClick, trailingContent)

@Composable
fun SettingRadioRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = SettingRadioRowImpl(title, subtitle, selected, enabled, onClick)

@Composable
fun SettingsRadioGroup(content: @Composable () -> Unit) = SettingsRadioGroupImpl(content)

@Composable
fun SettingsSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean,
    label: String,
    formatLabel: ((Float) -> String)? = null,
    commitOnFinish: Boolean = false,
    snapValue: ((Float) -> Float)? = null,
    startLabel: String? = null,
    endLabel: String? = null,
    triggersLayoutPreview: Boolean = false,
    onLayoutPreviewStart: () -> Unit = {},
    onLayoutPreviewStop: () -> Unit = {},
    onValueChange: (Float) -> Unit,
) = SettingsSliderRowImpl(
    title, value, valueRange, steps, enabled, label, formatLabel, commitOnFinish, snapValue,
    startLabel, endLabel, triggersLayoutPreview, onLayoutPreviewStart, onLayoutPreviewStop, onValueChange,
)

@Composable
fun SettingsRangeSliderRow(
    title: String,
    values: ClosedFloatingPointRange<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    startLabel: String,
    endLabel: String,
    enabled: Boolean,
    triggersLayoutPreview: Boolean = false,
    onLayoutPreviewStart: () -> Unit = {},
    onLayoutPreviewStop: () -> Unit = {},
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
) = SettingsRangeSliderRowImpl(
    title, values, valueRange, startLabel, endLabel, enabled,
    triggersLayoutPreview, onLayoutPreviewStart, onLayoutPreviewStop, onValueChange,
)

@Composable
fun PermissionCard(
    title: String,
    description: String,
    onGrant: () -> Unit,
    grantLabel: String = stringResource(R.string.grant_permission),
) = PermissionCardImpl(title, description, onGrant, grantLabel)

@Composable
fun ThemeColorPicker(
    selected: Int,
    enabled: Boolean,
    onColorSelected: (Int) -> Unit,
) = ThemeColorPickerImpl(selected, enabled, onColorSelected)
