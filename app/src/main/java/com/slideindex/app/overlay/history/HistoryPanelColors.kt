package com.slideindex.app.overlay.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 收纳面板专用色：Miuix 浅色下 background 与 surfaceContainer 均为白，
 * 须用 surface（灰底）+ surfaceContainer（白卡）才能看出卡片边界。
 */
internal object HistoryPanelColors {
    @Composable
    fun panelChrome(blurActive: Boolean = false): Color {
        val scheme = MiuixTheme.colorScheme
        return if (blurActive) {
            scheme.surfaceContainer.copy(alpha = 0.72f)
        } else {
            scheme.surfaceContainer
        }
    }

    @Composable
    fun listBackground(blurActive: Boolean = false): Color {
        val scheme = MiuixTheme.colorScheme
        return if (blurActive) {
            scheme.surface.copy(alpha = 0.65f)
        } else {
            scheme.surface
        }
    }

    @Composable
    fun cardBackground(starred: Boolean): Color {
        val scheme = MiuixTheme.colorScheme
        return if (starred) {
            scheme.primaryVariant.copy(alpha = 0.35f)
        } else {
            scheme.surfaceContainer
        }
    }
}
