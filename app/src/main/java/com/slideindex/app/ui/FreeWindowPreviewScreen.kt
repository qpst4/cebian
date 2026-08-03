package com.slideindex.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FreeWindowPreviewScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSave: (widthFraction: Float, heightFraction: Float, leftFraction: Float, topFraction: Float) -> Unit,
) {
    var widthFraction by remember(settings) { mutableFloatStateOf(settings.freeWindowWidthFraction) }
    var heightFraction by remember(settings) { mutableFloatStateOf(settings.freeWindowHeightFraction) }
    var leftFraction by remember(settings) { mutableFloatStateOf(settings.freeWindowLeftFraction) }
    var topFraction by remember(settings) { mutableFloatStateOf(settings.freeWindowTopFraction) }

    SettingsScreenScaffold(
        title = stringResource(R.string.free_window_preview_title),
        onBack = onBack,
        scrollContent = false,
        bottomBar = {
            Button(
                onClick = {
                    onSave(widthFraction, heightFraction, leftFraction, topFraction)
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.cd_action_confirm))
                Text(
                    text = stringResource(R.string.free_window_preview_save),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        ) {
            val frameWidthPx = constraints.maxWidth.toFloat()
            val frameHeightPx = constraints.maxHeight.toFloat()
            val density = LocalDensity.current

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Text(
                    text = stringResource(R.string.free_window_portrait_preview),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val windowWidthPx = frameWidthPx * widthFraction
                val windowHeightPx = frameHeightPx * heightFraction
                val maxLeftPx = (frameWidthPx - windowWidthPx).coerceAtLeast(0f)
                val maxTopPx = (frameHeightPx - windowHeightPx).coerceAtLeast(0f)
                val offsetXPx = (leftFraction * frameWidthPx).coerceIn(0f, maxLeftPx)
                val offsetYPx = (topFraction * frameHeightPx).coerceIn(0f, maxTopPx)

                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetXPx.roundToInt(), offsetYPx.roundToInt()) }
                        .size(
                            width = with(density) { windowWidthPx.toDp() },
                            height = with(density) { windowHeightPx.toDp() },
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x334285F4))
                        .border(2.dp, Color(0xFF4285F4), RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val wPx = widthFraction * frameWidthPx
                                val hPx = heightFraction * frameHeightPx
                                val maxLeft = (frameWidthPx - wPx).coerceAtLeast(0f)
                                val maxTop = (frameHeightPx - hPx).coerceAtLeast(0f)
                                val newLeftPx = (leftFraction * frameWidthPx + dragAmount.x)
                                    .coerceIn(0f, maxLeft)
                                val newTopPx = (topFraction * frameHeightPx + dragAmount.y)
                                    .coerceIn(0f, maxTop)
                                leftFraction = newLeftPx / frameWidthPx
                                topFraction = newTopPx / frameHeightPx
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${(widthFraction * 100).roundToInt()}% × ${(heightFraction * 100).roundToInt()}%",
                        color = Color(0xFF1565C0),
                        fontWeight = FontWeight.SemiBold,
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF4285F4))
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val newWidth = (widthFraction * frameWidthPx + dragAmount.x)
                                        .coerceIn(frameWidthPx * 0.35f, frameWidthPx * 0.95f)
                                    val newHeight = (heightFraction * frameHeightPx + dragAmount.y)
                                        .coerceIn(frameHeightPx * 0.35f, frameHeightPx * 0.9f)
                                    widthFraction = newWidth / frameWidthPx
                                    heightFraction = newHeight / frameHeightPx
                                    leftFraction = leftFraction.coerceIn(
                                        0f,
                                        ((frameWidthPx - newWidth) / frameWidthPx).coerceAtLeast(0f),
                                    )
                                    topFraction = topFraction.coerceIn(
                                        0f,
                                        ((frameHeightPx - newHeight) / frameHeightPx).coerceAtLeast(0f),
                                    )
                                }
                            },
                    )
                }
            }
        }
    }
}
