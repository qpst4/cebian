package com.slideindex.app.overlay.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.text.TextStyle

/** 顶栏工具图标：与 [MiuixExpandableSearchIconAction] 同尺寸、同色。 */
@Composable
internal fun HistoryPanelToolbarIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}

/** 对齐 ClipShare 卡片字号：正文 14sp、元信息 11sp。 */
internal object HistoryPanelTypography {
    @Composable
    fun content(): TextStyle = MiuixTheme.textStyles.body2

    @Composable
    fun meta(): TextStyle = MiuixTheme.textStyles.footnote2

    @Composable
    fun hint(): TextStyle = MiuixTheme.textStyles.footnote1
}

/** 收纳面板列表滚动：MIUI 回弹 + 触底触感。 */
internal fun Modifier.historyPanelListScrollEffects(): Modifier = this
    .scrollEndHaptic()
    .overScrollVertical(nestedScrollToParent = false)

/** 列表参与顶栏毛玻璃采样。 */
internal fun Modifier.historyPanelListBackdrop(backdrop: LayerBackdrop?): Modifier =
    historyPanelListScrollEffects().then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)

internal val HistoryListFooterPadding = 64.dp

@Composable
internal fun historyPanelWidth(): Dp {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    return with(density) {
        (windowInfo.containerSize.width / 2f).toDp()
    }
}

@Composable
internal fun historyClipboardCardPreviewHeightPx(): Int {
    val density = LocalDensity.current
    return with(density) { 120.dp.roundToPx() }
}

@Composable
internal fun historyPreviewWidthPx(): Int {
    val density = LocalDensity.current
    return with(density) { (historyPanelWidth() - 24.dp).roundToPx().coerceAtMost(960) }
}

@Composable
internal fun historyStashPreviewHeightPx(): Int {
    val density = LocalDensity.current
    return with(density) { 150.dp.roundToPx() }
}

@Composable
internal fun historyExpandedImageMaxSidePx(): Int {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenHeightPx = windowInfo.containerSize.height
    return with(density) {
        maxOf(
            historyPreviewWidthPx(),
            (screenHeightPx * 0.75f).toInt(),
        ).coerceAtMost(2048)
    }
}

@Composable
internal fun formatHistoryRelativeTime(epochMs: Long): String {
    val diffMs = (System.currentTimeMillis() - epochMs).coerceAtLeast(0L)
    return when {
        diffMs < 60_000L -> stringResource(R.string.stash_time_just_now)
        diffMs < 3_600_000L -> stringResource(R.string.stash_time_minutes_ago, (diffMs / 60_000L).toInt())
        diffMs < 86_400_000L -> stringResource(R.string.stash_time_hours_ago, (diffMs / 3_600_000L).toInt())
        else -> {
            val locale = LocalLocale.current.platformLocale
            val now = Calendar.getInstance()
            val then = Calendar.getInstance().apply { timeInMillis = epochMs }
            val pattern = if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) {
                if (locale.language == "zh") "M月d日" else "MMM d"
            } else {
                "yyyy/M/d"
            }
            SimpleDateFormat(pattern, locale).format(Date(epochMs))
        }
    }
}
