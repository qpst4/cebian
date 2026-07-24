package com.slideindex.app.overlay

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * FV-style GIF playback: Handler tick + lightweight View invalidate.
 */
internal class FloatBallGifPlayer(
    looper: Looper = Looper.getMainLooper(),
) {
    private val handler = Handler(looper)
    private var gifView: FloatBallGifView? = null
    private var sequence: FloatBallGifFrameDecoder.Sequence? = null
    private var frameIndex = 0
    private var paused = false
    private var streamingStartUptimeMs = 0L

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (paused) return
            val view = gifView ?: return
            when (val seq = sequence) {
                is FloatBallGifFrameDecoder.Sequence.Cached -> {
                    if (seq.frames.isEmpty()) return
                    val frame = seq.frames[frameIndex]
                    view.showCachedFrame(frame.bitmap)
                    val delayMs = frame.delayMs
                    frameIndex = (frameIndex + 1) % seq.frames.size
                    handler.postDelayed(this, delayMs.toLong())
                }
                is FloatBallGifFrameDecoder.Sequence.Streaming -> {
                    val elapsed = ((SystemClock.uptimeMillis() - streamingStartUptimeMs) % seq.durationMs)
                        .toInt()
                    view.showStreamingFrame(
                        movie = seq.movie,
                        elapsedMs = elapsed,
                        outW = seq.width,
                        outH = seq.height,
                    )
                    handler.postDelayed(this, STREAMING_TICK_MS.toLong())
                }
                null -> Unit
            }
        }
    }

    fun attach(view: FloatBallGifView) {
        gifView = view
    }

    fun setSequence(seq: FloatBallGifFrameDecoder.Sequence?) {
        stop()
        sequence?.recycle()
        sequence = seq
        frameIndex = 0
        streamingStartUptimeMs = SystemClock.uptimeMillis()
        seq?.let { showFirstFrame(it) }
    }

    fun setPaused(pause: Boolean) {
        if (paused == pause) return
        paused = pause
        gifView?.setAnimating(!pause && sequence != null)
        if (pause) {
            handler.removeCallbacks(tickRunnable)
        } else {
            streamingStartUptimeMs = SystemClock.uptimeMillis()
            start()
        }
    }

    fun start() {
        if (paused || sequence == null) return
        gifView?.setAnimating(true)
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
    }

    fun stop() {
        handler.removeCallbacks(tickRunnable)
        gifView?.setAnimating(false)
    }

    fun release() {
        stop()
        sequence?.recycle()
        sequence = null
        gifView?.clearFrame()
        gifView = null
    }

    private fun showFirstFrame(seq: FloatBallGifFrameDecoder.Sequence) {
        val view = gifView ?: return
        when (seq) {
            is FloatBallGifFrameDecoder.Sequence.Cached -> {
                val first = seq.frames.firstOrNull()?.bitmap ?: return
                view.showCachedFrame(first)
            }
            is FloatBallGifFrameDecoder.Sequence.Streaming -> {
                view.showStreamingFrame(
                    movie = seq.movie,
                    elapsedMs = 0,
                    outW = seq.width,
                    outH = seq.height,
                )
            }
        }
    }

    companion object {
        internal const val STREAMING_TICK_MS = 66
    }
}
