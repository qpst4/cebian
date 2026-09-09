package com.slideindex.app.ui.notificationrule

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 规则编辑页卡片内小节标题：不用 [SmallTitle]，避免与卡片边距叠加导致左缩进过大。 */
@Composable
internal fun NotificationRuleSectionHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.title4,
        color = MiuixTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth(),
    )
}

/** 规则编辑页卡片外（与 grouped card 左缘对齐）的小节标题。 */
@Composable
internal fun NotificationRuleListSectionHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    NotificationRuleSectionHeading(
        text = text,
        modifier = modifier.padding(horizontal = 16.dp),
    )
}
