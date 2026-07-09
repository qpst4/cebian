package com.slideindex.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R

enum class MainBottomNavDestination {
    Home,
    Shake,
    Notification,
    Extension,
}

val MainBottomNavHeight = 64.dp
val MainBottomNavOuterPadding = 16.dp

@Composable
fun FloatingBottomNavBar(
    selected: MainBottomNavDestination,
    onDestinationSelected: (MainBottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            FloatingBottomNavItem(
                selected = selected == MainBottomNavDestination.Home,
                onClick = { onDestinationSelected(MainBottomNavDestination.Home) },
                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                label = stringResource(R.string.main_nav_home),
            )
            FloatingBottomNavItem(
                selected = selected == MainBottomNavDestination.Shake,
                onClick = { onDestinationSelected(MainBottomNavDestination.Shake) },
                icon = { Icon(Icons.Default.ScreenRotation, contentDescription = null) },
                label = stringResource(R.string.main_nav_shake),
            )
            FloatingBottomNavItem(
                selected = selected == MainBottomNavDestination.Notification,
                onClick = { onDestinationSelected(MainBottomNavDestination.Notification) },
                icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                label = stringResource(R.string.main_nav_notification),
            )
            FloatingBottomNavItem(
                selected = selected == MainBottomNavDestination.Extension,
                onClick = { onDestinationSelected(MainBottomNavDestination.Extension) },
                icon = { Icon(Icons.Default.Widgets, contentDescription = null) },
                label = stringResource(R.string.main_nav_extension),
            )
        }
    }
}

@Composable
private fun RowScope.FloatingBottomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                icon()
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
    }
}
