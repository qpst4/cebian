package com.slideindex.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Launcher icons loaded during [AppRepository.loadApps] on a background thread.
 * Overlay code rasterizes to small bitmaps without calling PackageManager on the UI thread.
 */
@Singleton
class AppLaunchIconCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val pm get() = context.packageManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val drawableCache = object : LruCache<String, Drawable>(
        (Runtime.getRuntime().maxMemory() / 1024 / 32).toInt().coerceIn(64, 384),
    ) {}

    private val bitmapCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 16).toInt().coerceIn(96, 512),
    ) {}

    fun clear() {
        drawableCache.evictAll()
        bitmapCache.evictAll()
    }

    fun retainPackages(packageNames: Collection<String>) {
        val keep = packageNames.toSet()
        drawableCache.snapshot().keys.filter { it !in keep }.forEach { drawableCache.remove(it) }
        bitmapCache.evictAll()
    }

    fun loadDrawable(applicationInfo: ApplicationInfo) {
        val pkg = applicationInfo.packageName
        if (drawableCache.get(pkg) != null) return
        runCatching {
            val raw = pm.getApplicationIcon(applicationInfo)
            val drawable = raw.constantState?.newDrawable()?.mutate() ?: raw.mutate()
            drawableCache.put(pkg, drawable)
        }
    }

    fun loadDrawable(packageName: String) {
        if (drawableCache.get(packageName) != null) return
        runCatching {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            loadDrawable(appInfo)
        }
    }

    /** Returns a cached launcher icon without touching PackageManager. */
    fun peekDrawable(packageName: String): Drawable? {
        val cached = drawableCache.get(packageName) ?: return null
        return cached.constantState?.newDrawable()?.mutate() ?: cached.mutate()
    }

    /** Loads from PackageManager when missing, then returns a fresh drawable instance. */
    fun drawableFor(packageName: String): Drawable? {
        if (drawableCache.get(packageName) == null) {
            loadDrawable(packageName)
        }
        return peekDrawable(packageName)
    }

    fun bitmapFor(packageName: String, sizePx: Int): Bitmap {
        val size = sizePx.coerceAtLeast(1)
        val key = bitmapKey(packageName, size)
        bitmapCache.get(key)?.let { return it }
        if (drawableCache.get(packageName) == null) {
            loadDrawable(packageName)
        }
        val drawable = drawableCache.get(packageName) ?: ColorDrawable(0)
        val bitmap = rasterize(drawable, size)
        bitmapCache.put(key, bitmap)
        return bitmap
    }

    fun warmBitmapsAsync(packageNames: Collection<String>, sizePx: Int) {
        if (packageNames.isEmpty()) return
        scope.launch {
            warmBitmaps(packageNames, sizePx)
        }
    }

    suspend fun warmBitmaps(packageNames: Collection<String>, sizePx: Int) {
        val size = sizePx.coerceAtLeast(1)
        withContext(Dispatchers.Default) {
            packageNames.forEach { pkg ->
                bitmapFor(pkg, size)
            }
        }
    }

    private fun rasterize(drawable: Drawable, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val d = drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
        d.setBounds(0, 0, size, size)
        d.draw(canvas)
        return bitmap
    }

    private fun bitmapKey(packageName: String, sizePx: Int): String = "$packageName\u0000$sizePx"
}
