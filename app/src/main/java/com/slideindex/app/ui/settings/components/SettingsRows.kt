@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ui.SettingIconContainer
import com.slideindex.app.ui.pickerListSegmentedGap
import com.slideindex.app.ui.pickerSegmentedColors
import com.slideindex.app.ui.pickerSegmentedShapes
import com.slideindex.app.ui.settingsSegmentedColors

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
        SegmentedListItem(
            onClick = { if (enabled) onCheckedChange(!checked) },
            enabled = enabled,
            shapes = pickerSegmentedShapes(position.index, position.count),
            colors = settingsSegmentedColors(),
            leadingContent = icon?.let {
                {
                    SettingIconContainer { it(title) }
                }
            },
            trailingContent = {
                Switch(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = { if (enabled) onCheckedChange(it) },
                )
            },
            supportingContent = subtitle?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    },
                )
            },
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
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides 0.dp,
        ) {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = { if (enabled) onCheckedChange(it) },
                modifier = Modifier.padding(end = 4.dp),
            )
        }
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
        SegmentedListItem(
            onClick = { if (enabled) onNavigate() },
            onLongClick = onLongClick,
            enabled = enabled,
            shapes = pickerSegmentedShapes(position.index, position.count),
            colors = settingsSegmentedColors(),
            leadingContent = icon?.let {
                {
                    SettingIconContainer { it(title) }
                }
            },
            trailingContent = {
                SwitchNavigationTrailingContent(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = onCheckedChange,
                )
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    },
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
        SegmentedListItem(
            onClick = onClick,
            enabled = enabled,
            shapes = pickerSegmentedShapes(position.index, position.count),
            colors = settingsSegmentedColors(),
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.cd_navigate_forward),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            supportingContent = subtitle?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    },
                )
            },
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
        SegmentedListItem(
            onClick = { if (enabled) onCheckedChange(!checked) },
            enabled = enabled,
            shapes = pickerSegmentedShapes(position.index, position.count),
            colors = settingsSegmentedColors(),
            leadingContent = {
                SettingIconContainer { icon(title) }
            },
            trailingContent = {
                Switch(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = { if (enabled) onCheckedChange(it) },
                )
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    },
                )
            },
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
        SegmentedListItem(
            onClick = onClick,
            enabled = enabled,
            shapes = pickerSegmentedShapes(position.index, position.count),
            colors = settingsSegmentedColors(),
            leadingContent = {
                SettingIconContainer { icon(title) }
            },
            trailingContent = {
                if (trailingContent != null) {
                    trailingContent()
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.cd_navigate_forward),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    },
                )
            },
        )
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
        SegmentedListItem(
            selected = selected,
            onClick = { if (enabled) onClick() },
            enabled = enabled,
            shapes = pickerSegmentedShapes(position.index, position.count),
            colors = pickerSegmentedColors(),
            trailingContent = {
                androidx.compose.material3.RadioButton(
                    selected = selected,
                    onClick = { if (enabled) onClick() },
                )
            },
            supportingContent = subtitle?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    },
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
        verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
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
