package com.slideindex.app.inspire

import android.graphics.Bitmap
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reference-counted bitmap handle (GestureEVO Inspire ManagedBitmap).
 */
class ManagedBitmap private constructor(
    private val bitmap: Bitmap
) : Closeable {
    private val refCount = AtomicInteger(1)

    fun acquire(): ManagedBitmap? {
        while (true) {
            val current = refCount.get()
            if (current <= 0) return null
            if (refCount.compareAndSet(current, current + 1)) return this
        }
    }

    fun requireBitmap(): Bitmap = bitmap

    override fun close() {
        if (refCount.decrementAndGet() == 0 && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    companion object {
        fun from(bitmap: Bitmap): ManagedBitmap = ManagedBitmap(bitmap)
    }
}
