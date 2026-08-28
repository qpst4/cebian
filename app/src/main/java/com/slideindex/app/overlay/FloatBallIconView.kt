package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.settings.FloatBallStyleType
import kotlin.math.roundToInt

/**
 * Unified native float-ball surface for idle + drag (FV FloatIconView-style).
 * Parent positions this view; [bind] only manages appearance and inner layout gravity.
 */
internal class FloatBallIconView(context: Context) : FrameLayout(context) {

    private enum class ContentMode {
        STATIC,
        GIF,
        BUILTIN,
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val staticImage = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val gifView = FloatBallGifView(context).apply {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val builtinAnimView = FloatBallBuiltinAnimView(context).apply {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val gifPlayer = FloatBallGifPlayer()

    private var contentMode = ContentMode.STATIC
    private var boundKey: String? = null
    private var ownedBitmap: Bitmap? = null
    private var slideshowUris: List<String> = emptyList()
    private var slideshowIndex = 0
    private var gifDecodeToken = 0

    private val slideshowRunnable = object : Runnable {
        override fun run() {
            if (contentMode != ContentMode.STATIC || slideshowUris.size <= 1) return
            slideshowIndex = (slideshowIndex + 1) % slideshowUris.size
            showStaticUri(slideshowUris[slideshowIndex], currentOpacity())
            mainHandler.postDelayed(this, SLIDESHOW_INTERVAL_MS)
        }
    }

    private var currentOpacityValue = 1f

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        clipChildren = true
        addView(staticImage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(gifView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(builtinAnimView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        gifPlayer.attach(gifView)
        showContentMode(ContentMode.STATIC)
    }

    fun bind(
        settings: AppSettings,
        activeSide: FloatBallSide,
        styleGeneration: Int,
    ) {
        val density = resources.displayMetrics.density
        val sizePx = (settings.floatBallSizeDp.coerceIn(36f, 72f) * density).roundToInt().coerceAtLeast(1)
        val opacity = settings.floatBallOpacity.coerceIn(0f, 1f)
        currentOpacityValue = opacity
        alpha = opacity

        val key = buildBindKey(settings, activeSide, styleGeneration, sizePx)
        if (key == boundKey) {
            applyChildGravity(settings, activeSide)
            applyCircularClip(sizePx)
            return
        }
        boundKey = key
        applyChildGravity(settings, activeSide)
        applyCircularClip(sizePx)

        when (settings.floatBallStyleType) {
            FloatBallStyleType.ANIMATED_PLANE,
            FloatBallStyleType.ANIMATED_PULSE,
            FloatBallStyleType.ANIMATED_ORBIT,
            -> showBuiltin(settings.floatBallStyleType, opacity)

            FloatBallStyleType.GIF -> showGif(settings, opacity, sizePx)

            FloatBallStyleType.CUSTOM_IMAGE -> {
                stopSlideshow()
                showStaticUri(settings.floatBallCustomImageUri, opacity)
            }

            FloatBallStyleType.SLIDESHOW -> {
                val uris = settings.floatBallSlideshowUris
                if (uris.isEmpty()) {
                    stopSlideshow()
                    showStaticBitmap(FloatBallDragVisualRenderer.render(context, settings), owns = true)
                } else {
                    slideshowUris = uris
                    slideshowIndex = 0
                    showStaticUri(uris.first(), opacity)
                    stopSlideshow()
                    if (uris.size > 1) {
                        mainHandler.postDelayed(slideshowRunnable, SLIDESHOW_INTERVAL_MS)
                    }
                }
            }

            FloatBallStyleType.DEFAULT -> {
                stopSlideshow()
                showStaticBitmap(FloatBallDragVisualRenderer.render(context, settings), owns = true)
            }
        }
    }

    fun setDragging(dragging: Boolean) {
        when (contentMode) {
            ContentMode.GIF -> gifPlayer.setPaused(false)
            ContentMode.BUILTIN -> builtinAnimView.setPaused(false)
            ContentMode.STATIC -> Unit
        }
    }

    fun release() {
        stopSlideshow()
        gifDecodeToken++
        gifPlayer.release()
        builtinAnimView.releaseAnimation()
        releaseOwnedBitmap()
        staticImage.setImageDrawable(null)
        gifView.clearFrame()
        boundKey = null
        slideshowUris = emptyList()
    }

    private fun buildBindKey(
        settings: AppSettings,
        activeSide: FloatBallSide,
        styleGeneration: Int,
        sizePx: Int,
    ): String = buildString {
        append(styleGeneration)
        append('|')
        append(settings.floatBallStyleType.name)
        append('|')
        append(activeSide.name)
        append('|')
        append(sizePx)
        append('|')
        append(settings.themeColorArgb)
        append('|')
        append(settings.floatBallOpacity)
        append('|')
        append(settings.floatBallCustomImageUri)
        append('|')
        append(settings.floatBallGifUri)
        append('|')
        append(settings.floatBallSlideshowUris.joinToString(","))
    }

    private fun currentOpacity(): Float = currentOpacityValue

    private fun showContentMode(mode: ContentMode) {
        contentMode = mode
        staticImage.visibility = if (mode == ContentMode.STATIC) View.VISIBLE else View.GONE
        gifView.visibility = if (mode == ContentMode.GIF) View.VISIBLE else View.GONE
        builtinAnimView.visibility = if (mode == ContentMode.BUILTIN) View.VISIBLE else View.GONE
    }

    private fun showBuiltin(styleType: FloatBallStyleType, opacity: Float) {
        stopSlideshow()
        gifPlayer.release()
        releaseOwnedBitmap()
        showContentMode(ContentMode.BUILTIN)
        builtinAnimView.alpha = opacity
        builtinAnimView.setStyle(styleType)
        builtinAnimView.setPaused(false)
    }

    private fun showGif(settings: AppSettings, opacity: Float, decodePx: Int) {
        stopSlideshow()
        releaseOwnedBitmap()
        builtinAnimView.releaseAnimation()
        showContentMode(ContentMode.GIF)
        gifView.alpha = opacity

        val uri = settings.floatBallGifUri
        if (uri.isBlank() || !FloatBallStyleAssetStore.canRead(context, uri)) {
            showStaticBitmap(FloatBallDragVisualRenderer.render(context, settings), owns = true)
            return
        }

        val token = ++gifDecodeToken
        Thread {
            val decoded = FloatBallGifFrameDecoder.decode(context, uri, decodePx)
            mainHandler.post {
                if (token != gifDecodeToken) {
                    decoded?.recycle()
                    return@post
                }
                if (decoded != null) {
                    FloatBallGifDragSnapshot.update(uri, decodePx, decoded)
                    gifPlayer.setSequence(decoded)
                    gifPlayer.setPaused(false)
                    gifPlayer.start()
                } else {
                    showStaticBitmap(FloatBallDragVisualRenderer.render(context, settings), owns = true)
                }
            }
        }.start()
    }

    private fun showStaticUri(uri: String, opacity: Float) {
        if (uri.isBlank()) {
            releaseOwnedBitmap()
            staticImage.setImageDrawable(null)
            staticImage.alpha = opacity
            showContentMode(ContentMode.STATIC)
            return
        }
        val bitmap = FloatBallImageLoader.loadBitmap(context, uri)
        if (bitmap != null) {
            showStaticBitmap(bitmap, owns = false)
            staticImage.alpha = opacity
        } else {
            releaseOwnedBitmap()
            staticImage.setImageDrawable(null)
            staticImage.alpha = opacity
            showContentMode(ContentMode.STATIC)
        }
    }

    private fun showStaticBitmap(bitmap: Bitmap, owns: Boolean) {
        gifPlayer.release()
        builtinAnimView.releaseAnimation()
        releaseOwnedBitmap()
        ownedBitmap = if (owns) bitmap else null
        staticImage.setImageBitmap(bitmap)
        staticImage.alpha = currentOpacity()
        showContentMode(ContentMode.STATIC)
    }

    private fun releaseOwnedBitmap() {
        ownedBitmap?.recycle()
        ownedBitmap = null
    }

    private fun stopSlideshow() {
        mainHandler.removeCallbacks(slideshowRunnable)
    }

    private fun applyChildGravity(settings: AppSettings, activeSide: FloatBallSide) {
        val gravity = layoutGravity(settings, activeSide)
        listOf(staticImage, gifView, builtinAnimView).forEach { child ->
            val params = child.layoutParams as LayoutParams
            params.gravity = gravity
            child.layoutParams = params
        }
    }

    private fun applyCircularClip(sizePx: Int) {
        val clipper = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, sizePx, sizePx)
            }
        }
        listOf(staticImage, gifView, builtinAnimView).forEach { child ->
            child.clipToOutline = true
            child.outlineProvider = clipper
        }
    }

    private fun layoutGravity(settings: AppSettings, activeSide: FloatBallSide): Int {
        if (settings.floatBallPositionMode == FloatBallPositionMode.CUSTOM) {
            return Gravity.CENTER
        }
        return when (activeSide) {
            FloatBallSide.LEFT -> Gravity.CENTER_VERTICAL or Gravity.START
            FloatBallSide.RIGHT -> Gravity.CENTER_VERTICAL or Gravity.END
        }
    }

    companion object {
        private const val SLIDESHOW_INTERVAL_MS = 3_000L
    }
}
