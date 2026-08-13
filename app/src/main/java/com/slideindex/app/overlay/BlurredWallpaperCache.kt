package com.slideindex.app.overlay

/*
 * Portions derived from FanFreeform / Hyper手势 (https://github.com/oxohang/FanFreeform)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import android.graphics.RectF
import android.util.Log
import com.slideindex.app.service.RegionalScreenshotOcr
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.LinkedHashMap

/**
 * Blurred background for honeycomb overlay, captured via accessibility screenshot
 * before the overlay is attached (current screen content, then blurred).
 */
internal object BlurredWallpaperCache {
    private const val TAG = "HoneycombWallpaper"
    private const val DOWNSAMPLE = 6
    private const val MAX_ENTRIES = 2
    private val lock = Any()
    private val cache = LinkedHashMap<Key, Bitmap>(MAX_ENTRIES, 0.75f, true)
    private val waiters = LinkedHashMap<Key, MutableList<WeakReference<Callback>>>()
    private var generation = 0

    fun interface Callback {
        /** Null when accessibility screenshot / blur preparation failed. */
        fun onReady(bitmap: Bitmap?)
    }

    @JvmStatic
    fun captureFromDisplay(
        service: AccessibilityService,
        context: Context,
        blurDp: Int,
        callback: Callback,
    ): Bitmap? {
        val appContext = context.applicationContext ?: context
        val width = downsampleWidth(appContext)
        val height = downsampleHeight(appContext)
        val radius = blurRadiusForDp(blurDp)
        val key: Key
        synchronized(lock) {
            key = Key(generation, width, height, radius)
            val cached = cache[key]
            if (cached != null && !cached.isRecycled) return cached
            val callbacks = waiters[key]
            if (callbacks != null) {
                callbacks.add(WeakReference(callback))
                return null
            }
            val list = ArrayList<WeakReference<Callback>>()
            list.add(WeakReference(callback))
            waiters[key] = list
        }
        RegionalScreenshotOcr.captureDisplayBitmapAsync(service) { fullBitmap ->
            var result: Bitmap? = null
            try {
                if (fullBitmap != null) {
                    result = renderAndBlur(fullBitmap, width, height, radius)
                } else {
                    Log.w(TAG, "Accessibility screenshot unavailable; blur background will use dim mask only")
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Cannot prepare blurred honeycomb background", error)
            } finally {
                fullBitmap?.recycle()
            }
            dispatch(key, result)
        }
        return null
    }

    fun clear() {
        synchronized(lock) {
            generation++
            cache.clear()
            waiters.clear()
        }
    }

    private fun renderAndBlur(
        source: Bitmap,
        width: Int,
        height: Int,
        radius: Int,
    ): Bitmap {
        val result = createBitmap(width, height)
        val canvas = Canvas(result)
        canvas.drawBitmap(
            source,
            null,
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            null,
        )
        if (radius > 0) {
            blur(result, radius, 2)
        }
        return result
    }

    private fun blurRadiusForDp(blurDp: Int): Int =
        if (blurDp <= 0) 0 else maxOf(1, blurDp / DOWNSAMPLE)

    private fun dispatch(key: Key, result: Bitmap?) {
        val callbacks: List<WeakReference<Callback>>?
        synchronized(lock) {
            callbacks = waiters.remove(key)
            if (result != null && key.generation == generation) {
                cache[key] = result
                while (cache.size > MAX_ENTRIES) {
                    val oldest = cache.keys.iterator().next()
                    cache.remove(oldest)
                }
            }
        }
        if (callbacks == null) return
        for (reference in callbacks) {
            reference.get()?.onReady(result)
        }
    }

    private fun downsampleWidth(context: Context): Int =
        maxOf(48, context.resources.displayMetrics.widthPixels / DOWNSAMPLE)

    private fun downsampleHeight(context: Context): Int =
        maxOf(96, context.resources.displayMetrics.heightPixels / DOWNSAMPLE)

    private fun blur(bitmap: Bitmap, radius: Int, iterations: Int) {
        val width = bitmap.width
        val height = bitmap.height
        val source = IntArray(width * height)
        val target = IntArray(source.size)
        bitmap.getPixels(source, 0, width, 0, 0, width, height)
        for (iteration in 0 until iterations) {
            horizontal(source, target, width, height, radius)
            vertical(target, source, width, height, radius)
        }
        bitmap.setPixels(source, 0, width, 0, 0, width, height)
    }

    private fun horizontal(
        source: IntArray,
        target: IntArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        val samples = radius * 2 + 1
        for (y in 0 until height) {
            val row = y * width
            var a = 0L
            var r = 0L
            var g = 0L
            var b = 0L
            for (offset in -radius..radius) {
                val color = source[row + clamp(offset, 0, width - 1)]
                a += color ushr 24
                r += (color shr 16) and 255
                g += (color shr 8) and 255
                b += color and 255
            }
            for (x in 0 until width) {
                target[row + x] = color(a, r, g, b, samples)
                val remove = source[row + clamp(x - radius, 0, width - 1)]
                val add = source[row + clamp(x + radius + 1, 0, width - 1)]
                a += (add ushr 24) - (remove ushr 24)
                r += ((add shr 16) and 255) - ((remove shr 16) and 255)
                g += ((add shr 8) and 255) - ((remove shr 8) and 255)
                b += (add and 255) - (remove and 255)
            }
        }
    }

    private fun vertical(
        source: IntArray,
        target: IntArray,
        width: Int,
        height: Int,
        radius: Int,
    ) {
        val samples = radius * 2 + 1
        for (x in 0 until width) {
            var a = 0L
            var r = 0L
            var g = 0L
            var b = 0L
            for (offset in -radius..radius) {
                val color = source[clamp(offset, 0, height - 1) * width + x]
                a += color ushr 24
                r += (color shr 16) and 255
                g += (color shr 8) and 255
                b += color and 255
            }
            for (y in 0 until height) {
                target[y * width + x] = color(a, r, g, b, samples)
                val remove = source[clamp(y - radius, 0, height - 1) * width + x]
                val add = source[clamp(y + radius + 1, 0, height - 1) * width + x]
                a += (add ushr 24) - (remove ushr 24)
                r += ((add shr 16) and 255) - ((remove shr 16) and 255)
                g += ((add shr 8) and 255) - ((remove shr 8) and 255)
                b += (add and 255) - (remove and 255)
            }
        }
    }

    private fun color(a: Long, r: Long, g: Long, b: Long, samples: Int): Int =
        ((a / samples).toInt() shl 24) or
            ((r / samples).toInt() shl 16) or
            ((g / samples).toInt() shl 8) or
            (b / samples).toInt()

    private fun clamp(value: Int, minimum: Int, maximum: Int): Int =
        maxOf(minimum, minOf(maximum, value))

    private data class Key(
        val generation: Int,
        val width: Int,
        val height: Int,
        val radius: Int,
    )
}
