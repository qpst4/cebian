package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.settings.components.LazySettingsItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** WeKit 设置页：分组小标题与上一内容区的默认上间距。 */
val MiuixSmallTitleSectionTop = 12.dp

/**
 * 对齐 WeKit [dev.ujhhgtg.wekit.ui.content.MiuixSmallTitle]：
 * 列表分组小标题，水平边距 14dp（库 [SmallTitle] 默认 28dp 与全宽卡片不对齐）。
 */
@Composable
fun MiuixSmallTitle(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MiuixTheme.colorScheme.onBackgroundVariant,
    insideMargin: PaddingValues = PaddingValues(14.dp, 8.dp),
    lazyKey: String? = null,
) {
    LazySettingsItem(key = lazyKey ?: "small-title-$text") {
        Text(
            modifier = modifier.padding(insideMargin),
            text = text,
            style = MiuixTheme.textStyles.subtitle,
            color = textColor,
        )
    }
}
