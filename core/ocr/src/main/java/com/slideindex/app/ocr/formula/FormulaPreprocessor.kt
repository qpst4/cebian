package com.slideindex.app.ocr.formula

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * 图像预处理工具
 * 针对数学公式识别模型将 Bitmap 处理为标准 NCHW FloatBuffer (标准化与 Padding)
 */
object FormulaPreprocessor {

    // 标准输入图像高度 (Paddle 公式识别常见标准，如 192 或 224)
    const val TARGET_HEIGHT = 192
    const val MAX_WIDTH = 768

    // ImageNet 标准归一化均值与方差
    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    /**
     * 等比缩放到固定高度，并限制最大宽度，不足部分 Padding 填充为白色
     */
    fun preprocessToFloatBuffer(bitmap: Bitmap): PreprocessedImage {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        // 计算等比例缩放后的宽与高
        val scale = TARGET_HEIGHT.toFloat() / originalHeight.toFloat()
        var newWidth = (originalWidth * scale).toInt().coerceAtLeast(1)
        if (newWidth > MAX_WIDTH) {
            newWidth = MAX_WIDTH
        }

        // 创建标准画板 (高度 TARGET_HEIGHT，宽度 newWidth)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, TARGET_HEIGHT, true)

        // 转换为 CHW 顺序的 FloatBuffer (1, 3, TARGET_HEIGHT, newWidth)
        val bufferSize = 1 * 3 * TARGET_HEIGHT * newWidth * 4 // float 为 4 字节
        val byteBuffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
        val floatBuffer = byteBuffer.asFloatBuffer()

        val pixels = IntArray(newWidth * TARGET_HEIGHT)
        scaledBitmap.getPixels(pixels, 0, newWidth, 0, 0, newWidth, TARGET_HEIGHT)

        // 提取 R, G, B 三通道并归一化
        val channelSize = newWidth * TARGET_HEIGHT

        // R 通道
        for (i in pixels.indices) {
            val r = (pixels[i] shr 16 and 0xFF) / 255.0f
            floatBuffer.put(i, (r - MEAN[0]) / STD[0])
        }
        // G 通道
        for (i in pixels.indices) {
            val g = (pixels[i] shr 8 and 0xFF) / 255.0f
            floatBuffer.put(channelSize + i, (g - MEAN[1]) / STD[1])
        }
        // B 通道
        for (i in pixels.indices) {
            val b = (pixels[i] and 0xFF) / 255.0f
            floatBuffer.put(channelSize * 2 + i, (b - MEAN[2]) / STD[2])
        }

        return PreprocessedImage(
            floatBuffer = floatBuffer,
            batchSize = 1L,
            channels = 3L,
            height = TARGET_HEIGHT.toLong(),
            width = newWidth.toLong()
        )
    }

    data class PreprocessedImage(
        val floatBuffer: FloatBuffer,
        val batchSize: Long,
        val channels: Long,
        val height: Long,
        val width: Long
    )
}
