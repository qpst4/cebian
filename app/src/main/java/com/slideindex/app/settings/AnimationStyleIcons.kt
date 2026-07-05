package com.slideindex.app.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter

@Composable
fun waveStyleIconPainter(iconType: Int): Painter = when (iconType) {
    WaveStyle.ICON_TYPE_TRIANGLE -> rememberVectorPainter(Icons.Default.PlayArrow)
    WaveStyle.ICON_TYPE_ANGLE -> rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowForwardIos)
    WaveStyle.ICON_TYPE_ARROW_NEW -> rememberVectorPainter(Icons.AutoMirrored.Filled.Forward)
    else -> rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowForward)
}

@Composable
fun WaveStyle.painterIcon(): Painter = waveStyleIconPainter(iconType)

@Composable
fun CapsuleStyle.painterIcon(): Painter = waveStyleIconPainter(iconType)

@Composable
fun BubbleStyle.painterIcon(): Painter = waveStyleIconPainter(iconType)
