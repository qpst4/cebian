@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.pickresult.SearchEngineIcon
import com.slideindex.app.search.ImageSearchEngine
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.ui.pickerSegmentedShapes
import com.slideindex.app.ui.settingsSegmentedColors
import com.slideindex.app.ui.SettingIconContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageShareEngineReorderRow(
    engine: SearchEngineConfig,
    segmentIndex: Int,
    segmentCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = pickerSegmentedShapes(segmentIndex, segmentCount),
        colors = settingsSegmentedColors(),
        modifier = modifier,
        leadingContent = {
            SettingIconContainer {
                SearchEngineIcon(engine = engine, modifier = Modifier.size(24.dp))
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.search_engine_delete_confirm),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        supportingContent = {
            Text(
                text = stringResource(R.string.search_engine_type_share_image),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        content = {
            Text(
                text = engine.name,
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AggregatedImageSearchEngineReorderRow(
    engine: ImageSearchEngine,
    subtitle: String,
    segmentIndex: Int,
    segmentCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = pickerSegmentedShapes(segmentIndex, segmentCount),
        colors = settingsSegmentedColors(),
        modifier = modifier,
        leadingContent = {
            SettingIconContainer {
                AggregatedImageSearchEngineIcon(
                    engine = engine,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.cd_navigate_forward),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                text = engine.displayName,
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        },
    )
}
