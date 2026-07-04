@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.data.AppInfo
import com.slideindex.app.util.toSafeImageBitmap

internal val PickerListHorizontalPadding = 16.dp
internal val PickerListGroupSpacing = 16.dp
internal val PickerListContentPadding = PaddingValues(
    horizontal = PickerListHorizontalPadding,
    vertical = 8.dp,
)

enum class PickerTrailingMode {
    None,
    Radio,
    Toggle,
    Icon,
}

@Composable
internal fun pickerSegmentedColors() = ListItemDefaults.segmentedColors()

@Composable
internal fun pickerSegmentedShapes(index: Int, count: Int) =
    ListItemDefaults.segmentedShapes(index = index, count = count)

@Composable
internal fun pickerListSegmentedGap() = ListItemDefaults.SegmentedGap

@Composable
fun Md3PickerSectionHeader(title: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
fun Md3PickerListRow(
    segmentIndex: Int,
    segmentCount: Int,
    title: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingContent: @Composable () -> Unit,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingMode: PickerTrailingMode = PickerTrailingMode.None,
    trailingIcon: ImageVector? = null,
    trailingIconDescription: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val shapes = pickerSegmentedShapes(segmentIndex, segmentCount)
    val colors = pickerSegmentedColors()
    val headline: @Composable () -> Unit = {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val supporting: (@Composable () -> Unit)? = when {
        supportingContent != null -> supportingContent
        !subtitle.isNullOrBlank() -> {
            {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        else -> null
    }

    when (trailingMode) {
        PickerTrailingMode.Radio -> {
            val click = onClick ?: return
            SegmentedListItem(
                selected = selected,
                onClick = click,
                modifier = modifier,
                enabled = enabled,
                shapes = shapes,
                colors = colors,
                leadingContent = leadingContent,
                trailingContent = {
                    RadioButton(selected = selected, onClick = null)
                },
                supportingContent = supporting,
                content = headline,
            )
        }
        PickerTrailingMode.Toggle -> {
            val click = onTrailingClick ?: onClick ?: return
            SegmentedListItem(
                checked = selected,
                onCheckedChange = { click() },
                modifier = modifier,
                enabled = enabled,
                shapes = shapes,
                colors = colors,
                leadingContent = leadingContent,
                trailingContent = {
                    Checkbox(checked = selected, onCheckedChange = null)
                },
                supportingContent = supporting,
                content = headline,
            )
        }
        PickerTrailingMode.Icon -> {
            val icon = trailingIcon ?: return
            val click = onTrailingClick ?: onClick ?: return
            SegmentedListItem(
                onClick = click,
                modifier = modifier,
                enabled = enabled,
                shapes = shapes,
                colors = colors,
                leadingContent = leadingContent,
                trailingContent = {
                    IconButton(onClick = click) {
                        Icon(
                            imageVector = icon,
                            contentDescription = trailingIconDescription,
                        )
                    }
                },
                supportingContent = supporting,
                content = headline,
            )
        }
        PickerTrailingMode.None -> {
            SegmentedListItem(
                onClick = onClick ?: {},
                modifier = modifier,
                enabled = enabled && onClick != null,
                shapes = shapes,
                colors = colors,
                leadingContent = leadingContent,
                supportingContent = supporting,
                content = headline,
            )
        }
    }
}

@Composable
fun Md3PickerIconLeading(
    icon: ImageVector,
    selected: Boolean,
    contentDescription: String? = null,
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
fun Md3PickerAppLeading(app: AppInfo) {
    val bitmap = remember(app.packageName) { app.icon.toSafeImageBitmap(96) }
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = app.label,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .clip(MaterialTheme.shapes.extraSmall),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
fun Md3PickerAppEntryLeading(
    entry: AppPackageEntry,
    missingIcon: ImageVector,
) {
    when (entry) {
        is AppPackageEntry.Installed -> Md3PickerAppLeading(entry.app)
        is AppPackageEntry.Missing -> Md3PickerIconLeading(
            icon = missingIcon,
            selected = false,
        )
    }
}

@Composable
fun Md3PickerSupportingHints(
    description: String?,
    permissionHint: String?,
) {
    if (description.isNullOrBlank() && permissionHint.isNullOrBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!permissionHint.isNullOrBlank()) {
            Text(
                text = permissionHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}
