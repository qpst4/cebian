package com.slideindex.app.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavReselectScrollEffect(
    reselectCount: Int,
    scrollState: ScrollState,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    LaunchedEffect(reselectCount) {
        if (reselectCount > 0) {
            scrollState.animateScrollTo(0)
            scrollBehavior?.let { behavior ->
                animate(
                    initialValue = behavior.state.heightOffset,
                    targetValue = 0f,
                ) { value, _ ->
                    behavior.state.heightOffset = value
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavReselectScrollEffect(
    reselectCount: Int,
    listState: LazyListState,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    LaunchedEffect(reselectCount) {
        if (reselectCount > 0) {
            listState.animateScrollToItem(0)
            scrollBehavior?.let { behavior ->
                animate(
                    initialValue = behavior.state.heightOffset,
                    targetValue = 0f,
                ) { value, _ ->
                    behavior.state.heightOffset = value
                }
            }
        }
    }
}
