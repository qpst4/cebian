package com.slideindex.app.ui.navigation

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransition

/** 页内横移返回方向；[MainTabNavStackSingle] 注册 entry 前写入。 */
object NavEntrySwipeDismissScope {
    var current: NavSwipeDirection? = null
}

/**
 * 与 [NavEntryBuilder.entry] 相同，只是默认用 [NavEntrySwipeDismissScope.current]
 * 作为页内横移返回方向。
 *
 * miuix 0.9.4 起 `NavDisplay` 会为每个 entry 装配带 SavedState CreationExtras 与
 * ViewModelFactory 的 `ViewModelStoreOwner`（上游 `rememberNavEntryViewModelStoreOwner`），
 * 因此 `viewModel()`、`hiltViewModel()` 与 `SavedStateHandle` 在 entry 内开箱可用；
 * 旧版手工补 SavedState 作用域的 NavEntryHiltScope 已删除。
 */
inline fun <reified T : NavKey> NavEntryBuilder.hiltEntry(
    noinline contentKey: ((T) -> Any)? = null,
    transition: NavTransition? = null,
    swipeDismiss: NavSwipeDirection? = NavEntrySwipeDismissScope.current,
    noinline content: @Composable (T) -> Unit,
) {
    entry<T>(
        contentKey = contentKey,
        transition = transition,
        swipeDismiss = swipeDismiss,
    ) { key ->
        content(key)
    }
}
