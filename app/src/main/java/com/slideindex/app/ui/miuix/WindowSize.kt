package com.slideindex.app.ui.miuix

/**
 * Portions derived from Mishka (https://github.com/YuKongA/Mishka)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.mainAppPrefersWideContentLayout

/** 宽屏下设置列表内容的最大宽度；LazyColumn 本身保持全宽，通过 contentPadding 居中限宽。 */
val SettingsContentMaxWidth: Dp = 720.dp

/** 列表内容与屏幕边缘的基础水平间距（叠加 [WideContentBox] 的 sidePadding）。 */
val SettingsListHorizontalPadding: Dp = 12.dp

/**
 * 宽屏内容居中：算出单侧留白 [sidePadding]，交给调用方加进 LazyColumn 的 `contentPadding`。
 * LazyColumn 保持全宽，避免两侧滚动死区（对齐 Mishka [WideContentBox]）。
 */
@Composable
fun WideContentBox(
    modifier: Modifier = Modifier,
    content: @Composable (sidePadding: Dp) -> Unit,
) {
    val isWideScreen = mainAppPrefersWideContentLayout()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sidePadding = if (isWideScreen) {
            ((maxWidth - SettingsContentMaxWidth) / 2).coerceAtLeast(0.dp)
        } else {
            0.dp
        }
        content(sidePadding)
    }
}

/**
 * 二级页 LazyColumn 根节点：补水平方向 displayCutout ∪ navigationBars inset。
 * 竖屏或无侧边缺口时为 0。主 Tab Hub 不需要（外壳已处理）。
 */
@Composable
fun Modifier.horizontalCutoutPadding(): Modifier = windowInsetsPadding(
    WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal),
)
