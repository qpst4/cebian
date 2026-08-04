package com.slideindex.app.ui.settings.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.SettingIconContainer
import com.slideindex.app.ui.miuix.MiuixArrowRow
import com.slideindex.app.ui.miuix.MiuixGroupedCard
import com.slideindex.app.ui.miuix.miuixGroupedCardItem
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.basic.DropdownItem

@Composable
fun SettingsCardScope.SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    icon: (@Composable (accessibilityLabel: String) -> Unit)? = null,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsCardRow(key = title) { position ->
        SwitchPreference(
            modifier = Modifier.miuixGroupedCardItem(position.index, position.count),
            title = title,
            summary = subtitle,
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            startAction = icon?.let { { SettingIconContainer { it(title) } } },
        )
    }
}

@Composable
fun SwitchNavigationTrailingContent(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(start = 8.dp),
    ) {
        VerticalDivider(
            modifier = Modifier.height(32.dp),
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = { if (enabled) onCheckedChange(it) },
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

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
) {
    SettingsCardRow(key = title) { position ->
        val rowModifier = Modifier
            .miuixGroupedCardItem(position.index, position.count)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        enabled = enabled,
                        onClick = { if (enabled) onNavigate() },
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                },
            )
        BasicComponent(
            modifier = rowModifier,
            title = title,
            summary = subtitle,
            enabled = enabled,
            startAction = icon?.let { { SettingIconContainer { it(title) } } },
            onClick = if (onLongClick == null) {
                { if (enabled) onNavigate() }
            } else {
                null
            },
            endActions = {
                SwitchNavigationTrailingContent(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = onCheckedChange,
                )
            },
        )
    }
}

@Composable
fun SettingsCardScope.SettingLinkRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SettingsCardRow(key = title) { position ->
        ArrowPreference(
            modifier = Modifier.miuixGroupedCardItem(position.index, position.count),
            title = title,
            summary = subtitle,
            enabled = enabled,
            onClick = onClick,
        )
    }
}

@Composable
fun SettingsCardScope.SettingToggleRow(
    icon: @Composable (accessibilityLabel: String) -> Unit,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsCardRow(key = title) { position ->
        SwitchPreference(
            modifier = Modifier.miuixGroupedCardItem(position.index, position.count),
            title = title,
            summary = subtitle,
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            startAction = { SettingIconContainer { icon(title) } },
        )
    }
}

@Composable
fun SettingsCardScope.SettingDropdownRow(
    title: String,
    subtitle: String? = null,
    items: List<String>,
    selectedIndex: Int,
    enabled: Boolean = true,
    icon: (@Composable (accessibilityLabel: String) -> Unit)? = null,
    onSelectedIndexChange: (Int) -> Unit,
) {
    SettingsCardRow(key = title) { position ->
        WindowDropdownPreference(
            modifier = Modifier.miuixGroupedCardItem(position.index, position.count),
            title = title,
            summary = subtitle,
            items = items,
            selectedIndex = selectedIndex.coerceIn(0, (items.lastIndex).coerceAtLeast(0)),
            enabled = enabled,
            startAction = icon?.let { { SettingIconContainer { it(title) } } },
            onSelectedIndexChange = onSelectedIndexChange,
        )
    }
}

@Composable
fun SettingsCardScope.SettingSpinnerRow(
    title: String,
    subtitle: String? = null,
    dialogButtonText: String,
    items: List<DropdownItem>,
    selectedIndex: Int,
    enabled: Boolean = true,
    icon: (@Composable (accessibilityLabel: String) -> Unit)? = null,
    onSelectedIndexChange: (Int) -> Unit,
) {
    SettingsCardRow(key = title) { position ->
        WindowSpinnerPreference(
            modifier = Modifier.miuixGroupedCardItem(position.index, position.count),
            title = title,
            summary = subtitle,
            dialogButtonString = dialogButtonText,
            items = items,
            selectedIndex = selectedIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0)),
            enabled = enabled,
            startAction = icon?.let { { SettingIconContainer { it(title) } } },
            onSelectedIndexChange = onSelectedIndexChange,
        )
    }
}

@Composable
fun SettingsCardScope.SettingNavigationRow(
    icon: @Composable (accessibilityLabel: String) -> Unit,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    SettingsCardRow(key = title) { position ->
        if (trailingContent != null) {
            BasicComponent(
                modifier = Modifier.miuixGroupedCardItem(position.index, position.count),
                title = title,
                summary = subtitle,
                enabled = enabled,
                startAction = { SettingIconContainer { icon(title) } },
                onClick = onClick,
                endActions = { trailingContent() },
            )
        } else {
            ArrowPreference(
                modifier = Modifier.miuixGroupedCardItem(position.index, position.count),
                title = title,
                summary = subtitle,
                enabled = enabled,
                startAction = { SettingIconContainer { icon(title) } },
                onClick = onClick,
            )
        }
    }
}

/** Hub / 设置入口：无 M3 图标，纯 Miuix 箭头行 + 分组圆角。 */
@Composable
fun SettingsCardScope.MiuixNavigationRow(
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    rowKey: Any = title,
) {
    SettingsCardRow(key = rowKey) { position ->
        MiuixGroupedCard(index = position.index, count = position.count) {
            MiuixArrowRow(
                title = title,
                summary = summary,
                enabled = enabled,
                onClick = onClick,
            )
        }
    }
}

@Composable
fun SettingsCardScope.SettingRadioRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    enabled: Boolean = true,
    segmentKey: Any = title,
    onClick: () -> Unit,
) {
    SettingsCardRow(key = segmentKey) { position ->
        BasicComponent(
            modifier = Modifier.miuixGroupedCardItem(position.index, position.count),
            title = title,
            summary = subtitle,
            enabled = enabled,
            onClick = { if (enabled) onClick() },
            endActions = {
                RadioButton(
                    selected = selected,
                    onClick = { if (enabled) onClick() },
                )
            },
        )
    }
}

@Composable
fun SettingsRadioGroup(content: @Composable SettingsCardScope.() -> Unit) {
    val coordinator = remember { SettingsCardGroupCoordinator() }
    val scope = SettingsCardScope()
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        coordinator.clear()
        CompositionLocalProvider(
            LocalSettingsCardScope provides scope,
            LocalSettingsCardGroupCoordinator provides coordinator,
        ) {
            scope.content()
        }
        coordinator.RenderRows()
    }
}
