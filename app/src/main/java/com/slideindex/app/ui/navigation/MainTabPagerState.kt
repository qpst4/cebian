package com.slideindex.app.ui.navigation

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.utils.springAnimateToPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Tab Pager 程序化切页。
 *
 * 动画交给 miuix 的 [springAnimateToPage]（`PagerNavigationSpringSpec`，并在同一处
 * `scroll(MutatePriority.UserInput)` 突变里完成，取消不会把新目标吸附回旧页）。
 * 本类只保留选中页与「正在切页」状态，供底栏与 [MainTabPagerHost] 判断。
 */
@Stable
internal class MainTabPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage &&
            pagerState.currentPage == targetIndex &&
            !isNavigating
        ) {
            return
        }

        navJob?.cancel()
        selectedPage = targetIndex
        isNavigating = true

        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.springAnimateToPage(targetIndex)
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetIndex) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
internal fun rememberMainTabPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): MainTabPagerState = remember(pagerState, coroutineScope) {
    MainTabPagerState(pagerState, coroutineScope)
}
