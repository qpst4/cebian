package com.slideindex.app.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.miuix.miuixGroupedRowInsets
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 状态行的语义色：好（主色）/ 坏（错误色）/ 中性（次要文字色）。
 *
 * 「检测中」「未启用」这类不是故障的状态走中性，避免整排红字看起来像报错。
 */
enum class StatusTone {
    Good,
    Bad,
    Neutral,
}

@Composable
fun StatusTone.contentColor(): Color = when (this) {
    StatusTone.Good -> MiuixTheme.colorScheme.primary
    StatusTone.Bad -> MiuixTheme.colorScheme.error
    StatusTone.Neutral -> MiuixTheme.colorScheme.onSurfaceVariantSummary
}

/**
 * 卡片内的状态行：色点 + 标题 + 状态胶囊，下面一行说明。
 *
 * 「剪贴板后台监听」页与「屏蔽系统手势」页共用，保证两处观感一致。
 */
@Composable
fun SettingsCardScope.StatusRow(
    title: String,
    pill: String,
    tone: StatusTone,
    detail: String? = null,
    segmentKey: Any = title,
    onClick: (() -> Unit)? = null,
) {
    SettingsCardRow(key = segmentKey) { position ->
        Column(
            modifier = Modifier
                .settingsGroupedRowBackground(position.index, position.count)
                .miuixGroupedRowInsets()
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(tone.contentColor()),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = title,
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(8.dp))
                StatusPill(text = pill, tone = tone)
            }
            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = detail,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/** 状态胶囊：同色文字 + 14% 同色底（深色主题下也看得清，不用 container 配色）。 */
@Composable
fun StatusPill(
    text: String,
    tone: StatusTone,
    onClick: (() -> Unit)? = null,
) {
    val content = tone.contentColor()
    Text(
        text = text,
        color = content,
        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(content.copy(alpha = 0.14f))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
