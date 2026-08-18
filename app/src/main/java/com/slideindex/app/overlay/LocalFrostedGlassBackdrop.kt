package com.slideindex.app.overlay

import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun LocalFrostedGlassBackdrop(
    modifier: Modifier = Modifier,
    cornerRadiusPx: Float = 0f,
    blurRadiusPx: Int = 80,
    @ColorInt tintColor: Int = 0x331C1C1E,
    enabled: Boolean = true,
) {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            FrameLayout(ctx).apply {
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, outline: Outline) {
                        outline.setRoundRect(0, 0, v.width, v.height, cornerRadiusPx)
                    }
                }
                clipToOutline = true
                viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        viewTreeObserver.removeOnPreDrawListener(this)
                        setupBackgroundBlur(this@apply, cornerRadiusPx, blurRadiusPx, tintColor)
                        return true
                    }
                })
            }
        },
        update = { view ->
            setupBackgroundBlur(view, cornerRadiusPx, blurRadiusPx, tintColor)
        },
    )
}

internal fun setupBackgroundBlur(view: View, cornerRadiusPx: Float, blurRadiusPx: Int, @ColorInt tintColor: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    runCatching {
        val blurDrawable = view.background?.takeIf {
            it.javaClass.name.contains("BackgroundBlurDrawable")
        } ?: run {
            val d = LocalFrostedGlassDrawable.createBlurDrawable(view) ?: return
            view.background = d
            d
        }
        val setBlurRadius = blurDrawable.javaClass.getMethod("setBlurRadius", java.lang.Integer.TYPE)
        val setCornerRadius = blurDrawable.javaClass.getMethod("setCornerRadius", java.lang.Float.TYPE)
        val setColor = blurDrawable.javaClass.getMethod("setColor", java.lang.Integer.TYPE)
        setBlurRadius.invoke(blurDrawable, blurRadiusPx)
        setCornerRadius.invoke(blurDrawable, cornerRadiusPx)
        setColor.invoke(blurDrawable, tintColor)
    }.onFailure { Log.w("LocalFrostedGlass", "setupBackgroundBlur failed", it) }
}

/**
 * Reusable Canvas-level helper for drawing hardware BackgroundBlurDrawable
 * inside custom View onDraw(canvas) or overlay renderers.
 */
internal class LocalFrostedGlassDrawable(private val viewProvider: () -> View?) {
    constructor(view: View) : this({ view })
    private var blurDrawable: Drawable? = null
    private var lastViewRootImpl: Any? = null
    private val isSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun reset() {
        blurDrawable = null
        lastViewRootImpl = null
    }

    private fun ensureDrawable(): Drawable? {
        if (!isSupported) return null
        val view = viewProvider() ?: return null
        if (!view.isAttachedToWindow) return null
        val currentVri = runCatching {
            val getViewRootImplMethod = View::class.java.getDeclaredMethod("getViewRootImpl")
            getViewRootImplMethod.isAccessible = true
            getViewRootImplMethod.invoke(view)
        }.getOrNull() ?: return null

        if (blurDrawable != null && lastViewRootImpl === currentVri) {
            return blurDrawable
        }

        lastViewRootImpl = currentVri
        blurDrawable = runCatching {
            val createMethod = currentVri.javaClass.getMethod("createBackgroundBlurDrawable")
            createMethod.invoke(currentVri) as? Drawable
        }.getOrNull()
        return blurDrawable
    }

    fun draw(
        canvas: Canvas,
        bounds: RectF,
        cornerRadiusPx: Float,
        blurRadiusPx: Int,
        @ColorInt tintColor: Int,
        alpha: Float = 1f,
    ): Boolean {
        if (!isSupported || bounds.isEmpty || alpha <= 0.001f) return false
        val drawable = ensureDrawable() ?: return false
        return runCatching {
            val setBlurRadius = drawable.javaClass.getMethod("setBlurRadius", java.lang.Integer.TYPE)
            val setCornerRadius = drawable.javaClass.getMethod("setCornerRadius", java.lang.Float.TYPE)
            val setColor = drawable.javaClass.getMethod("setColor", java.lang.Integer.TYPE)
            setBlurRadius.invoke(drawable, blurRadiusPx)
            setCornerRadius.invoke(drawable, cornerRadiusPx)
            setColor.invoke(drawable, tintColor)
            drawable.setBounds(
                bounds.left.toInt(),
                bounds.top.toInt(),
                bounds.right.toInt(),
                bounds.bottom.toInt(),
            )
            drawable.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
            drawable.draw(canvas)
            true
        }.getOrDefault(false)
    }

    companion object {
        fun createBlurDrawable(view: View): Drawable? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
            return runCatching {
                val getViewRootImplMethod = View::class.java.getDeclaredMethod("getViewRootImpl")
                getViewRootImplMethod.isAccessible = true
                val viewRootImpl = getViewRootImplMethod.invoke(view) ?: return null
                val createMethod = viewRootImpl.javaClass.getMethod("createBackgroundBlurDrawable")
                createMethod.invoke(viewRootImpl) as? Drawable
            }.getOrNull()
        }
    }
}
