package com.slideindex.app.ui.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransition

/**
 * 与 miuix-nav 注入的 entry [ViewModelStore] 绑定的 [SavedStateRegistryOwner]，
 * 并通过 [HasDefaultViewModelProviderFactory] 向 [androidx.lifecycle.viewmodel.compose.viewModel]
 * / [androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel] 提供 SavedState [CreationExtras]。
 */
private class NavEntrySavedStateViewModelStoreOwner(
    lifecycleOwner: LifecycleOwner,
    override val viewModelStore: ViewModelStore,
    private val application: Application?,
) : ViewModelStoreOwner,
    SavedStateRegistryOwner,
    HasDefaultViewModelProviderFactory,
    LifecycleOwner by lifecycleOwner {
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras().apply {
            set(SAVED_STATE_REGISTRY_OWNER_KEY, this@NavEntrySavedStateViewModelStoreOwner)
            set(VIEW_MODEL_STORE_OWNER_KEY, this@NavEntrySavedStateViewModelStoreOwner)
            application?.let { set(APPLICATION_KEY, it) }
        }

    init {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        enableSavedStateHandles()
    }
}

/**
 * 在 miuix-nav entry 内为 Hilt ViewModel 补齐 SavedState 作用域。
 * 复用 miuix-nav 已有的 entry [ViewModelStore] 与 [LifecycleOwner]，不另建导航栈。
 */
@Composable
fun NavEntryHiltScope(content: @Composable () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val entryStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "NavEntryHiltScope must run inside miuix-nav NavDisplay entry content"
    }
    val application = LocalContext.current.applicationContext as? Application
    val owner = remember(lifecycleOwner, entryStoreOwner, application) {
        NavEntrySavedStateViewModelStoreOwner(
            lifecycleOwner = lifecycleOwner,
            viewModelStore = entryStoreOwner.viewModelStore,
            application = application,
        )
    }

    CompositionLocalProvider(
        LocalViewModelStoreOwner provides owner,
        LocalSavedStateRegistryOwner provides owner,
        content = content,
    )
}

/**
 * 与 [NavEntryBuilder.entry] 相同，但在 entry 内容外包 [NavEntryHiltScope] 以支持 [hiltViewModel]。
 */
inline fun <reified T : NavKey> NavEntryBuilder.hiltEntry(
    noinline contentKey: ((T) -> Any)? = null,
    transition: NavTransition? = null,
    swipeDismiss: NavSwipeDirection? = null,
    noinline content: @Composable (T) -> Unit,
) {
    entry<T>(
        contentKey = contentKey,
        transition = transition,
        swipeDismiss = swipeDismiss,
    ) { key ->
        NavEntryHiltScope {
            content(key)
        }
    }
}
