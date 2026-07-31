package com.slideindex.app.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ui.settings.components.LocalSettingsCardScope
import com.slideindex.app.ui.settings.components.PermissionCard as PermissionCardImpl
import com.slideindex.app.ui.settings.components.SettingLinkRow as SettingLinkRowImpl
import com.slideindex.app.ui.settings.components.SettingNavigationRow as SettingNavigationRowImpl
import com.slideindex.app.ui.settings.components.SettingRadioRow as SettingRadioRowImpl
import com.slideindex.app.ui.settings.components.SettingSwitchNavigationRow as SettingSwitchNavigationRowImpl
import com.slideindex.app.ui.settings.components.SettingSwitchRow as SettingSwitchRowImpl
import com.slideindex.app.ui.settings.components.SettingToggleRow as SettingToggleRowImpl
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsEmbeddedContent as SettingsEmbeddedContentImpl
import com.slideindex.app.ui.settings.components.SettingsHintText as SettingsHintTextImpl
import com.slideindex.app.ui.settings.components.SettingsRadioGroup as SettingsRadioGroupImpl
import com.slideindex.app.ui.settings.components.SettingsRangeSliderRow as SettingsRangeSliderRowImpl
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold as SettingsLazyScreenScaffoldImpl
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
fun SettingsLazyScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) = SettingsLazyScreenScaffoldImpl(
    title = title,
    modifier = modifier,
    subtitle = subtitle,
    onBack = onBack,
    content = content,
)

@Composable
fun SettingsSectionTitle(title: String, modifier: Modifier = Modifier) =
    SettingsSectionTitleImpl(title, modifier)

@Composable
fun SettingsHintText(text: String, modifier: Modifier = Modifier) =
    SettingsHintTextImpl(text, modifier)

@Composable
fun SettingsCardScope.SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    icon: (@Composable (accessibilityLabel: String) -> Unit)? = null,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) = SettingSwitchRowImpl(title, subtitle, icon, checked, enabled, onCheckedChange)

@Composable
fun SettingsCardScope.SettingSwitchNavigationRow(
    title: String,
    subtitle: String,
    icon: (@Composable (accessibilityLabel: String) -> Unit)? = null,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onNavigate: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) = SettingSwitchNavigationRowImpl(
    title, subtitle, icon, checked, enabled, onCheckedChange, onNavigate, onLongClick,
)

@Composable
fun SettingsCardScope.SettingLinkRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = SettingLinkRowImpl(title, subtitle, enabled, onClick)

@Composable
fun SettingsCardScope.SettingToggleRow(
    icon: @Composable (accessibilityLabel: String) -> Unit,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) = SettingToggleRowImpl(icon, title, subtitle, checked, enabled, onCheckedChange)

@Composable
fun SettingsCardScope.SettingNavigationRow(
    icon: @Composable (accessibilityLabel: String) -> Unit,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
) = SettingNavigationRowImpl(icon, title, subtitle, enabled, onClick, trailingContent)

@Composable
fun SettingsCardScope.SettingRadioRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    enabled: Boolean = true,
    segmentKey: Any = title,
    onClick: () -> Unit,
) = SettingRadioRowImpl(title, subtitle, selected, enabled, segmentKey, onClick)

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    icon: (@Composable (accessibilityLabel: String) -> Unit)? = null,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val scope = LocalSettingsCardScope.current
    if (scope != null) {
        scope.SettingSwitchRow(title, subtitle, icon, checked, enabled, onCheckedChange)
    } else {
        SettingsCard {
            SettingSwitchRow(title, subtitle, icon, checked, enabled, onCheckedChange)
        }
    }
}

@Composable
fun SettingSwitchNavigationRow(
    title: String,
    subtitle: String,
    icon: (@Composable (accessibilityLabel: String) -> Unit)? = null,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onNavigate: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val scope = LocalSettingsCardScope.current
    if (scope != null) {
        scope.SettingSwitchNavigationRow(
            title, subtitle, icon, checked, enabled, onCheckedChange, onNavigate, onLongClick,
        )
    } else {
        SettingsCard {
            SettingSwitchNavigationRow(
                title, subtitle, icon, checked, enabled, onCheckedChange, onNavigate, onLongClick,
            )
        }
    }
}

@Composable
fun SettingLinkRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val scope = LocalSettingsCardScope.current
    if (scope != null) {
        scope.SettingLinkRow(title, subtitle, enabled, onClick)
    } else {
        SettingsCard {
            SettingLinkRow(title, subtitle, enabled, onClick)
        }
    }
}

@Composable
fun SettingToggleRow(
    icon: @Composable (accessibilityLabel: String) -> Unit,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val scope = LocalSettingsCardScope.current
    if (scope != null) {
        scope.SettingToggleRow(icon, title, subtitle, checked, enabled, onCheckedChange)
    } else {
        SettingsCard {
            SettingToggleRow(icon, title, subtitle, checked, enabled, onCheckedChange)
        }
    }
}

@Composable
fun SettingNavigationRow(
    icon: @Composable (accessibilityLabel: String) -> Unit,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val scope = LocalSettingsCardScope.current
    if (scope != null) {
        scope.SettingNavigationRow(icon, title, subtitle, enabled, onClick, trailingContent)
    } else {
        SettingsCard {
            SettingNavigationRow(icon, title, subtitle, enabled, onClick, trailingContent)
        }
    }
}

@Composable
fun SettingRadioRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    enabled: Boolean = true,
    segmentKey: Any = title,
    onClick: () -> Unit,
) {
    val scope = LocalSettingsCardScope.current
    if (scope != null) {
        scope.SettingRadioRow(title, subtitle, selected, enabled, segmentKey, onClick)
    } else {
        SettingsCard {
            SettingRadioRow(title, subtitle, selected, enabled, segmentKey, onClick)
        }
    }
}

@Composable
fun SettingsSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean,
    label: String,
    formatLabel: ((Float) -> String)? = null,
    commitOnFinish: Boolean = true,
    snapValue: ((Float) -> Float)? = null,
    startLabel: String? = null,
    endLabel: String? = null,
    triggersLayoutPreview: Boolean = false,
    onLayoutPreviewStart: () -> Unit = {},
    onLayoutPreviewStop: () -> Unit = {},
    onLayoutPreviewValueChange: (Float) -> Unit = {},
    onValueChange: (Float) -> Unit,
) {
    val scope = LocalSettingsCardScope.current
    if (scope != null) {
        scope.SettingsSliderRow(
            title, value, valueRange, steps, enabled, label, formatLabel, commitOnFinish, snapValue,
            startLabel, endLabel, triggersLayoutPreview, onLayoutPreviewStart, onLayoutPreviewStop,
            onLayoutPreviewValueChange, onValueChange,
        )
    } else {
        SettingsCard {
            SettingsSliderRow(
                title, value, valueRange, steps, enabled, label, formatLabel, commitOnFinish, snapValue,
                startLabel, endLabel, triggersLayoutPreview, onLayoutPreviewStart, onLayoutPreviewStop,
                onLayoutPreviewValueChange, onValueChange,
            )
        }
    }
}

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
    onLayoutPreviewValueChange: (ClosedFloatingPointRange<Float>) -> Unit = {},
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
) {
    val scope = LocalSettingsCardScope.current
    if (scope != null) {
        scope.SettingsRangeSliderRow(
            title, values, valueRange, startLabel, endLabel, enabled,
            triggersLayoutPreview, onLayoutPreviewStart, onLayoutPreviewStop,
            onLayoutPreviewValueChange, onValueChange,
        )
    } else {
        SettingsCard {
            SettingsRangeSliderRow(
                title, values, valueRange, startLabel, endLabel, enabled,
                triggersLayoutPreview, onLayoutPreviewStart, onLayoutPreviewStop,
                onLayoutPreviewValueChange, onValueChange,
            )
        }
    }
}

@Composable
fun SettingsRadioGroup(content: @Composable SettingsCardScope.() -> Unit) = SettingsRadioGroupImpl(content)

@Composable
fun SettingsCardScope.SettingsSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean,
    label: String,
    formatLabel: ((Float) -> String)? = null,
    commitOnFinish: Boolean = true,
    snapValue: ((Float) -> Float)? = null,
    startLabel: String? = null,
    endLabel: String? = null,
    triggersLayoutPreview: Boolean = false,
    onLayoutPreviewStart: () -> Unit = {},
    onLayoutPreviewStop: () -> Unit = {},
    onLayoutPreviewValueChange: (Float) -> Unit = {},
    onValueChange: (Float) -> Unit,
) = SettingsSliderRowImpl(
    title, value, valueRange, steps, enabled, label, formatLabel, commitOnFinish, snapValue,
    startLabel, endLabel, triggersLayoutPreview, onLayoutPreviewStart, onLayoutPreviewStop,
    onLayoutPreviewValueChange, onValueChange,
)

@Composable
fun SettingsCardScope.SettingsRangeSliderRow(
    title: String,
    values: ClosedFloatingPointRange<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    startLabel: String,
    endLabel: String,
    enabled: Boolean,
    triggersLayoutPreview: Boolean = false,
    onLayoutPreviewStart: () -> Unit = {},
    onLayoutPreviewStop: () -> Unit = {},
    onLayoutPreviewValueChange: (ClosedFloatingPointRange<Float>) -> Unit = {},
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
) = SettingsRangeSliderRowImpl(
    title, values, valueRange, startLabel, endLabel, enabled,
    triggersLayoutPreview, onLayoutPreviewStart, onLayoutPreviewStop,
    onLayoutPreviewValueChange, onValueChange,
)

@Composable
fun PermissionCard(
    title: String,
    description: String,
    onGrant: () -> Unit,
    grantLabel: String = stringResource(R.string.grant_permission),
) = PermissionCardImpl(title, description, onGrant, grantLabel)

@Composable
fun SettingsCardScope.ThemeColorPicker(
    selected: Int,
    enabled: Boolean,
    onColorSelected: (Int) -> Unit,
) = ThemeColorPickerImpl(selected, enabled, onColorSelected)
