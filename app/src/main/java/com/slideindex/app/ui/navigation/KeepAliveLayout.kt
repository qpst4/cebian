package com.slideindex.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout

@Composable
fun KeepAliveLayout(
    modifier: Modifier = Modifier,
    active: Boolean,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier,
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (active) {
                placeables.forEach { it.placeRelative(0, 0) }
            }
        }
    }
}
