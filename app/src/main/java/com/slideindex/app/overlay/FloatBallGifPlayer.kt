package com.slideindex.app.overlay

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.ImageView

/**
 * FV-style GIF playback: Handler tick + setImageBitmap, optional full frame cache.
 */
internal class FloatBallGifPlayer(
    looper: Looper = Looper.getMainLooper(),
) {
    private val handler = Handler(looper)
    private var imageView: ImageView? = null
    private var sequence: FloatBallGifFrameDecoder.Sequence? = null
    private var frameIndex = 0
    private var paused = false
    private var streamingBitmap: Bitmap? = null
    private var streamingStartUptimeMs = 0L

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (paused) return
            val view = imageView ?: return
            when (val seq = sequence) {
                is FloatBallGifFrameDecoder.Sequence.Cached -> {
                    if (seq.frames.isEmpty()) return
                    val frame = seq.frames[frameIndex]
                    view.setImageBitmap(frame.bitmap)
                    val delayMs = frame.delayMs
                    frameIndex = (frameIndex + 1) % seq.frames.size
                    handler.postDelayed(this, delayMs.toLong())
                }
                is FloatBallGifFrameDecoder.Sequence.Streaming -> {
                    val elapsed = ((SystemClock.uptimeMillis() - streamingStartUptimeMs) % seq.durationMs)
                        .toInt()
                    streamingBitmap = FloatBallGifFrameDecoder.renderStreamingFrame(
                        streaming = seq,
                        elapsedMs = elapsed,
                        reuse = streamingBitmap,
                    )
                    view.setImageBitmap(streamingBitmap)
                    handler.postDelayed(this, STREAMING_TICK_MS.toLong())
                }
                null -> Unit
            }
        }
    }

    fun attach(view: ImageView) {
        imageView = view
    }

    fun setSequence(seq: FloatBallGifFrameDecoder.Sequence?) {
        stop()
        sequence?.recycle()
        streamingBitmap?.recycle()
        streamingBitmap = null
        sequence = seq
        frameIndex = 0
        streamingStartUptimeMs = SystemClock.uptimeMillis()
        seq?.let { showFirstFrame(it) }
    }

    fun setPaused(pause: Boolean) {
        if (paused == pause) return
        paused = pause
        if (pause) {
            handler.removeCallbacks(tickRunnable)
        } else {
            streamingStartUptimeMs = SystemClock.uptimeMillis()
            start()
        }
    }

    fun start() {
        if (paused || sequence == null) return
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
    }

    fun stop() {
        handler.removeCallbacks(tickRunnable)
    }

    fun release() {
        stop()
        sequence?.recycle()
        sequence = null
        streamingBitmap?.recycle()
        streamingBitmap = null
        imageView = null
    }

    private fun showFirstFrame(seq: FloatBallGifFrameDecoder.Sequence) {
        val view = imageView ?: return
        when (seq) {
            is FloatBallGifFrameDecoder.Sequence.Cached -> {
                val first = seq.frames.firstOrNull()?.bitmap ?: return
                view.setImageBitmap(first)
            }
            is FloatBallGifFrameDecoder.Sequence.Streaming -> {
                streamingBitmap = FloatBallGifFrameDecoder.renderStreamingFrame(
                    streaming = seq,
                    elapsedMs = 0,
                    reuse = streamingBitmap,
                )
                view.setImageBitmap(streamingBitmap)
            }
        }
    }

    companion object {
        internal const val STREAMING_TICK_MS = 33
    }
}
