@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui.messagestyle

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.message.MessageSettings
import com.slideindex.app.message.MessageStyle
import com.slideindex.app.message.MessageThemeCatalog
import com.slideindex.app.message.messageThemeBackground
import com.slideindex.app.message.overlayAlpha
import com.slideindex.app.ui.SettingsCard
import com.slideindex.app.ui.SettingsSectionTitle

@Composable
internal fun MessageStyleLivePreviewSection(
    settings: MessageSettings,
    selectedMainStyle: MessageStyle,
) {
    SettingsSectionTitle(stringResource(R.string.message_style_section_preview))
    SettingsCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            when (selectedMainStyle) {
                MessageStyle.FloatIcon -> FloatIconStylePreview(settings)
                MessageStyle.DarkCard -> CardStylePreview(settings)
                MessageStyle.SideBubble -> SideBubbleStylePreview(settings)
                MessageStyle.Danmaku -> DanmakuStylePreview(settings)
            }
            if (settings.danmakuEnabled && selectedMainStyle != MessageStyle.Danmaku) {
                DanmakuStylePreview(
                    settings = settings,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.FloatIconStylePreview(settings: MessageSettings) {
    val size = settings.floatIconSizeDp.coerceIn(32f, 64f).dp
        .coerceAtMost(56.dp)
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 20.dp)
            .size(size)
            .alpha(settings.floatIconOpacity.coerceIn(0f, 1f))
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.95f))
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_notification),
            contentDescription = null,
            modifier = Modifier.size(size * 0.66f),
        )
        Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(size * 0.34f)
                .clip(CircleShape),
        )
    }
}

@Composable
private fun BoxScope.CardStylePreview(settings: MessageSettings) {
    val theme = MessageThemeCatalog.themeFor(MessageStyle.DarkCard, settings.themeId)
    Row(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .alpha(theme.overlayAlpha(settings.cardOpacity))
            .clip(RoundedCornerShape(theme.cornerRadiusDp.dp))
            .messageThemeBackground(theme, settings.cardOpacity)
            .padding(horizontal = theme.paddingHorizontalDp.dp, vertical = theme.paddingVerticalDp.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.message_style_preview_title),
                color = Color(theme.titleColorArgb),
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.message_style_preview_content),
                color = Color(theme.contentColorArgb),
                fontSize = 14.sp,
                maxLines = settings.cardMaxLines.coerceIn(1, 3),
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BoxScope.SideBubbleStylePreview(settings: MessageSettings) {
    val theme = MessageThemeCatalog.themeFor(MessageStyle.SideBubble, settings.themeId)
    Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 24.dp, end = 12.dp)
            .widthIn(max = settings.sideMaxWidthDp.dp)
            .alpha(theme.overlayAlpha(settings.sideBubbleOpacity))
            .clip(RoundedCornerShape(theme.cornerRadiusDp.dp))
            .messageThemeBackground(theme, settings.sideBubbleOpacity)
            .padding(horizontal = theme.paddingHorizontalDp.dp, vertical = theme.paddingVerticalDp.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = null,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape),
        )
        Column {
            Text(
                text = stringResource(R.string.message_style_preview_title),
                color = Color(theme.titleColorArgb),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.message_style_preview_content),
                color = Color(theme.contentColorArgb),
                fontSize = 12.sp,
                maxLines = settings.sideMaxLines.coerceIn(1, 3),
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DanmakuStylePreview(
    settings: MessageSettings,
    modifier: Modifier = Modifier,
) {
    val theme = MessageThemeCatalog.themeFor(MessageStyle.Danmaku, settings.danmakuThemeId)
    Text(
        text = stringResource(R.string.message_style_preview_danmaku),
        modifier = modifier
            .padding(top = 16.dp)
            .alpha(theme.overlayAlpha(settings.danmakuOpacity))
            .clip(RoundedCornerShape(theme.cornerRadiusDp.dp))
            .messageThemeBackground(theme, settings.danmakuOpacity)
            .border(1.dp, Color(theme.titleColorArgb).copy(alpha = 0.2f), RoundedCornerShape(theme.cornerRadiusDp.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = Color(theme.titleColorArgb),
        fontSize = 14.sp,
        maxLines = settings.danmakuMaxLines.coerceAtLeast(1),
        overflow = TextOverflow.Ellipsis,
    )
}
