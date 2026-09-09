package com.slideindex.app.overlay



import android.graphics.Matrix

import android.graphics.Paint

import android.graphics.RadialGradient

import android.graphics.RectF

import android.graphics.Shader

import androidx.compose.ui.geometry.CornerRadius

import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.geometry.Rect

import androidx.compose.ui.geometry.Size

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.drawscope.DrawScope

import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

import androidx.compose.ui.graphics.nativeCanvas

import com.slideindex.app.settings.AppSettings

import com.slideindex.app.settings.FloatingPointerEdgeSide

import com.slideindex.app.util.GestureActionIconBitmap

import kotlin.math.max

import kotlin.math.roundToInt



internal object FloatingPointerEdgeActionsRenderer {

    private const val ICON_DIAMETER_DP = 36f



    fun draw(

        scope: DrawScope,

        settings: AppSettings,

        segments: List<FloatingPointerEdgeActionSegment>,

        previewAlpha: Float,

        density: Float

    ) {

        if (previewAlpha <= 0.001f || segments.none { it.proximity > 0f }) return

        val wedgeWidth = settings.floatingPointerEdgeVisualSizeDp.coerceAtLeast(0f) * density

        val globalOpacity = settings.floatingPointerEdgeVisualOpacity.coerceIn(0, 100) / 100f

        val glowWidth = FloatingPointerEdgeActionsEngine.previewGlowWidthPx(settings, density)

        val showIcon = settings.floatingPointerEdgePreviewShowIcon

        val iconDiameterPx = (ICON_DIAMETER_DP * density).roundToInt().coerceAtLeast(1)

        val iconRadiusPx = iconDiameterPx / 2f

        val iconTint = settings.floatingPointerEdgeVisualColorArgb

        val screenWidth = scope.size.width

        val screenHeight = scope.size.height



        segments.forEach { segment ->

            val strength = (segment.proximity * previewAlpha).coerceIn(0f, 1f)

            if (strength <= 0.001f) return@forEach

            val baseColor = Color(segment.colorArgb)



            if (wedgeWidth > 0f && globalOpacity > 0f) {

                val wedgeColor = baseColor.copy(alpha = globalOpacity * previewAlpha)

                val corner = CornerRadius(wedgeWidth, wedgeWidth)

                scope.drawRoundRect(

                    color = wedgeColor,

                    topLeft = Offset(segment.wedgeRect.left, segment.wedgeRect.top),

                    size = Size(segment.wedgeRect.width, segment.wedgeRect.height),

                    cornerRadius = corner

                )

            }



            if (glowWidth > 0f) {

                val glowRect = segment.glowRect

                val glowPaint = edgeGlowPaint(

                    glowRect = glowRect,

                    side = segment.side,

                    colorArgb = segment.colorArgb,

                    alpha = strength,

                    screenWidth = screenWidth,

                    screenHeight = screenHeight

                )

                val expanded = expandedGlowRect(glowRect)

                scope.drawIntoCanvas { canvas ->

                    canvas.nativeCanvas.drawRect(expanded, glowPaint)

                }

            }



            if (!showIcon) return@forEach

            val eased = decelerate(strength)

            val slideOffset = iconRadiusPx * 1.15f * eased

            val iconCenter = when (segment.side) {

                FloatingPointerEdgeSide.LEFT -> Offset(

                    segment.iconAnchor.x + slideOffset,

                    segment.iconAnchor.y

                )

                FloatingPointerEdgeSide.RIGHT -> Offset(

                    segment.iconAnchor.x - slideOffset,

                    segment.iconAnchor.y

                )

                FloatingPointerEdgeSide.TOP -> Offset(

                    segment.iconAnchor.x,

                    segment.iconAnchor.y + slideOffset

                )

                FloatingPointerEdgeSide.BOTTOM -> Offset(

                    segment.iconAnchor.x,

                    segment.iconAnchor.y - slideOffset

                )

            }

            scope.drawCircle(

                color = Color.White.copy(alpha = strength * previewAlpha * 0.9f),

                radius = iconRadiusPx,

                center = iconCenter

            )

            val bitmap = GestureActionIconBitmap.get(segment.action, iconDiameterPx, iconTint)

            val iconPaint = Paint().apply { alpha = (strength * 255f).roundToInt().coerceIn(0, 255) }

            scope.drawIntoCanvas { canvas ->

                canvas.nativeCanvas.drawBitmap(

                    bitmap,

                    iconCenter.x - bitmap.width / 2f,

                    iconCenter.y - bitmap.height / 2f,

                    iconPaint

                )

            }

        }

    }



    private fun expandedGlowRect(rect: Rect): RectF {

        val width = rect.width

        val height = rect.height

        return RectF(

            rect.left - width,

            rect.top - height,

            rect.right + width,

            rect.bottom + height

        )

    }



    private fun edgeGlowPaint(

        glowRect: Rect,

        side: FloatingPointerEdgeSide,

        colorArgb: Int,

        alpha: Float,

        screenWidth: Float,

        screenHeight: Float

    ): Paint {

        val width = glowRect.width.coerceAtLeast(0.01f)

        val height = glowRect.height.coerceAtLeast(0.01f)

        val centerX = glowRect.center.x

        val centerY = glowRect.center.y

        val radius = max(width, height) / 2f

        val alphaInt = (alpha * 255f).roundToInt().coerceIn(0, 255)

        val startColor = android.graphics.Color.argb(

            alphaInt,

            android.graphics.Color.red(colorArgb),

            android.graphics.Color.green(colorArgb),

            android.graphics.Color.blue(colorArgb)

        )

        val radialGradient = RadialGradient(

            centerX,

            centerY,

            radius,

            startColor,

            android.graphics.Color.TRANSPARENT,

            Shader.TileMode.CLAMP

        )

        val matrix = Matrix()

        when (side) {

            FloatingPointerEdgeSide.TOP -> {

                val scaleY = height / width

                matrix.setScale(1f, scaleY)

                matrix.postTranslate(0f, (glowRect.bottom - glowRect.top) / 2f * scaleY)

            }

            FloatingPointerEdgeSide.BOTTOM -> {

                val scaleY = height / width

                matrix.setScale(1f, scaleY)

                matrix.postTranslate(

                    0f,

                    (-screenHeight * scaleY + screenHeight) - height * scaleY

                )

            }

            FloatingPointerEdgeSide.LEFT -> {

                val scaleX = width / height

                matrix.setScale(scaleX, 1f)

                matrix.postTranslate((glowRect.right - glowRect.left) * scaleX, 0f)

            }

            FloatingPointerEdgeSide.RIGHT -> {

                val scaleX = width / height

                matrix.setScale(scaleX, 1f)

                matrix.postTranslate(

                    (-screenWidth * scaleX + screenWidth) - width * scaleX,

                    0f

                )

            }

        }

        radialGradient.setLocalMatrix(matrix)

        return Paint(Paint.ANTI_ALIAS_FLAG).apply {

            style = Paint.Style.FILL

            shader = radialGradient

        }

    }



    private fun decelerate(value: Float): Float {

        val v = value.coerceIn(0f, 1f)

        return 1f - (1f - v) * (1f - v)

    }

}


