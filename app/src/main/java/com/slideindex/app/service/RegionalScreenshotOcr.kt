package com.slideindex.app.service



import android.accessibilityservice.AccessibilityService

import android.content.Context

import android.graphics.Bitmap

import android.graphics.Rect

import android.hardware.HardwareBuffer

import android.os.Build

import android.os.SystemClock

import android.util.Log

import com.slideindex.app.ocr.OcrDependencyAccess

import com.slideindex.app.overlay.FloatBallOcrRegions

import com.slideindex.app.perf.PickPerf

import java.util.concurrent.Executors

import kotlin.coroutines.resume

import kotlinx.coroutines.suspendCancellableCoroutine



/**

 * API 30+ regional screenshot via [AccessibilityService.takeScreenshot], then on-device OCR.

 */

object RegionalScreenshotOcr {

    private const val TAG = "RegionalScreenshotOcr"

    private val screenshotExecutor = Executors.newSingleThreadExecutor { runnable ->

        Thread(runnable, "A11yScreenshot").apply { isDaemon = true }

    }



    suspend fun captureRectBitmap(

        service: AccessibilityService,

        screenRect: Rect,

    ): Bitmap? {

        val (screenWidth, screenHeight) = logicalScreenSizePx(service)

        val fullBitmap = captureDisplayBitmap(service) ?: return null

        return try {

            cropBitmap(

                fullBitmap = fullBitmap,

                screenRect = screenRect,

                screenWidth = screenWidth,

                screenHeight = screenHeight,

            )

        } finally {

            fullBitmap.recycle()

        }

    }



    suspend fun recognizeInRect(

        service: AccessibilityService,

        context: Context,

        screenRect: Rect,

        modelId: String,

    ): String? {

        if (modelId.isBlank()) return null

        val cropped = captureRectBitmap(service, screenRect) ?: return null

        return try {

            recognizeBitmapPublic(context, modelId, cropped)?.trim()?.takeIf { it.isNotEmpty() }

        } finally {

            cropped.recycle()

        }

    }



    suspend fun recognizeBitmapPublic(

        context: Context,

        modelId: String,

        bitmap: Bitmap,

    ): String? {

        if (modelId.isBlank()) return null

        val service = OcrDependencyAccess.inferenceService(context) ?: return null

        return try {

            service.recognizeBitmap(modelId, bitmap)

        } catch (error: Throwable) {

            Log.w(TAG, "ocr failed", error)

            null

        }

    }



    suspend fun captureDisplayBitmapPublic(service: AccessibilityService): Bitmap? =

        captureDisplayBitmap(service)



    private fun logicalScreenSizePx(service: AccessibilityService): Pair<Int, Int> {

        return FloatBallOcrRegions.accessibilityScreenSizePx(service)

    }



    private fun cropBitmap(

        fullBitmap: Bitmap,

        screenRect: Rect,

        screenWidth: Int,

        screenHeight: Int,

    ): Bitmap? {

        val paddedRect = FloatBallOcrRegions.padScreenRect(screenRect)

        val mapped = FloatBallOcrRegions.mapScreenRectToBitmap(

            screenRect = paddedRect,

            screenWidth = screenWidth,

            screenHeight = screenHeight,

            bitmapWidth = fullBitmap.width,

            bitmapHeight = fullBitmap.height,

        )

        val cropRect = FloatBallOcrRegions.clampToScreen(

            mapped,

            fullBitmap.width,

            fullBitmap.height,

        )

        if (cropRect.width() <= 0 || cropRect.height() <= 0) return null

        return Bitmap.createBitmap(

            fullBitmap,

            cropRect.left,

            cropRect.top,

            cropRect.width(),

            cropRect.height(),

        )

    }



    private suspend fun captureDisplayBitmap(service: AccessibilityService): Bitmap? {

        val start = SystemClock.elapsedRealtime()

        PickPerf.mark("a11y_screenshot_start")

        val bitmap = suspendCancellableCoroutine { continuation ->

            service.takeScreenshot(

                android.view.Display.DEFAULT_DISPLAY,

                screenshotExecutor,

                object : AccessibilityService.TakeScreenshotCallback {

                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {

                        if (!continuation.isActive) {

                            closeHardwareBuffer(result.hardwareBuffer)

                            return

                        }

                        try {

                            val hardware = result.hardwareBuffer

                            val colorSpace = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                                result.colorSpace

                            } else {

                                null

                            }

                            val wrapped = Bitmap.wrapHardwareBuffer(hardware, colorSpace)

                            val software = wrapped?.copy(Bitmap.Config.ARGB_8888, false)

                            wrapped?.recycle()

                            continuation.resume(software)

                        } catch (error: Throwable) {

                            Log.w(TAG, "decode screenshot failed", error)

                            continuation.resume(null)

                        } finally {

                            closeHardwareBuffer(result.hardwareBuffer)

                        }

                    }



                    override fun onFailure(errorCode: Int) {

                        Log.w(TAG, "takeScreenshot failed: $errorCode")

                        if (continuation.isActive) continuation.resume(null)

                    }

                },

            )

        }

        PickPerf.markStepDuration("a11y_screenshot_end", start, "bitmap=${bitmap != null}")

        return bitmap

    }



    private fun closeHardwareBuffer(buffer: HardwareBuffer?) {
        runCatching { buffer?.close() }
    }

}


