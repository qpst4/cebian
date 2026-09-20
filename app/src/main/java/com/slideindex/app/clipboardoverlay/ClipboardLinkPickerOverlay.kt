package com.slideindex.app.clipboardoverlay

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.LocalFrostedGlassBackdrop
import com.slideindex.app.overlay.MessageOverlayHost
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.OverlayViewBackHandler
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.search.SearchEngineLauncher
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val PICKER_ANIM_MS = 280

internal object ClipboardLinkPickerOverlay {
    private const val TAG = "ClipboardLinkPicker"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var owner: OverlayComposeOwner? = null
    private var backHandler: OverlayViewBackHandler? = null
    private var panelVisible: MutableState<Boolean>? = null
    private var onHidden: (() -> Unit)? = null
    private val exitRunnable = Runnable {
        val extra = onHidden
        onHidden = null
        removeWindow()
        extra?.invoke()
    }

    val isShowing: Boolean
        get() = composeView?.isAttachedToWindow == true

    fun show(context: Context, urls: List<String>) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(context, urls) }
            return
        }
        val distinct = urls.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty()) return
        dismissImmediate()
        val host = MessageOverlayHost.resolveHostContext(context) ?: return
        val overlayContext = OverlayCompose.themedContext(host)
        val wm = host.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val dialogOwner = OverlayComposeOwner()
        val view = OverlayCompose.createComposeView(overlayContext, dialogOwner)
        val visibleState = mutableStateOf(false)
        panelVisible = visibleState
        view.setContent {
            OverlayAwareModuleTheme {
                ClipboardLinkPickerContent(
                    visible = visibleState.value,
                    urls = distinct,
                    onOpen = { url ->
                        dismiss { openUrl(host, url) }
                    },
                    onOpenAll = {
                        dismiss { ClipboardLinkOpenAllActivity.start(host, distinct) }
                    },
                    onDismiss = { dismiss() },
                )
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.contentPanelWindowType(host),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            title = "ClipboardLinkPicker"
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
        }
        val added = runCatching { wm.addView(view, params) }
            .onFailure { Log.e(TAG, "addView failed", it) }
            .isSuccess
        if (!added) {
            panelVisible = null
            dialogOwner.destroy()
            return
        }
        windowManager = wm
        composeView = view
        owner = dialogOwner
        backHandler = OverlayViewBackHandler(view) { dismiss() }.also { it.attach() }
        view.requestFocus()
        view.post { visibleState.value = true }
    }

    fun dismiss(afterHide: (() -> Unit)? = null) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss(afterHide) }
            return
        }
        if (composeView == null) {
            afterHide?.invoke()
            return
        }
        val visible = panelVisible
        if (visible == null || !visible.value) return
        onHidden = afterHide
        visible.value = false
        mainHandler.removeCallbacks(exitRunnable)
        mainHandler.postDelayed(exitRunnable, PICKER_ANIM_MS.toLong())
    }

    fun dismissImmediate() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissImmediate() }
            return
        }
        mainHandler.removeCallbacks(exitRunnable)
        onHidden = null
        removeWindow()
    }

    private fun removeWindow() {
        backHandler?.detach()
        backHandler = null
        val wm = windowManager
        val view = composeView
        if (wm != null && view != null) {
            runCatching { wm.removeView(view) }
        }
        OverlayCompose.teardownOverlayCompose(composeView, owner)
        owner = null
        composeView = null
        windowManager = null
        panelVisible = null
    }

    private fun openUrl(context: Context, url: String) {
        val settings = OverlayDependencyAccess.overlayDependencies(context)
            ?.settingsRepository?.readSnapshot()
            ?: AppSettings()
        val opened = runCatching {
            SearchEngineLauncher.launchOpenableUri(context, url, settings)
        }.getOrDefault(false)
        if (!opened) {
            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
        }
    }
}

private data class LinkHandlerInfo(
    val label: String?,
    val icon: androidx.compose.ui.graphics.ImageBitmap?,
)

@Composable
private fun ClipboardLinkPickerContent(
    visible: Boolean,
    urls: List<String>,
    onOpen: (String) -> Unit,
    onOpenAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = LocalDensity.current
    val isDark = LocalAppDarkTheme.current
    val handler = remember(urls.firstOrNull()) {
        resolveHandler(context, urls.firstOrNull().orEmpty())
    }
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val cornerPx = with(density) { 28.dp.toPx() }
    val sheetTint = if (isDark) 0x991C1C1E.toInt() else 0x99F5F5F7.toInt()
    val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    val scrimInteraction = remember { MutableInteractionSource() }
    val sheetInteraction = remember { MutableInteractionSource() }
    val fadeSpec = tween<Float>(PICKER_ANIM_MS, easing = FastOutSlowInEasing)
    val slideSpec = tween<IntOffset>(PICKER_ANIM_MS, easing = FastOutSlowInEasing)

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(fadeSpec),
            exit = fadeOut(fadeSpec),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f))
                    .clickable(
                        indication = null,
                        interactionSource = scrimInteraction,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(fadeSpec) + slideInVertically(slideSpec) { it },
            exit = fadeOut(fadeSpec) + slideOutVertically(slideSpec) { it },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(sheetShape)
                    .clickable(
                        indication = null,
                        interactionSource = sheetInteraction,
                        onClick = {},
                    )
                    .navigationBarsPadding(),
            ) {
                Box {
                    LocalFrostedGlassBackdrop(
                        modifier = Modifier.matchParentSize(),
                        cornerRadiusPx = cornerPx,
                        blurRadiusPx = 24,
                        tintColor = sheetTint,
                        enabled = true,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.clipboard_overlay_pick_links_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.clipboard_overlay_pick_links_count, urls.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxListHeight),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 8.dp),
                        ) {
                            items(urls, key = { it }) { url ->
                                LinkRow(
                                    url = url,
                                    handler = handler,
                                    onClick = { onOpen(url) },
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text(stringResource(R.string.clipboard_overlay_pick_links_cancel))
                            }
                            Button(
                                onClick = onOpenAll,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = CircleShape,
                            ) {
                                Text(stringResource(R.string.clipboard_overlay_pick_links_open_all))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkRow(
    url: String,
    handler: LinkHandlerInfo,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = handler.icon
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
            Image(
                painter = painterResource(R.drawable.ic_clipboard_overlay_open),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondaryContainer),
            )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            AutoScrollingUrlText(
                text = displayUrl(url),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
            val handlerLabel = handler.label
            if (!handlerLabel.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.clipboard_overlay_open_with, handlerLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun displayUrl(url: String): String {
    val withoutScheme = url.substringAfter("://", missingDelimiterValue = url)
    return withoutScheme.ifBlank { url }
}

@Composable
private fun AutoScrollingUrlText(
    text: String,
    color: Color,
    style: TextStyle,
) {
    val scrollState = rememberScrollState()
    var userHolding by remember { mutableStateOf(false) }

    LaunchedEffect(text, userHolding) {
        if (userHolding) return@LaunchedEffect
        delay(700)
        while (isActive) {
            val max = scrollState.maxValue
            if (max <= 0) {
                delay(400)
                continue
            }
            if (scrollState.value < max) {
                val duration = ((max - scrollState.value) * 16).coerceIn(1800, 12000)
                scrollState.animateScrollTo(max, tween(duration, easing = LinearEasing))
                delay(900)
            } else {
                scrollState.animateScrollTo(0, tween(500))
                delay(800)
            }
        }
    }

    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .pointerInput(text) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    userHolding = true
                    waitForUpOrCancellation()
                    userHolding = false
                }
            },
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

private fun resolveHandler(context: Context, url: String): LinkHandlerInfo {
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val resolved = runCatching {
        context.packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))
    }.getOrNull() ?: return LinkHandlerInfo(null, null)
    val label = runCatching { resolved.loadLabel(context.packageManager).toString() }.getOrNull()
    val icon = runCatching {
        val drawable = resolved.loadIcon(context.packageManager) ?: return@runCatching null
        val size = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
        drawable.toBitmap(width = size, height = size).asImageBitmap()
    }.getOrNull()
    return LinkHandlerInfo(label, icon)
}
