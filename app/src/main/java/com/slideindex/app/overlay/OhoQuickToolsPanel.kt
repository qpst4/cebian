package com.slideindex.app.overlay

import android.media.AudioManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pixel-mapped palette matching the Samsung One Hand Operation+ quick-tools panel. */
private object OhoColors {
    val PanelBackground = Color(0xD82D2D30) // rgba(45,45,48,0.85)
    val PanelBorder = Color(0x1FFFFFFF)
    val CardBackground = Color(0xFF222224) // rgb(34,34,36)
    val RecordRed = Color(0xFFEB3323)
    val TileInactiveBg = Color(0x1AFFFFFF) // rgba(255,255,255,0.1)
    val TileInactiveIcon = Color(0xB3FFFFFF) // rgba(255,255,255,0.7)
    val TileActiveBg = Color(0xFFFFFFFF)
    val TileActiveIcon = Color(0xFF000000)
    val SliderTrack = Color(0x33FFFFFF)
    val SliderFill = Color(0xFFFFFFFF)
}

private val tileOrder = listOf(
    OhoTile.WIFI, OhoTile.MOBILE_DATA, OhoTile.SOUND, OhoTile.BLUETOOTH,
    OhoTile.QUICK_PANEL, OhoTile.SCREENSHOT, OhoTile.SCREEN_RECORD, OhoTile.LOCK,
    OhoTile.FLASHLIGHT, OhoTile.DO_NOT_DISTURB, OhoTile.AUTO_ROTATE, OhoTile.CLOSE,
)

@Composable
fun OhoQuickToolsPanel(
    state: OhoQuickToolsPanelState,
    visible: Boolean,
    onEvent: (OhoPanelEvent) -> Unit,
    modifier: Modifier = Modifier,
    side: PanelSide? = null,
) {
    val density = LocalDensity.current
    val screenWidthDp = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val panelWidth = (screenWidthDp * 0.62f).let { if (it > 252.dp) 252.dp else it }
    val outerCorner = panelWidth * 0.08f
    val cardCorner = outerCorner * 0.75f
    var panelWidthPx by remember { mutableIntStateOf(0) }
    val enterProgress = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            enterProgress.snapTo(0f)
            enterProgress.animateTo(1f, OverlayPanelEnterAnimation.enterSpec)
        } else {
            enterProgress.animateTo(0f, OverlayPanelEnterAnimation.exitSpec)
        }
    }

    if (enterProgress.value <= 0.001f && !visible) return

    val marginPx = with(density) { OverlayPanelEnterAnimation.OFFSCREEN_MARGIN.toPx() }
    val effectivePanelWidthPx = if (panelWidthPx > 0) {
        panelWidthPx.toFloat()
    } else {
        with(density) { panelWidth.toPx() }
    }
    val slideOffsetPx = OverlayPanelEnterAnimation.slideOffsetPx(
        progress = enterProgress.value,
        panelWidthPx = effectivePanelWidthPx,
        marginPx = marginPx,
        side = side,
    )
    val panelAlpha = OverlayPanelEnterAnimation.alpha(enterProgress.value)

    Box(
        modifier = modifier
            .width(panelWidth)
            .onSizeChanged { panelWidthPx = it.width }
            .graphicsLayer {
                alpha = panelAlpha
                translationX = slideOffsetPx
            }
            .shadow(elevation = 28.dp, shape = RoundedCornerShape(outerCorner), clip = false)
            .clip(RoundedCornerShape(outerCorner))
            .background(OhoColors.PanelBackground)
            .border(width = 1.dp, color = OhoColors.PanelBorder, shape = RoundedCornerShape(outerCorner))
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TopBrightnessRow(
                fraction = state.brightnessFraction,
                autoBrightnessEnabled = state.autoBrightnessEnabled,
                onFractionChange = { f, previewOnly -> state.updateBrightness(f, previewOnly) },
                onAutoBrightnessClick = { onEvent(OhoPanelEvent.ToggleAutoBrightness) },
            )
            MediaControlCard(
                cornerRadius = cardCorner,
                volumeFraction = state.volumeFraction,
                mediaAppPackage = state.mediaAppPackage,
                mediaIsPlaying = state.mediaIsPlaying,
                mediaListenerEnabled = state.mediaListenerEnabled,
                onVolumeChange = { f -> state.updateVolume(f) },
                onEvent = onEvent,
            )
            ToolsGrid(state = state, onEvent = onEvent)
        }
    }
}

@Composable
private fun TopBrightnessRow(
    fraction: Float,
    autoBrightnessEnabled: Boolean,
    onFractionChange: (Float, Boolean) -> Unit,
    onAutoBrightnessClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (autoBrightnessEnabled) OhoColors.TileActiveBg else OhoColors.TileInactiveBg,
        label = "autoBrightnessBg",
    )
    val iconTint by animateColorAsState(
        targetValue = if (autoBrightnessEnabled) OhoColors.TileActiveIcon else Color.White,
        label = "autoBrightnessIcon",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OhoSlider(
            fraction = fraction,
            onFractionChange = { f, previewOnly -> onFractionChange(f, previewOnly) },
            modifier = Modifier.weight(1f),
            height = 22.dp,
        )
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(bgColor)
                .clickable(onClick = onAutoBrightnessClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_brightness_auto),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun MediaControlCard(
    cornerRadius: Dp,
    volumeFraction: Float,
    mediaAppPackage: String?,
    mediaIsPlaying: Boolean,
    mediaListenerEnabled: Boolean,
    onVolumeChange: (Float) -> Unit,
    onEvent: (OhoPanelEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(OhoColors.CardBackground)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OhoSlider(
                fraction = volumeFraction,
                onFractionChange = { f, _ -> onVolumeChange(f) },
                modifier = Modifier.weight(1f),
                height = 22.dp,
            )
            MediaAppButton(
                packageName = mediaAppPackage,
                needsMediaAccess = !mediaListenerEnabled && mediaAppPackage.isNullOrBlank(),
                onClick = { onEvent(OhoPanelEvent.OpenMediaApp) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaGlyphButton(Icons.Default.SkipPrevious) { onEvent(OhoPanelEvent.MediaPrevious) }
            MediaGlyphButton(
                if (mediaIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            ) { onEvent(OhoPanelEvent.MediaPlayPause) }
            MediaGlyphButton(Icons.Default.SkipNext) { onEvent(OhoPanelEvent.MediaNext) }
            MediaGlyphButton(Icons.Default.KeyboardArrowUp) { onEvent(OhoPanelEvent.ChevronUp) }
            MediaGlyphButton(Icons.Default.KeyboardArrowDown) { onEvent(OhoPanelEvent.ChevronDown) }
        }
    }
}

@Composable
private fun MediaAppButton(
    packageName: String?,
    needsMediaAccess: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    var iconBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(packageName) {
        iconBitmap = if (packageName.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager.getApplicationIcon(packageName).toBitmap(128, 128)
                }.getOrNull()
            }
        }
    }
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(
                when {
                    iconBitmap != null -> Color.Transparent
                    needsMediaAccess -> OhoColors.RecordRed.copy(alpha = 0.35f)
                    else -> OhoColors.TileInactiveBg
                },
            )
            .border(
                width = if (iconBitmap != null) 1.5.dp else 0.dp,
                color = if (iconBitmap != null) Color.White.copy(alpha = 0.35f) else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            iconBitmap != null -> Image(
                bitmap = iconBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape),
            )
            needsMediaAccess -> Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            else -> Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = OhoColors.TileInactiveIcon,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun RowScope.MediaGlyphButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ToolsGrid(state: OhoQuickToolsPanelState, onEvent: (OhoPanelEvent) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val tileDiameter = maxWidth * 0.17f
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tileOrder.chunked(4).forEach { rowTiles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rowTiles.forEach { tile ->
                        OhoTileButton(
                            tile = tile,
                            diameter = tileDiameter,
                            active = state.isActive(tile),
                            ringerMode = if (tile == OhoTile.SOUND) state.ringerMode else null,
                            onClick = { onEvent(OhoPanelEvent.Tile(tile)) },
                            onLongClick = if (tile in QuickToolsTileSettings.longPressTiles) {
                                { onEvent(OhoPanelEvent.TileLongPress(tile)) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OhoTileButton(
    tile: OhoTile,
    diameter: Dp,
    active: Boolean,
    ringerMode: Int? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            tile == OhoTile.SCREEN_RECORD && active -> OhoColors.RecordRed
            active -> OhoColors.TileActiveBg
            else -> OhoColors.TileInactiveBg
        },
        label = "ohoTileBg",
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            tile == OhoTile.SCREEN_RECORD && active -> Color.White
            active -> OhoColors.TileActiveIcon
            else -> OhoColors.TileInactiveIcon
        },
        label = "ohoTileIcon",
    )
    Box(
        modifier = Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        OhoTileGlyph(tile = tile, diameter = diameter, tint = iconTint, active = active, ringerMode = ringerMode)
    }
}

@Composable
private fun OhoTileGlyph(
    tile: OhoTile,
    diameter: Dp,
    tint: Color,
    active: Boolean,
    ringerMode: Int? = null,
) {
    val iconSize = diameter * when (tile) {
        OhoTile.SCREEN_RECORD -> 0.64f
        else -> 0.58f
    }
    when (tile) {
        OhoTile.QUICK_PANEL -> Icon(
            imageVector = OhoPanelIcons.NotificationShade,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
        OhoTile.SCREEN_RECORD -> Icon(
            imageVector = OhoPanelIcons.ScreenRecord,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
        OhoTile.DO_NOT_DISTURB -> Icon(
            painter = painterResource(if (active) R.drawable.ic_dnd_on else R.drawable.ic_dnd_off),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
        OhoTile.SOUND -> Icon(
            painter = painterResource(RingerModeIconRenderer.iconResFor(ringerMode ?: AudioManager.RINGER_MODE_NORMAL)),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
        else -> Icon(
            imageVector = ohoTileIcon(tile),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

private fun ohoTileIcon(tile: OhoTile): ImageVector = when (tile) {
    OhoTile.WIFI -> Icons.Default.Wifi
    OhoTile.MOBILE_DATA -> Icons.Default.SignalCellularAlt
    OhoTile.SOUND -> Icons.Default.MusicNote // unused; custom painter above
    OhoTile.BLUETOOTH -> Icons.Default.Bluetooth
    OhoTile.AUTO_ROTATE -> Icons.Default.ScreenRotation
    OhoTile.QUICK_PANEL -> Icons.Default.MusicNote // unused; custom painter above
    OhoTile.SCREENSHOT -> Icons.Default.Screenshot
    OhoTile.LOCK -> Icons.Default.Lock
    OhoTile.FLASHLIGHT -> Icons.Default.FlashlightOn
    OhoTile.DO_NOT_DISTURB -> Icons.Default.Settings // unused; custom painter above
    OhoTile.SCREEN_RECORD -> Icons.Default.MusicNote // unused; custom painter above
    OhoTile.CLOSE -> Icons.Default.Close
}

@Composable
private fun OhoSlider(
    fraction: Float,
    onFractionChange: (Float, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 26.dp,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(OhoColors.SliderTrack)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    onFractionChange(down.position.x / size.width, true)
                    var pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) {
                            onFractionChange(change.position.x / size.width, false)
                            break
                        }
                        change.consume()
                        pointerId = change.id
                        onFractionChange(change.position.x / size.width, true)
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(50))
                .background(OhoColors.SliderFill),
        )
    }
}
