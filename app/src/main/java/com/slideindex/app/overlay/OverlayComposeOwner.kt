package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.slideindex.app.R

class OverlayComposeOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

object OverlayCompose {
    /** 保留宿主 Context 的 display 关联（AccessibilityService 等），避免 WindowLayoutInfo / UI Context 崩溃。 */
    fun themedContext(context: Context): Context =
        ContextThemeWrapper(context, R.style.Theme_SlideIndex_Transparent)

    fun bindOwners(view: View, owner: OverlayComposeOwner) {
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)
        view.setViewTreeViewModelStoreOwner(owner)
    }

    fun clearViewTreeOwners(view: View) {
        view.setViewTreeLifecycleOwner(null)
        view.setViewTreeSavedStateRegistryOwner(null)
        view.setViewTreeViewModelStoreOwner(null)
    }

    fun createComposeView(context: Context, owner: OverlayComposeOwner): ComposeView {
        return ComposeView(themedContext(context)).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            bindOwners(this, owner)
            // Overlays manage their own owner lifecycle; dispose on window detach instead of
            // ON_DESTROY to avoid races when removeView and destroy() happen close together.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        }
    }

    fun disposeComposeView(view: ComposeView?) {
        if (view == null) return
        runCatching { view.disposeComposition() }
    }
}
