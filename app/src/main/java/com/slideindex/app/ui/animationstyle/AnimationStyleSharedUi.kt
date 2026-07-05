package com.slideindex.app.ui.animationstyle

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.WaveStyle
import com.slideindex.app.ui.RegisterSettingsSegment
import com.slideindex.app.ui.SettingsCard
import com.slideindex.app.ui.SettingsRadioGroup
import com.slideindex.app.ui.pickerSegmentedColors
import com.slideindex.app.ui.pickerSegmentedShapes

@Composable
fun waveStyleIconPainter(iconType: Int): Painter = when (iconType) {
    WaveStyle.ICON_TYPE_TRIANGLE -> rememberVectorPainter(Icons.Default.PlayArrow)
    WaveStyle.ICON_TYPE_ANGLE -> rememberVectorPainter(Icons.Default.ArrowForwardIos)
    WaveStyle.ICON_TYPE_ARROW_NEW -> rememberVectorPainter(Icons.Default.Forward)
    else -> rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowForward)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimationStyleColorRow(
    title: String,
    color: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    RegisterSettingsSegment { segmentIndex, segmentCount ->
        SegmentedListItem(
            onClick = onClick,
            enabled = enabled,
            shapes = pickerSegmentedShapes(segmentIndex, segmentCount),
            colors = pickerSegmentedColors(),
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
            },
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimationStyleIconTypePicker(
    selectedType: Int,
    enabled: Boolean,
    onTypeSelected: (Int) -> Unit,
) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                WaveStyle.ICON_TYPE_ARROW,
                WaveStyle.ICON_TYPE_TRIANGLE,
                WaveStyle.ICON_TYPE_ANGLE,
                WaveStyle.ICON_TYPE_ARROW_NEW,
            ).forEach { iconType ->
                val selected = selectedType == iconType
                val background = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                }
                Image(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(background)
                        .clickable(enabled = enabled) { onTypeSelected(iconType) }
                        .padding(10.dp),
                    painter = waveStyleIconPainter(iconType),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White),
                )
            }
        }
    }
}

@Composable
fun AnimationStyleSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = stringResource(R.string.animation_style_customize),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun AnimationStyleCard(
    title: String,
    description: String,
    selected: Boolean,
    preview: @Composable () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(modifier = Modifier.size(80.dp)) {
                preview()
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = description,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun AnimationStylePreviewStage(content: @Composable BoxScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f),
                    MaterialTheme.shapes.small,
                ),
            content = content,
        )
    }
}
