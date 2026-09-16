package com.slideindex.app.screensearch



import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Check

import androidx.compose.material.icons.filled.KeyboardArrowDown

import androidx.compose.material.icons.filled.KeyboardArrowUp

import androidx.compose.material3.Card

import androidx.compose.material3.CardDefaults

import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.material3.Icon

import androidx.compose.material3.MaterialTheme

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.unit.dp

import com.slideindex.app.R

import com.slideindex.app.ui.miuix.MiuixOverlayComposeLocals



@Composable

fun ScreenSearchIndicatorCard(

    searching: Boolean,

    showSuccess: Boolean,

    onDismissOrStop: () -> Unit,

    onContinueUp: () -> Unit,

    onContinueDown: () -> Unit,

    modifier: Modifier = Modifier,

) {

    MiuixOverlayComposeLocals {

        Column(

            modifier = modifier.padding(vertical = 4.dp),

            verticalArrangement = Arrangement.spacedBy(8.dp),

            horizontalAlignment = Alignment.CenterHorizontally,

        ) {

            if (showSuccess) {

                ScreenSearchIndicatorCircle(onClick = onContinueUp) {

                    Icon(

                        imageVector = Icons.Filled.KeyboardArrowUp,

                        contentDescription = stringResource(R.string.screen_search_continue_up),

                        tint = MaterialTheme.colorScheme.primary,

                    )

                }

            }

            ScreenSearchIndicatorCircle(onClick = onDismissOrStop) {

                when {

                    showSuccess -> Icon(

                        imageVector = Icons.Filled.Check,

                        contentDescription = stringResource(R.string.screen_search_dismiss),

                        tint = MaterialTheme.colorScheme.primary,

                    )

                    searching -> CircularProgressIndicator(

                        modifier = Modifier.size(24.dp),

                        strokeWidth = 2.5.dp,

                        color = MaterialTheme.colorScheme.primary,

                    )

                }

            }

            if (showSuccess) {

                ScreenSearchIndicatorCircle(onClick = onContinueDown) {

                    Icon(

                        imageVector = Icons.Filled.KeyboardArrowDown,

                        contentDescription = stringResource(R.string.screen_search_continue_down),

                        tint = MaterialTheme.colorScheme.primary,

                    )

                }

            }

        }

    }

}



@Composable

private fun ScreenSearchIndicatorCircle(

    onClick: () -> Unit,

    modifier: Modifier = Modifier,

    content: @Composable () -> Unit,

) {

    Card(

        modifier = modifier

            .size(48.dp)

            .clickable(onClick = onClick),

        shape = CircleShape,

        colors = CardDefaults.cardColors(

            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,

        ),

        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),

    ) {

        Box(

            modifier = Modifier.size(48.dp),

            contentAlignment = Alignment.Center,

        ) {

            content()

        }

    }

}


