package com.slideindex.app.ui.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator

/** 设置子页转场占位：Miuix 灰色圆环点状指示器，与 Mishka / 系统设置一致。 */
@Composable
fun SettingsDeferredLoadingIndicator(modifier: Modifier = Modifier) {
    InfiniteProgressIndicator(modifier = modifier)
}
