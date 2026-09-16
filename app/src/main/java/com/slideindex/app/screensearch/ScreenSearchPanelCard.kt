package com.slideindex.app.screensearch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.screensearch.ScreenSearchCapture.ScrollDirection
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixInsetCardComponentMargin
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixOverlayComposeLocals
import com.slideindex.app.ui.miuix.MiuixSwitchRow
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ScreenSearchPanelCard(
    state: ScreenSearchPanelState,
    requestInitialFocus: Boolean,
    onStartScroll: (ScrollDirection) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) {
            focusRequester.requestFocus()
        }
    }

    MiuixOverlayComposeLocals {
        Card(
            modifier = modifier
                .padding(horizontal = 12.dp, vertical = 20.dp)
                .widthIn(max = 360.dp),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
            ),
            insideMargin = PaddingValues(16.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SmallTitle(
                    text = stringResource(R.string.gesture_action_screen_search),
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(bottom = 4.dp),
                )
                MiuixLabeledTextField(
                    value = state.keyword,
                    onValueChange = { state.keyword = it },
                    label = stringResource(R.string.screen_search_keyword_hint),
                    modifier = Modifier.focusRequester(focusRequester),
                )
                MiuixSwitchRow(
                    title = stringResource(R.string.screen_search_accessibility_mode),
                    checked = state.accessibilityMode,
                    insideMargin = MiuixInsetCardComponentMargin,
                    onCheckedChange = { state.accessibilityMode = it },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            state.scrollDirection = ScrollDirection.UP
                            onStartScroll(ScrollDirection.UP)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isSearching,
                    ) {
                        Text(text = stringResource(R.string.screen_search_scroll_up_action))
                    }
                    Button(
                        onClick = {
                            state.scrollDirection = ScrollDirection.DOWN
                            onStartScroll(ScrollDirection.DOWN)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isSearching,
                    ) {
                        Text(text = stringResource(R.string.screen_search_scroll_down_action))
                    }
                }
                MiuixHintText(
                    text = stringResource(
                        if (state.isSearching) {
                            R.string.screen_search_status_searching
                        } else {
                            R.string.screen_search_status_idle
                        },
                    ),
                    horizontalPadding = 0.dp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        text = stringResource(R.string.screen_search_close),
                        onClick = onClose,
                    )
                }
            }
        }
    }
}
