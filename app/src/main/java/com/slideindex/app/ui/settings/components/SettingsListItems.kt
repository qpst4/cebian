package com.slideindex.app.ui.settings.components

/**
 * Mishka-style settings list helpers: [groupedCardItems] inside [LazyListScope].
 * State for switches / conditional rows must be read inside [settingsCardItem] content lambdas.
 */
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.MiuixSmallTitle
import com.slideindex.app.ui.miuix.MiuixSmallTitleSectionTop
import com.slideindex.app.ui.miuix.groupedCardItems
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Built card rows for lazy lists or [RenderRows] in non-lazy surfaces. */
class SettingsCardItems internal constructor(
    internal val coordinator: SettingsCardGroupCoordinator,
) {
    @Composable
    fun RenderRows() {
        coordinator.RenderRows()
    }
}

/** Single lazy card segment; reads state inside [content] at compose time. */
fun settingsCardItem(
    key: String,
    content: @Composable () -> Unit,
): CardItem = CardItem(key) {
    SettingsCardSegmentContent(content = content)
}

@Composable
internal fun SettingsCardSegmentContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSettingsCardSegmentMode provides true,
        LocalContentColor provides MiuixTheme.colorScheme.onSurfaceContainer,
    ) {
        content()
    }
}

/** Run [SettingsCardScope] row helpers inside a card segment (single row). */
fun settingsCardScopeItem(
    key: String,
    content: @Composable SettingsCardScope.() -> Unit,
): CardItem = settingsCardItem(key) {
    SettingsCardScopeContent(content)
}

/** Run [SettingsCardScope] row helpers inside a card segment (single row). */
@Composable
fun SettingsCardScopeContent(content: @Composable SettingsCardScope.() -> Unit) {
    val scope = remember { SettingsCardScope() }
    SettingsCardSegmentContent {
        scope.content()
    }
}

/**
 * Collect multi-row card content at @Composable scope (for [RenderRows] in non-lazy surfaces).
 * Prefer [settingsCardItem] / [groupedCardItems] inside [LazyListScope] for lazy settings screens.
 */
@Composable
fun settingsCardItems(
    content: @Composable SettingsCardScope.() -> Unit,
): SettingsCardItems = settingsCardItemsInternal(keys = emptyArray(), content = content)

@Composable
fun settingsCardItems(
    vararg keys: Any?,
    content: @Composable SettingsCardScope.() -> Unit,
): SettingsCardItems = settingsCardItemsInternal(keys = keys, content = content)

@Composable
private fun settingsCardItemsInternal(
    keys: Array<out Any?>,
    content: @Composable SettingsCardScope.() -> Unit,
): SettingsCardItems {
    val coordinator = remember { SettingsCardGroupCoordinator() }
    val scope = remember { SettingsCardScope() }
    remember(*keys) { Unit }
    coordinator.clear()
    CompositionLocalProvider(
        LocalSettingsCardScope provides scope,
        LocalSettingsCardGroupCoordinator provides coordinator,
    ) {
        scope.content()
    }
    return SettingsCardItems(coordinator)
}

fun LazyListScope.settingsGroupedCardItems(
    keyPrefix: String,
    items: List<CardItem>,
    selectableGroup: Boolean = false,
) {
    groupedCardItems(
        keyPrefix = keyPrefix,
        items = items,
        selectableGroup = selectableGroup,
    )
}

/** Non-card lazy item wrapper for custom sections (grids, scrollable bodies, etc.). */
fun LazyListScope.LazySettingsItem(
    key: String,
    fillParentMaxSize: Boolean = false,
    content: @Composable () -> Unit,
) {
    item(key = key) {
        if (fillParentMaxSize) {
            Box(Modifier.fillParentMaxSize()) {
                content()
            }
        } else {
            content()
        }
    }
}

fun LazyListScope.settingsLazySmallTitle(
    key: String,
    title: String,
    sectionTop: Boolean = false,
) {
    item(key = key) {
        MiuixSmallTitle(
            title,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (sectionTop) {
                        Modifier.padding(top = MiuixSmallTitleSectionTop)
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

fun LazyListScope.settingsLazyHint(
    key: String,
    text: String,
) {
    item(key = key) {
        com.slideindex.app.ui.miuix.MiuixHintText(text)
    }
}
