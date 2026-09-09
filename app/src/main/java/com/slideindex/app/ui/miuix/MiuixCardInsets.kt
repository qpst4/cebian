package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * Card 已设 `insideMargin = 16.dp`（TabRowSection 模式 B）时，
 * Preference 行只消掉水平重复缩进，保留上下 16dp 行距。
 */
val MiuixInsetCardComponentMargin = PaddingValues(horizontal = 0.dp, vertical = 16.dp)
