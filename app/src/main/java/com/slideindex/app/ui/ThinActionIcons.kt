package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object ThinActionIcons {
    private const val STROKE_WIDTH = 1.5f
    private val strokeBrush = SolidColor(Color.White)

    private fun createThinIcon(
        name: String,
        builder: ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(builder).build()

    val Block: ImageVector by lazy {
        createThinIcon("ThinBlock") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 4.5f)
                curveTo(7.86f, 4.5f, 4.5f, 7.86f, 4.5f, 12f)
                curveTo(4.5f, 16.14f, 7.86f, 19.5f, 12f, 19.5f)
                curveTo(16.14f, 19.5f, 19.5f, 16.14f, 19.5f, 12f)
                curveTo(19.5f, 7.86f, 16.14f, 4.5f, 12f, 4.5f)
                close()
                moveTo(6.7f, 6.7f)
                lineTo(17.3f, 17.3f)
            }
        }
    }

    val SortByAlpha: ImageVector by lazy {
        createThinIcon("ThinSortByAlpha") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 左上 A
                moveTo(4f, 9f)
                lineTo(6.5f, 4f)
                lineTo(9f, 9f)
                moveTo(5.2f, 7f)
                lineTo(7.8f, 7f)
                // 左下 Z
                moveTo(4f, 13f)
                lineTo(9f, 13f)
                lineTo(4f, 19f)
                lineTo(9f, 19f)
                // 右侧贯穿双向箭头
                moveTo(16.5f, 4f)
                lineTo(16.5f, 20f)
                moveTo(13.5f, 7f)
                lineTo(16.5f, 4f)
                lineTo(19.5f, 7f)
                moveTo(13.5f, 17f)
                lineTo(16.5f, 20f)
                lineTo(19.5f, 17f)
            }
        }
    }

    val Apps: ImageVector by lazy {
        createThinIcon("ThinApps") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 4.5f); lineTo(8.5f, 4.5f); lineTo(8.5f, 8.5f); lineTo(4.5f, 8.5f); close()
                moveTo(11.5f, 4.5f); lineTo(15.5f, 4.5f); lineTo(15.5f, 8.5f); lineTo(11.5f, 8.5f); close()
                moveTo(18.5f, 4.5f); lineTo(19.5f, 4.5f); lineTo(19.5f, 8.5f); lineTo(18.5f, 8.5f); close()
                moveTo(4.5f, 11.5f); lineTo(8.5f, 11.5f); lineTo(8.5f, 15.5f); lineTo(4.5f, 15.5f); close()
                moveTo(11.5f, 11.5f); lineTo(15.5f, 11.5f); lineTo(15.5f, 15.5f); lineTo(11.5f, 15.5f); close()
                moveTo(18.5f, 11.5f); lineTo(19.5f, 11.5f); lineTo(19.5f, 15.5f); lineTo(18.5f, 15.5f); close()
                moveTo(4.5f, 18.5f); lineTo(8.5f, 18.5f); lineTo(8.5f, 19.5f); lineTo(4.5f, 19.5f); close()
                moveTo(11.5f, 18.5f); lineTo(15.5f, 18.5f); lineTo(15.5f, 19.5f); lineTo(11.5f, 19.5f); close()
                moveTo(18.5f, 18.5f); lineTo(19.5f, 18.5f); lineTo(19.5f, 19.5f); lineTo(18.5f, 19.5f); close()
            }
        }
    }

    /** 1:1 官方蜂窝六边形网格 (Material Honeycomb) */
    val Hive: ImageVector by lazy {
        createThinIcon("ThinHive") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 1. 中心六边形
                moveTo(10.95f, 14f); lineTo(13.05f, 14f); lineTo(14.18f, 12f); lineTo(13.05f, 10f); lineTo(10.95f, 10f); lineTo(9.82f, 12f); close()
                // 2. 正上六边形
                moveTo(10.95f, 8f); lineTo(13.05f, 8f); lineTo(14.18f, 6f); lineTo(13.05f, 4f); lineTo(10.95f, 4f); lineTo(9.82f, 6f); close()
                // 3. 正下六边形
                moveTo(10.95f, 20f); lineTo(13.05f, 20f); lineTo(14.18f, 18f); lineTo(13.05f, 16f); lineTo(10.95f, 16f); lineTo(9.82f, 18f); close()
                // 4. 左上六边形
                moveTo(5.92f, 11f); lineTo(8.03f, 11f); lineTo(9.15f, 9f); lineTo(8.03f, 7f); lineTo(5.92f, 7f); lineTo(4.79f, 9f); close()
                // 5. 左下六边形
                moveTo(5.92f, 17f); lineTo(8.03f, 17f); lineTo(9.15f, 15f); lineTo(8.03f, 13f); lineTo(5.92f, 13f); lineTo(4.79f, 15f); close()
                // 6. 右上六边形
                moveTo(15.97f, 11f); lineTo(18.08f, 11f); lineTo(19.21f, 9f); lineTo(18.08f, 7f); lineTo(15.97f, 7f); lineTo(14.85f, 9f); close()
                // 7. 右下六边形
                moveTo(15.97f, 17f); lineTo(18.08f, 17f); lineTo(19.21f, 15f); lineTo(18.08f, 13f); lineTo(15.97f, 13f); lineTo(14.85f, 15f); close()
            }
        }
    }

  /** 3D 球 / 全息启动器 */
    val Globe: ImageVector by lazy {
        createThinIcon("ThinGlobe") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 21f)
                arcTo(9f, 9f, 0f, true, false, 12f, 3f)
                arcTo(9f, 9f, 0f, true, false, 12f, 21f)
                close()
                moveTo(3.6f, 9f)
                lineTo(20.4f, 9f)
                moveTo(3.6f, 15f)
                lineTo(20.4f, 15f)
                moveTo(12f, 3f)
                arcTo(5.4f, 9f, 0f, false, true, 12f, 21f)
                moveTo(12f, 3f)
                arcTo(5.4f, 9f, 0f, false, false, 12f, 21f)
            }
        }
    }

    val ViewCarousel: ImageVector by lazy {
        createThinIcon("ThinViewCarousel") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.5f, 4.5f)
                lineTo(15.5f, 4.5f)
                curveTo(16.6f, 4.5f, 17.5f, 5.4f, 17.5f, 6.5f)
                lineTo(17.5f, 17.5f)
                curveTo(17.5f, 18.6f, 16.6f, 19.5f, 15.5f, 19.5f)
                lineTo(8.5f, 19.5f)
                curveTo(7.4f, 19.5f, 6.5f, 18.6f, 6.5f, 17.5f)
                lineTo(6.5f, 6.5f)
                curveTo(6.5f, 5.4f, 7.4f, 4.5f, 8.5f, 4.5f)
                close()
                moveTo(3f, 7f); lineTo(6.5f, 7f)
                moveTo(3f, 17f); lineTo(6.5f, 17f)
                moveTo(3f, 7f); lineTo(3f, 17f)
                moveTo(17.5f, 7f); lineTo(21f, 7f)
                moveTo(17.5f, 17f); lineTo(21f, 17f)
                moveTo(21f, 7f); lineTo(21f, 17f)
            }
        }
    }

    val Code: ImageVector by lazy {
        createThinIcon("ThinCode") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.5f, 6f)
                lineTo(3.5f, 12f)
                lineTo(8.5f, 18f)
                moveTo(15.5f, 6f)
                lineTo(20.5f, 12f)
                lineTo(15.5f, 18f)
                moveTo(14f, 4.5f)
                lineTo(10f, 19.5f)
            }
        }
    }

    val PlayCircle: ImageVector by lazy {
        createThinIcon("ThinPlayCircle") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3.5f)
                curveTo(7.3f, 3.5f, 3.5f, 7.3f, 3.5f, 12f)
                curveTo(3.5f, 16.7f, 7.3f, 20.5f, 12f, 20.5f)
                curveTo(16.7f, 20.5f, 20.5f, 16.7f, 20.5f, 12f)
                curveTo(20.5f, 7.3f, 16.7f, 3.5f, 12f, 3.5f)
                close()
                moveTo(10f, 8.5f)
                lineTo(16f, 12f)
                lineTo(10f, 15.5f)
                close()
            }
        }
    }

    val QuickTools: ImageVector by lazy {
        createThinIcon("ThinQuickTools") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 4.5f); lineTo(9.5f, 4.5f); lineTo(9.5f, 9.5f); lineTo(4.5f, 9.5f); close()
                moveTo(14.5f, 4.5f); lineTo(19.5f, 4.5f); lineTo(19.5f, 9.5f); lineTo(14.5f, 9.5f); close()
                moveTo(4.5f, 14.5f); lineTo(9.5f, 14.5f); lineTo(9.5f, 19.5f); lineTo(4.5f, 19.5f); close()
                moveTo(14.5f, 14.5f); lineTo(19.5f, 14.5f); lineTo(19.5f, 19.5f); lineTo(14.5f, 19.5f); close()
            }
        }
    }

    val Widgets: ImageVector by lazy {
        createThinIcon("ThinWidgets") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.5f, 3.5f); lineTo(9.5f, 3.5f); lineTo(9.5f, 9.5f); lineTo(3.5f, 9.5f); close()
                moveTo(14.5f, 3.5f); lineTo(20.5f, 3.5f); lineTo(20.5f, 9.5f); lineTo(14.5f, 9.5f); close()
                moveTo(3.5f, 14.5f); lineTo(9.5f, 14.5f); lineTo(9.5f, 20.5f); lineTo(3.5f, 20.5f); close()
                moveTo(17.5f, 13.5f); lineTo(21f, 17f); lineTo(17.5f, 20.5f); lineTo(14f, 17f); close()
            }
        }
    }

    val Inventory: ImageVector by lazy {
        createThinIcon("ThinInventory") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 4.5f); lineTo(19.5f, 4.5f); lineTo(19.5f, 8.5f); lineTo(4.5f, 8.5f); close()
                moveTo(5.5f, 8.5f); lineTo(5.5f, 19.5f); lineTo(18.5f, 19.5f); lineTo(18.5f, 8.5f)
                moveTo(10f, 12f); lineTo(14f, 12f)
            }
        }
    }

    val ContentPaste: ImageVector by lazy {
        createThinIcon("ThinContentPaste") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.5f, 6.5f)
                lineTo(17.5f, 6.5f)
                curveTo(18.6f, 6.5f, 19.5f, 7.4f, 19.5f, 8.5f)
                lineTo(19.5f, 19.5f)
                curveTo(19.5f, 20.6f, 18.6f, 21.5f, 17.5f, 21.5f)
                lineTo(6.5f, 21.5f)
                curveTo(5.4f, 21.5f, 4.5f, 20.6f, 4.5f, 19.5f)
                lineTo(4.5f, 8.5f)
                curveTo(4.5f, 7.4f, 5.4f, 6.5f, 6.5f, 6.5f)
                close()
                moveTo(9f, 6.5f)
                curveTo(9f, 5.4f, 9.9f, 4.5f, 11f, 4.5f)
                lineTo(13f, 4.5f)
                curveTo(14.1f, 4.5f, 15f, 5.4f, 15f, 6.5f)
                close()
            }
        }
    }

    val MyLocation: ImageVector by lazy {
        createThinIcon("ThinMyLocation") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 6.5f)
                curveTo(8.96f, 6.5f, 6.5f, 8.96f, 6.5f, 12f)
                curveTo(6.5f, 15.04f, 8.96f, 17.5f, 12f, 17.5f)
                curveTo(15.04f, 17.5f, 17.5f, 15.04f, 17.5f, 12f)
                curveTo(17.5f, 8.96f, 15.04f, 6.5f, 12f, 6.5f)
                close()
                moveTo(12f, 3f); lineTo(12f, 5f)
                moveTo(12f, 19f); lineTo(12f, 21f)
                moveTo(3f, 12f); lineTo(5f, 12f)
                moveTo(19f, 12f); lineTo(21f, 12f)
            }
            path(fill = strokeBrush) {
                moveTo(13f, 12f)
                curveTo(13f, 12.55f, 12.55f, 13f, 12f, 13f)
                curveTo(11.45f, 13f, 11f, 12.55f, 11f, 12f)
                curveTo(11f, 11.45f, 11.45f, 11f, 12f, 11f)
                curveTo(12.55f, 11f, 13f, 11.45f, 13f, 12f)
                close()
            }
        }
    }

    val TouchApp: ImageVector by lazy {
        createThinIcon("ThinTouchApp") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(10f, 6f)
                lineTo(10f, 13f)
                moveTo(10f, 10f)
                curveTo(8.3f, 10f, 7f, 11.3f, 7f, 13f)
                lineTo(7f, 17f)
                curveTo(7f, 19.2f, 8.8f, 21f, 11f, 21f)
                lineTo(14f, 21f)
                curveTo(16.8f, 21f, 19f, 18.8f, 19f, 16f)
                lineTo(19f, 12f)
                moveTo(14f, 4f)
                curveTo(15.7f, 4.8f, 17f, 6.5f, 17f, 8.5f)
            }
        }
    }

    val Back: ImageVector by lazy {
        createThinIcon("ThinBack") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(19f, 12f)
                lineTo(5f, 12f)
                moveTo(11f, 6f)
                lineTo(5f, 12f)
                lineTo(11f, 18f)
            }
        }
    }

    val Home: ImageVector by lazy {
        createThinIcon("ThinHome") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.5f, 10.5f)
                lineTo(12f, 3.5f)
                lineTo(20.5f, 10.5f)
                moveTo(5.5f, 9.5f)
                lineTo(5.5f, 19.5f)
                lineTo(18.5f, 19.5f)
                lineTo(18.5f, 9.5f)
            }
        }
    }

    val Recents: ImageVector by lazy {
        createThinIcon("ThinRecents") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3.5f)
                lineTo(21f, 7.5f)
                lineTo(12f, 11.5f)
                lineTo(3f, 7.5f)
                close()
                moveTo(3f, 12f)
                lineTo(12f, 16f)
                lineTo(21f, 12f)
                moveTo(3f, 16.5f)
                lineTo(12f, 20.5f)
                lineTo(21f, 16.5f)
            }
        }
    }

    val Close: ImageVector by lazy {
        createThinIcon("ThinClose") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6f, 6f)
                lineTo(18f, 18f)
                moveTo(18f, 6f)
                lineTo(6f, 18f)
            }
        }
    }

    /** 100% 精准对齐：FreeWindow 箭头尾部恰好位于左竖线 (X:2.5) 与上横线 (Y:4.5) 的延伸交点 (2.5, 4.5) */
    val FreeWindow: ImageVector by lazy {
        createThinIcon("ThinFreeWindow") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 1. 单向右下拉伸箭头 (尾部精确对齐左竖线与上横线延伸交点 2.5, 4.5 -> 右下 Pip 窗口 11.5, 13.5)
                moveTo(2.5f, 4.5f)
                lineTo(11.5f, 13.5f)
                // 右下方向箭尖 (指向右下方 11.5, 13.5)
                moveTo(8.5f, 13.5f)
                lineTo(11.5f, 13.5f)
                lineTo(11.5f, 10.5f)
                // 2. 左下角框架 (左侧竖线 4dp 延长)
                moveTo(2.5f, 9f); lineTo(2.5f, 19f)
                curveTo(2.5f, 20f, 3.5f, 20.5f, 4.5f, 20.5f)
                lineTo(11.5f, 20.5f)
                // 3. 右上角框架 (顶端横线 4dp 延长)
                moveTo(7.5f, 4.5f); lineTo(19.5f, 4.5f)
                curveTo(20.5f, 4.5f, 21.5f, 5.5f, 21.5f, 6.5f)
                lineTo(21.5f, 13f)
                // 4. 右下角 Pip 悬浮迷你小窗口
                moveTo(13.5f, 14.5f)
                lineTo(21.5f, 14.5f)
                lineTo(21.5f, 20.5f)
                lineTo(13.5f, 20.5f)
                close()
            }
        }
    }

    val Flashlight: ImageVector by lazy {
        createThinIcon("ThinFlashlight") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9f, 11f)
                lineTo(9f, 20f)
                lineTo(15f, 20f)
                lineTo(15f, 11f)
                lineTo(18f, 6f)
                lineTo(18f, 4f)
                lineTo(6f, 4f)
                lineTo(6f, 6f)
                close()
                moveTo(6f, 6f)
                lineTo(18f, 6f)
                moveTo(12f, 12f)
                lineTo(12f, 14f)
            }
        }
    }

    val VolumeUp: ImageVector by lazy {
        createThinIcon("ThinVolumeUp") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 9f)
                lineTo(8.5f, 9f)
                lineTo(13f, 5.5f)
                lineTo(13f, 18.5f)
                lineTo(8.5f, 15f)
                lineTo(5f, 15f)
                close()
                moveTo(16.5f, 8.5f)
                curveTo(17.8f, 9.8f, 17.8f, 14.2f, 16.5f, 15.5f)
                moveTo(19f, 6f)
                curveTo(21.5f, 9f, 21.5f, 15f, 19f, 18f)
            }
        }
    }

    val VolumeOff: ImageVector by lazy {
        createThinIcon("ThinVolumeOff") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 9f)
                lineTo(8.5f, 9f)
                lineTo(13f, 5.5f)
                lineTo(13f, 18.5f)
                lineTo(8.5f, 15f)
                lineTo(5f, 15f)
                close()
                moveTo(16f, 9f)
                lineTo(21f, 15f)
                moveTo(21f, 9f)
                lineTo(16f, 15f)
            }
        }
    }

    val Brightness: ImageVector by lazy {
        createThinIcon("ThinBrightness") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 7f)
                curveTo(9.24f, 7f, 7f, 9.24f, 7f, 12f)
                curveTo(7f, 14.76f, 9.24f, 17f, 12f, 17f)
                curveTo(14.76f, 17f, 17f, 14.76f, 17f, 12f)
                curveTo(17f, 9.24f, 14.76f, 7f, 12f, 7f)
                close()
                moveTo(12f, 3f); lineTo(12f, 4.5f)
                moveTo(12f, 19.5f); lineTo(12f, 21f)
                moveTo(3f, 12f); lineTo(4.5f, 12f)
                moveTo(19.5f, 12f); lineTo(21f, 12f)
                moveTo(5.64f, 5.64f); lineTo(6.7f, 6.7f)
                moveTo(17.3f, 17.3f); lineTo(18.36f, 18.36f)
                moveTo(5.64f, 18.36f); lineTo(6.7f, 17.3f)
                moveTo(17.3f, 6.7f); lineTo(18.36f, 5.64f)
            }
        }
    }

    val Assistant: ImageVector by lazy {
        createThinIcon("ThinAssistant") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 4f)
                curveTo(12f, 8.4f, 8.4f, 12f, 4f, 12f)
                curveTo(8.4f, 12f, 12f, 15.6f, 12f, 20f)
                curveTo(12f, 15.6f, 15.6f, 12f, 20f, 12f)
                curveTo(15.6f, 12f, 12f, 8.4f, 12f, 4f)
                close()
            }
        }
    }

    val PlayPause: ImageVector by lazy {
        createThinIcon("ThinPlayPause") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5.5f, 7.5f)
                lineTo(5.5f, 16.5f)
                lineTo(10.5f, 12f)
                close()
                moveTo(13f, 7.5f)
                lineTo(13f, 16.5f)
                moveTo(16f, 7.5f)
                lineTo(16f, 16.5f)
            }
        }
    }

    val SkipPrevious: ImageVector by lazy {
        createThinIcon("ThinSkipPrevious") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7f, 6.5f); lineTo(7f, 17.5f)
                moveTo(17f, 6.5f); lineTo(9.5f, 12f); lineTo(17f, 17.5f); close()
            }
        }
    }

    val SkipNext: ImageVector by lazy {
        createThinIcon("ThinSkipNext") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(17f, 6.5f); lineTo(17f, 17.5f)
                moveTo(7f, 6.5f); lineTo(14.5f, 12f); lineTo(7f, 17.5f); close()
            }
        }
    }

    /** 绝无撞线的 Android 机器人 + 右下角独立避让 History 历史指针 (Restore) */
    val Restore: ImageVector by lazy {
        createThinIcon("ThinRestore") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 1. Android 机器人触角
                moveTo(6.5f, 6f); lineTo(8.5f, 9.5f)
                moveTo(15.5f, 6f); lineTo(13.5f, 9.5f)
                // Android 机器人圆弧头顶 (右下避让)
                moveTo(4.5f, 16f)
                curveTo(4.5f, 10f, 7.5f, 9.5f, 11f, 9.5f)
                curveTo(14.5f, 9.5f, 16.5f, 10.5f, 16.5f, 15f)
                // 机器人眼睛
                moveTo(8f, 12.5f); lineTo(8f, 12.6f)
                moveTo(13.5f, 12.5f); lineTo(13.5f, 12.6f)
                // 2. 右下角独立避让 History 历史指针角标 (X: 13.5..21.5, Y: 13.5..21.5)
                moveTo(17.5f, 14.5f)
                curveTo(15.5f, 14.5f, 14f, 16f, 14f, 18f)
                curveTo(14f, 20f, 15.5f, 21.5f, 17.5f, 21.5f)
                curveTo(19.5f, 21.5f, 21f, 20f, 21f, 18f)
                curveTo(21f, 16f, 19.5f, 14.5f, 17.5f, 14.5f)
                close()
                // 指针
                moveTo(17.5f, 16f); lineTo(17.5f, 18f); lineTo(19f, 18f)
                // 回溯箭尖
                moveTo(16f, 14f); lineTo(14.5f, 15.5f); lineTo(16f, 17f)
            }
        }
    }

    val Notifications: ImageVector by lazy {
        createThinIcon("ThinNotifications") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 4f)
                curveTo(9.2f, 4f, 7f, 6.2f, 7f, 9f)
                lineTo(7f, 14f)
                lineTo(5f, 16f)
                lineTo(19f, 16f)
                lineTo(17f, 14f)
                lineTo(17f, 9f)
                curveTo(17f, 6.2f, 14.8f, 4f, 12f, 4f)
                close()
                moveTo(10f, 18f)
                curveTo(10f, 19.1f, 10.9f, 20f, 12f, 20f)
                curveTo(13.1f, 20f, 14f, 19.1f, 14f, 18f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        createThinIcon("ThinSettings") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 8.5f)
                curveTo(10.07f, 8.5f, 8.5f, 10.07f, 8.5f, 12f)
                curveTo(8.5f, 13.93f, 10.07f, 15.5f, 12f, 15.5f)
                curveTo(13.93f, 15.5f, 15.5f, 13.93f, 15.5f, 12f)
                curveTo(15.5f, 10.07f, 13.93f, 8.5f, 12f, 8.5f)
                close()
                moveTo(12f, 4.5f); lineTo(12f, 6.5f)
                moveTo(12f, 17.5f); lineTo(12f, 19.5f)
                moveTo(4.5f, 12f); lineTo(6.5f, 12f)
                moveTo(17.5f, 12f); lineTo(19.5f, 12f)
                moveTo(6.7f, 6.7f); lineTo(8.1f, 8.1f)
                moveTo(15.9f, 15.9f); lineTo(17.3f, 17.3f)
                moveTo(6.7f, 17.3f); lineTo(8.1f, 15.9f)
                moveTo(15.9f, 8.1f); lineTo(17.3f, 6.7f)
            }
        }
    }

    /** 快捷设置 / 控制中心：三竖滑条，与太阳亮度图标区分。 */
    val QuickSettings: ImageVector by lazy {
        createThinIcon("ThinQuickSettings") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8f, 6f); lineTo(8f, 18f)
                moveTo(12f, 5f); lineTo(12f, 19f)
                moveTo(16f, 7f); lineTo(16f, 17f)
                moveTo(9.35f, 10f)
                arcTo(1.35f, 1.35f, 0f, false, true, 6.65f, 10f)
                arcTo(1.35f, 1.35f, 0f, false, true, 9.35f, 10f)
                moveTo(13.35f, 14f)
                arcTo(1.35f, 1.35f, 0f, false, true, 10.65f, 14f)
                arcTo(1.35f, 1.35f, 0f, false, true, 13.35f, 14f)
                moveTo(17.35f, 9f)
                arcTo(1.35f, 1.35f, 0f, false, true, 14.65f, 9f)
                arcTo(1.35f, 1.35f, 0f, false, true, 17.35f, 9f)
            }
        }
    }

    /** 屏幕常亮：圆环 + 中心锁 + 八向光芒（与 [Brightness] 纯太阳、[Lock] 纯锁区分）。 */
    val KeepScreenOn: ImageVector by lazy {
        createThinIcon("ThinKeepScreenOn") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 5.5f)
                curveTo(15.58f, 5.5f, 18.5f, 8.42f, 18.5f, 12f)
                curveTo(18.5f, 15.58f, 15.58f, 18.5f, 12f, 18.5f)
                curveTo(8.42f, 18.5f, 5.5f, 15.58f, 5.5f, 12f)
                curveTo(5.5f, 8.42f, 8.42f, 5.5f, 12f, 5.5f)
                close()
                moveTo(10f, 11f)
                lineTo(10f, 9.25f)
                curveTo(10f, 8.1f, 10.9f, 7.25f, 12f, 7.25f)
                curveTo(13.1f, 7.25f, 14f, 8.1f, 14f, 9.25f)
                lineTo(14f, 11f)
                moveTo(9.75f, 11f)
                lineTo(14.25f, 11f)
                lineTo(14.25f, 14.75f)
                lineTo(9.75f, 14.75f)
                close()
                moveTo(12f, 12.35f)
                lineTo(12f, 13.45f)
                moveTo(12f, 3f)
                lineTo(12f, 5.5f)
                moveTo(12f, 21f)
                lineTo(12f, 18.5f)
                moveTo(3f, 12f)
                lineTo(5.5f, 12f)
                moveTo(21f, 12f)
                lineTo(18.5f, 12f)
                moveTo(5.64f, 5.64f)
                lineTo(7.35f, 7.35f)
                moveTo(18.36f, 18.36f)
                lineTo(16.65f, 16.65f)
                moveTo(5.64f, 18.36f)
                lineTo(7.35f, 16.65f)
                moveTo(18.36f, 5.64f)
                lineTo(16.65f, 7.35f)
            }
        }
    }

    val Lock: ImageVector by lazy {
        createThinIcon("ThinLock") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.5f, 10.5f)
                lineTo(17.5f, 10.5f)
                curveTo(18.6f, 10.5f, 19.5f, 11.4f, 19.5f, 12.5f)
                lineTo(19.5f, 19.5f)
                curveTo(19.5f, 20.6f, 18.6f, 21.5f, 17.5f, 21.5f)
                lineTo(6.5f, 21.5f)
                curveTo(5.4f, 21.5f, 4.5f, 20.6f, 4.5f, 19.5f)
                lineTo(4.5f, 12.5f)
                curveTo(4.5f, 11.4f, 5.4f, 10.5f, 6.5f, 10.5f)
                close()
                moveTo(8f, 10.5f)
                lineTo(8f, 6.5f)
                curveTo(8f, 4.3f, 9.8f, 2.5f, 12f, 2.5f)
                curveTo(14.2f, 2.5f, 16f, 4.3f, 16f, 6.5f)
                lineTo(16f, 10.5f)
            }
        }
    }

    /** 锁屏并静音响铃：大锁屏外框 + 方框内上下左右几何居中 (Y: 15.75f) 干净带斜杠静音铃铛 */
    val LockSilenceRing: ImageVector by lazy {
        createThinIcon("ThinLockSilenceRing") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 1. 大锁屏外框
                moveTo(5f, 10f)
                lineTo(19f, 10f)
                curveTo(19.8f, 10f, 20.5f, 10.7f, 20.5f, 11.5f)
                lineTo(20.5f, 20f)
                curveTo(20.5f, 20.8f, 19.8f, 21.5f, 19f, 21.5f)
                lineTo(5f, 21.5f)
                curveTo(4.2f, 21.5f, 3.5f, 20.8f, 3.5f, 20f)
                lineTo(3.5f, 11.5f)
                curveTo(3.5f, 10.7f, 4.2f, 10f, 5f, 10f)
                close()
                // 锁顶钩
                moveTo(8.5f, 10f)
                lineTo(8.5f, 6.5f)
                curveTo(8.5f, 4.5f, 10f, 3f, 12f, 3f)
                curveTo(14f, 3f, 15.5f, 4.5f, 15.5f, 6.5f)
                lineTo(15.5f, 10f)
                // 2. 方框内部上下居中 (Y 轴中心 15.75) 的清晰静音铃铛
                moveTo(12f, 12.5f)
                curveTo(11.2f, 12.5f, 10.5f, 13.2f, 10.5f, 14f)
                lineTo(10.5f, 16.5f)
                lineTo(9.5f, 17.5f)
                lineTo(14.5f, 17.5f)
                lineTo(13.5f, 16.5f)
                lineTo(13.5f, 14f)
                curveTo(13.5f, 13.2f, 12.8f, 12.5f, 12f, 12.5f)
                close()
                // 摆锤
                moveTo(11.5f, 18.5f)
                curveTo(11.5f, 18.8f, 11.7f, 19f, 12f, 19f)
                curveTo(12.3f, 19f, 12.5f, 18.8f, 12.5f, 18.5f)
                // 静音斜杠 (直观易懂)
                moveTo(9f, 13f)
                lineTo(15f, 18.5f)
            }
        }
    }

    /** 锁屏并全部静音：大锁屏外框 + 喇叭与右侧静音叉号精确保留 2dp 完美间距 */
    val LockMuteAll: ImageVector by lazy {
        createThinIcon("ThinLockMuteAll") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 1. 大锁屏外框
                moveTo(5f, 10f)
                lineTo(19f, 10f)
                curveTo(19.8f, 10f, 20.5f, 10.7f, 20.5f, 11.5f)
                lineTo(20.5f, 20f)
                curveTo(20.5f, 20.8f, 19.8f, 21.5f, 19f, 21.5f)
                lineTo(5f, 21.5f)
                curveTo(4.2f, 21.5f, 3.5f, 20.8f, 3.5f, 20f)
                lineTo(3.5f, 11.5f)
                curveTo(3.5f, 10.7f, 4.2f, 10f, 5f, 10f)
                close()
                // 锁顶钩
                moveTo(8.5f, 10f)
                lineTo(8.5f, 6.5f)
                curveTo(8.5f, 4.5f, 10f, 3f, 12f, 3f)
                curveTo(14f, 3f, 15.5f, 4.5f, 15.5f, 6.5f)
                lineTo(15.5f, 10f)
                // 2. 喇叭口 (X: 7.5..11.0)
                moveTo(7.5f, 14.5f)
                lineTo(9f, 14.5f)
                lineTo(11f, 12.5f)
                lineTo(11f, 19f)
                lineTo(9f, 17f)
                lineTo(7.5f, 17f)
                close()
                // 3. 静音叉号 x (X: 13.0..16.0，与喇叭口保留精准 2dp 完美留白)
                moveTo(13f, 14f)
                lineTo(16f, 17f)
                moveTo(16f, 14f)
                lineTo(13f, 17f)
            }
        }
    }

    /** 1:1 依照用户指定 SVG (screenshot) 重绘：手机边框 + 顶底横杠 + 单线精细 L 裁切角标 (Screenshot) */
    val Screenshot: ImageVector by lazy {
        createThinIcon("ThinScreenshot") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 1. 手机外框 (5..19, 2.5..21.5)
                moveTo(6.5f, 2.5f)
                lineTo(17.5f, 2.5f)
                curveTo(18.5f, 2.5f, 19f, 3f, 19f, 4f)
                lineTo(19f, 20f)
                curveTo(19f, 21f, 18.5f, 21.5f, 17.5f, 21.5f)
                lineTo(6.5f, 21.5f)
                curveTo(5.5f, 21.5f, 5f, 21f, 5f, 20f)
                lineTo(5f, 4f)
                curveTo(5f, 3f, 5.5f, 2.5f, 6.5f, 2.5f)
                close()
                // 2. 顶杠与底杠区分线
                moveTo(5f, 5f); lineTo(19f, 5f)
                moveTo(5f, 19f); lineTo(19f, 19f)
                // 3. 干净单线条 1.5dp L 型截切角标 (绝不粗重)
                moveTo(11.5f, 8f); lineTo(8.5f, 8f); lineTo(8.5f, 11f)
                moveTo(12.5f, 16f); lineTo(15.5f, 16f); lineTo(15.5f, 13f)
            }
        }
    }

    val TextFields: ImageVector by lazy {
        createThinIcon("ThinTextFields") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 6f); lineTo(14f, 6f)
                moveTo(9f, 6f); lineTo(9f, 19f)
                moveTo(14f, 11f); lineTo(20f, 11f)
                moveTo(17f, 11f); lineTo(17f, 19f)
            }
        }
    }

    /** 1:1 官方 Material crop_free 区域截图/裁剪+ (ScreenshotRegion) */
    val ScreenshotRegion: ImageVector by lazy {
        createThinIcon("ThinScreenshotRegion") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 左上 L
                moveTo(5f, 10f); lineTo(5f, 5f); lineTo(10f, 5f)
                // 右上 L
                moveTo(14f, 5f); lineTo(19f, 5f); lineTo(19f, 10f)
                // 左下 L
                moveTo(5f, 14f); lineTo(5f, 19f); lineTo(10f, 19f)
                // 右下加号 +
                moveTo(16.5f, 13.5f); lineTo(16.5f, 19.5f)
                moveTo(13.5f, 16.5f); lineTo(19.5f, 16.5f)
            }
        }
    }

    val Search: ImageVector by lazy {
        createThinIcon("ThinSearch") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(10.5f, 4.5f)
                curveTo(13.8f, 4.5f, 16.5f, 7.2f, 16.5f, 10.5f)
                curveTo(16.5f, 13.8f, 13.8f, 16.5f, 10.5f, 16.5f)
                curveTo(7.2f, 16.5f, 4.5f, 13.8f, 4.5f, 10.5f)
                curveTo(4.5f, 7.2f, 7.2f, 4.5f, 10.5f, 4.5f)
                close()
                moveTo(15f, 15f)
                lineTo(19.5f, 19.5f)
            }
        }
    }

    val Power: ImageVector by lazy {
        createThinIcon("ThinPower") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 4f); lineTo(12f, 11f)
                moveTo(6.34f, 7.34f)
                curveTo(4.56f, 9.12f, 4f, 11.8f, 4.8f, 14.2f)
                curveTo(6f, 17.8f, 9.6f, 20f, 13.5f, 19.5f)
                curveTo(17f, 19f, 19.8f, 16.2f, 20f, 12.6f)
                curveTo(20.2f, 9.8f, 18.8f, 7.4f, 17.66f, 7.34f)
            }
        }
    }

    val ArrowUp: ImageVector by lazy {
        createThinIcon("ThinArrowUp") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 19f)
                lineTo(12f, 5f)
                moveTo(6f, 11f)
                lineTo(12f, 5f)
                lineTo(18f, 11f)
            }
        }
    }

    val ArrowDown: ImageVector by lazy {
        createThinIcon("ThinArrowDown") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(6f, 13f)
                lineTo(12f, 19f)
                lineTo(18f, 13f)
            }
        }
    }

    /** 快速回到顶部：箭头尖端带横线。 */
    val ScrollToTop: ImageVector by lazy {
        createThinIcon("ThinScrollToTop") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 5f)
                lineTo(19f, 5f)
                moveTo(12f, 19f)
                lineTo(12f, 5f)
                moveTo(6f, 11f)
                lineTo(12f, 5f)
                lineTo(18f, 11f)
            }
        }
    }

    /** 快速回到底部：箭头尖端带横线。 */
    val ScrollToBottom: ImageVector by lazy {
        createThinIcon("ThinScrollToBottom") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 19f)
                lineTo(19f, 19f)
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(6f, 13f)
                lineTo(12f, 19f)
                lineTo(18f, 13f)
            }
        }
    }

    val ArrowRight: ImageVector by lazy {
        createThinIcon("ThinArrowRight") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 12f)
                lineTo(19f, 12f)
                moveTo(13f, 6f)
                lineTo(19f, 12f)
                lineTo(13f, 18f)
            }
        }
    }

    /** 长距离滑动：左侧轨迹线 + 右侧双 chevron（>>），与短划 [ArrowRight] 同 viewport 跨度。 */
    val DoubleArrowRight: ImageVector by lazy {
        createThinIcon("ThinDoubleArrowRight") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 12f)
                lineTo(13.5f, 12f)
                moveTo(12.5f, 7f)
                lineTo(16.5f, 12f)
                lineTo(12.5f, 17f)
                moveTo(15.5f, 7f)
                lineTo(19f, 12f)
                lineTo(15.5f, 17f)
            }
        }
    }

    val DoNotDisturb: ImageVector by lazy {
        createThinIcon("ThinDoNotDisturb") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 4.5f)
                curveTo(7.86f, 4.5f, 4.5f, 7.86f, 4.5f, 12f)
                curveTo(4.5f, 16.14f, 7.86f, 19.5f, 12f, 19.5f)
                curveTo(16.14f, 19.5f, 19.5f, 16.14f, 19.5f, 12f)
                curveTo(19.5f, 7.86f, 16.14f, 4.5f, 12f, 4.5f)
                close()
                moveTo(7.5f, 12f)
                lineTo(16.5f, 12f)
            }
        }
    }

    /** 极度具辨识度的 1:1 Material Videocam / 摄像机录屏图标 (ScreenRecord) */
    val ScreenRecord: ImageVector by lazy {
        createThinIcon("ThinScreenRecord") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // 1. 摄像机主框架
                moveTo(4.5f, 7.5f)
                lineTo(14.5f, 7.5f)
                curveTo(15.6f, 7.5f, 16.5f, 8.4f, 16.5f, 9.5f)
                lineTo(16.5f, 16.5f)
                curveTo(16.5f, 17.6f, 15.6f, 18.5f, 14.5f, 18.5f)
                lineTo(4.5f, 18.5f)
                curveTo(3.4f, 18.5f, 2.5f, 17.6f, 2.5f, 16.5f)
                lineTo(2.5f, 9.5f)
                curveTo(2.5f, 8.4f, 3.4f, 7.5f, 4.5f, 7.5f)
                close()
                // 2. 右侧摄像镜头梯形
                moveTo(16.5f, 11f)
                lineTo(20.5f, 8.5f)
                lineTo(20.5f, 17.5f)
                lineTo(16.5f, 15f)
                close()
                // 3. REC 录制红点
                moveTo(9.5f, 11.5f)
                lineTo(9.5f, 14.5f)
            }
        }
    }

    val Wifi: ImageVector by lazy {
        createThinIcon("ThinWifi") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.5f, 7.5f)
                curveTo(8.5f, 3.5f, 15.5f, 3.5f, 20.5f, 7.5f)
                moveTo(6.5f, 11.5f)
                curveTo(9.8f, 8.8f, 14.2f, 8.8f, 17.5f, 11.5f)
                moveTo(9.5f, 15.5f)
                curveTo(11f, 14.2f, 13f, 14.2f, 14.5f, 15.5f)
                moveTo(12f, 19.5f)
                lineTo(12f, 19.6f)
            }
        }
    }

    val Cellular: ImageVector by lazy {
        createThinIcon("ThinCellular") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 17.5f); lineTo(4.5f, 19.5f)
                moveTo(9.5f, 13.5f); lineTo(9.5f, 19.5f)
                moveTo(14.5f, 9.5f); lineTo(14.5f, 19.5f)
                moveTo(19.5f, 5.5f); lineTo(19.5f, 19.5f)
            }
        }
    }

    val Keyboard: ImageVector by lazy {
        createThinIcon("ThinKeyboard") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 6.5f)
                lineTo(19.5f, 6.5f)
                curveTo(20.6f, 6.5f, 21.5f, 7.4f, 21.5f, 8.5f)
                lineTo(21.5f, 15.5f)
                curveTo(21.5f, 16.6f, 20.6f, 17.5f, 19.5f, 17.5f)
                lineTo(4.5f, 17.5f)
                curveTo(3.4f, 17.5f, 2.5f, 16.6f, 2.5f, 15.5f)
                lineTo(2.5f, 8.5f)
                curveTo(2.5f, 7.4f, 3.4f, 6.5f, 4.5f, 6.5f)
                close()
                moveTo(6f, 9.5f); lineTo(8f, 9.5f)
                moveTo(11f, 9.5f); lineTo(13f, 9.5f)
                moveTo(16f, 9.5f); lineTo(18f, 9.5f)
                moveTo(6f, 12f); lineTo(8f, 12f)
                moveTo(11f, 12f); lineTo(13f, 12f)
                moveTo(16f, 12f); lineTo(18f, 12f)
                moveTo(8f, 14.5f); lineTo(16f, 14.5f)
            }
        }
    }

    val Gesture: ImageVector by lazy {
        createThinIcon("ThinGesture") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 16.5f)
                curveTo(4.5f, 16.5f, 8.5f, 6.5f, 12.5f, 11.5f)
                curveTo(16.5f, 16.5f, 19.5f, 7.5f, 19.5f, 7.5f)
            }
        }
    }

    val MenuOpen: ImageVector by lazy {
        createThinIcon("ThinMenuOpen") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3f, 6f); lineTo(21f, 6f)
                moveTo(10f, 12f); lineTo(21f, 12f)
                moveTo(3f, 18f); lineTo(21f, 18f)
                moveTo(6f, 9f); lineTo(3f, 12f); lineTo(6f, 15f)
            }
        }
    }

    val Shortcut: ImageVector by lazy {
        createThinIcon("ThinShortcut") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(15f, 5f)
                lineTo(20f, 5f)
                lineTo(20f, 10f)
                moveTo(20f, 5f)
                lineTo(11f, 14f)
                curveTo(7.5f, 17.5f, 4f, 19f, 4f, 19f)
            }
        }
    }

    val VisibilityOff: ImageVector by lazy {
        createThinIcon("ThinVisibilityOff") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 12f)
                curveTo(7f, 7.8f, 17f, 7.8f, 19.5f, 12f)
                curveTo(17f, 16.2f, 7f, 16.2f, 4.5f, 12f)
                close()
                moveTo(12f, 14.25f)
                curveTo(13.38f, 14.25f, 14.5f, 13.13f, 14.5f, 11.75f)
                curveTo(14.5f, 10.37f, 13.38f, 9.25f, 12f, 9.25f)
                curveTo(10.62f, 9.25f, 9.5f, 10.37f, 9.5f, 11.75f)
                curveTo(9.5f, 13.13f, 10.62f, 14.25f, 12f, 14.25f)
                close()
                moveTo(3.5f, 3.5f)
                lineTo(20.5f, 20.5f)
            }
        }
    }

    val ClickPassthrough: ImageVector by lazy { MaterialTouchIcons.SingleTap }

    /** 雪花：重冻应用（Material AcUnit） */
    val Snowflake: ImageVector get() = Icons.Outlined.AcUnit

    /** 自动亮度 */
    val BrightnessAuto: ImageVector get() = Icons.Outlined.BrightnessAuto

    /** 冰箱：打开冰箱管理 */
    val Fridge: ImageVector by lazy {
        createThinIcon("ThinFridge") {
            path(
                stroke = strokeBrush,
                strokeLineWidth = STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7f, 4f)
                lineTo(17f, 4f)
                lineTo(17f, 20f)
                lineTo(7f, 20f)
                close()
                moveTo(7f, 10f)
                lineTo(17f, 10f)
                moveTo(13f, 6f)
                lineTo(13f, 8f)
                moveTo(13f, 12f)
                lineTo(13f, 17f)
            }
        }
    }
}
