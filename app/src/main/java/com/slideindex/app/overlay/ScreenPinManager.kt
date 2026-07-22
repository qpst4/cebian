package com.slideindex.app.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardBlockKind
import com.slideindex.app.clipboard.ClipboardEntry
import com.slideindex.app.clipboard.ClipboardImageStore
import com.slideindex.app.clipboard.resolvedContentBlocks
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.stash.PinNotificationSnapshot
import com.slideindex.app.stash.StashCoordinator
import com.slideindex.app.stash.StashPinNotificationHelper
import com.slideindex.app.ui.theme.SlideIndexTheme
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

private enum class ScreenPinDropZone {
    NONE,
    STASH,
    DELETE,
    NOTIFICATION,
}

private const val TEXT_PIN_MAX_WIDTH_FRACTION = 0.44f
private const val RICH_PIN_MAX_HEIGHT_FRACTION = 0.5f
private const val RICH_PIN_IMAGE_MAX_HEIGHT_DP = 220f
private const val PIN_CONTROL_ALPHA_MIN = 0.1f
private const val EDGE_DOCK_SIZE_DP = 50f
private const val EDGE_SNAP_ANIM_MS = 200L
private const val PIN_CONTROL_BAR_MIN_WIDTH_DP = 188f
private const val PIN_CONTROL_BAR_SLOT_HEIGHT_DP = 56f
private const val DROP_TARGET_HIT_SLOP_DP = 10f
private const val DROP_TARGET_CHIP_WIDTH_DP = 100f
private const val DROP_TARGET_CHIP_HEIGHT_DP = 52f
private const val DROP_TARGET_EDGE_PADDING_DP = 8f

private fun pinOverlayFlags(): Int {
    return WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
}

private fun viewToScreen(view: android.view.View, localX: Float, localY: Float): Pair<Float, Float> {
    val loc = IntArray(2)
    view.getLocationOnScreen(loc)
    return loc[0] + localX to loc[1] + localY
}

private fun overlayBoundsToScreen(overlayView: android.view.View, boundsInOverlay: Rect): Rect {
    if (boundsInOverlay.isEmpty) return boundsInOverlay
    val loc = IntArray(2)
    overlayView.getLocationOnScreen(loc)
    return Rect(
        loc[0] + boundsInOverlay.left,
        loc[1] + boundsInOverlay.top,
        loc[0] + boundsInOverlay.right,
        loc[1] + boundsInOverlay.bottom,
    )
}

private data class PinInitialPlacement(
    val x: Int,
    val y: Int,
    val expandedWidthPx: Int = 0,
    val expandedHeightPx: Int = 0,
)

private class PinUiState {
    val showControls: MutableState<Boolean> = mutableStateOf(false)
    val contentAlpha: MutableFloatState = mutableFloatStateOf(1f)
    var expandedWidthPx: Int = 0
    var expandedHeightPx: Int = 0
    val displayWidthPx = mutableIntStateOf(0)
    val displayHeightPx = mutableIntStateOf(0)
    val isEdgeDocked = mutableStateOf(false)
    var animator: ValueAnimator? = null
}

private sealed class PinContent {
    data class Text(val body: String) : PinContent()
    data class Image(
        val bitmap: Bitmap,
        val screenRect: Rect? = null,
        val layoutMeta: ScreenshotLayoutMeta? = null,
    ) : PinContent()
    data class Rich(val blocks: List<PinDisplayBlock>) : PinContent()
}

private sealed class PinDisplayBlock {
    data class Text(val body: String) : PinDisplayBlock()
    data class Image(val bitmap: Bitmap) : PinDisplayBlock()
}

private class PinInstance(
    val id: String,
    val content: PinContent,
    val composeView: ComposeView,
    val owner: OverlayComposeOwner,
    val params: WindowManager.LayoutParams,
    val uiState: PinUiState,
    var offsetX: Int,
    var offsetY: Int,
)

private class DropOverlayState {
    var deleteBounds: Rect = Rect()
    var notifyBounds: Rect = Rect()
    var stashBounds: Rect = Rect()
    val highlighted = mutableStateOf(ScreenPinDropZone.NONE)
}

object ScreenPinManager {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var appContext: Context? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private val pins = linkedMapOf<String, PinInstance>()
    private var dropOverlay: DropOverlayState? = null
    private var dropComposeView: ComposeView? = null
    private var dropOwner: OverlayComposeOwner? = null

    fun pinText(context: Context, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        mainHandler.post { addPin(context, PinContent.Text(trimmed)) }
    }

    fun pinImage(
        context: Context,
        bitmap: Bitmap,
        screenRect: Rect? = null,
        layoutMeta: ScreenshotLayoutMeta? = null,
    ) {
        val copy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false) ?: return
        val rectCopy = screenRect?.let { Rect(it) }
        val metaCopy = layoutMeta?.copy()
        mainHandler.post { addPin(context, PinContent.Image(copy, rectCopy, metaCopy)) }
    }

    private fun pinRich(context: Context, blocks: List<PinDisplayBlock>) {
        if (blocks.isEmpty()) return
        val copies = blocks.mapNotNull { block ->
            when (block) {
                is PinDisplayBlock.Text -> {
                    val trimmed = block.body.trim()
                    if (trimmed.isEmpty()) null else PinDisplayBlock.Text(trimmed)
                }
                is PinDisplayBlock.Image -> {
                    val copy = block.bitmap.copy(block.bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                        ?: return@mapNotNull null
                    PinDisplayBlock.Image(copy)
                }
            }
        }
        if (copies.isEmpty()) return
        mainHandler.post { addPin(context, PinContent.Rich(copies)) }
    }

    fun pinClipboardEntry(context: Context, entry: ClipboardEntry) {
        val pinBlocks = entry.resolvedContentBlocks().mapNotNull { block ->
            when (block.kind) {
                ClipboardBlockKind.TEXT -> {
                    val trimmed = block.text.trim()
                    if (trimmed.isEmpty()) null else PinDisplayBlock.Text(trimmed)
                }
                ClipboardBlockKind.IMAGE -> {
                    val loaded = ClipboardImageStore.loadBitmap(context, block.fileName) ?: return@mapNotNull null
                    val copy = loaded.copy(loaded.config ?: Bitmap.Config.ARGB_8888, false) ?: return@mapNotNull null
                    if (copy !== loaded) loaded.recycle()
                    PinDisplayBlock.Image(copy)
                }
            }
        }
        if (pinBlocks.isEmpty()) return
        mainHandler.post { addPin(context, PinContent.Rich(pinBlocks)) }
    }

    fun pinFromStashText(context: Context, text: String) = pinText(context, text)
    fun pinFromStashImage(
        context: Context,
        bitmap: Bitmap,
        displayWidthPx: Int? = null,
        displayHeightPx: Int? = null,
    ) {
        val copy = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false) ?: return
        mainHandler.post {
            val preferredW = displayWidthPx?.takeIf { it > 0 }
            val preferredH = displayHeightPx?.takeIf { it > 0 }
            if (preferredW != null && preferredH != null) {
                addPin(
                    context,
                    PinContent.Image(copy),
                    placement = PinInitialPlacement(
                        x = defaultPinX(context),
                        y = defaultPinY(context),
                        expandedWidthPx = preferredW,
                        expandedHeightPx = preferredH,
                    ),
                )
            } else {
                addPin(context, PinContent.Image(copy))
            }
        }
    }

    fun restoreFromNotification(
        context: Context,
        snapshot: PinNotificationSnapshot,
        imageBitmap: Bitmap?,
        onResult: (Boolean) -> Unit = {},
    ) {
        mainHandler.post {
            val placement = PinInitialPlacement(
                x = snapshot.x,
                y = snapshot.y,
                expandedWidthPx = snapshot.expandedWidthPx,
                expandedHeightPx = snapshot.expandedHeightPx,
            )
            val success = when (snapshot.type) {
                PinNotificationSnapshot.TYPE_IMAGE -> {
                    val source = imageBitmap ?: run {
                        onResult(false)
                        return@post
                    }
                    val bitmap = source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
                    if (bitmap == null) {
                        onResult(false)
                        return@post
                    }
                    addPin(
                        context,
                        PinContent.Image(
                            bitmap = bitmap,
                            screenRect = snapshot.toScreenRect(),
                            layoutMeta = snapshot.toLayoutMeta(),
                        ),
                        placement = placement,
                    )
                }
                PinNotificationSnapshot.TYPE_TEXT -> {
                    val text = snapshot.text?.trim().orEmpty()
                    if (text.isEmpty()) {
                        onResult(false)
                        return@post
                    }
                    addPin(context, PinContent.Text(text), placement = placement)
                }
                else -> false
            }
            onResult(success)
        }
    }

    fun dismissAll() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissAll() }
            return
        }
        pins.keys.toList().forEach { removePin(it) }
        hideDropOverlay()
    }

    private fun addPin(
        context: Context,
        content: PinContent,
        placement: PinInitialPlacement? = null,
    ): Boolean {
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: context.applicationContext
        ensureScreenOffReceiver(hostContext)
        val wm = windowManager ?: (
            hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        )?.also { windowManager = it } ?: return false

        val id = UUID.randomUUID().toString()
        val owner = OverlayComposeOwner()
        val composeView = OverlayCompose.createComposeView(hostContext, owner)
        val metrics = hostContext.resources.displayMetrics
        val imageDisplaySize = when (content) {
            is PinContent.Image -> {
                content.bitmap.density = metrics.densityDpi
                if (placement != null &&
                    placement.expandedWidthPx > 0 &&
                    placement.expandedHeightPx > 0
                ) {
                    placement.expandedWidthPx to placement.expandedHeightPx
                } else {
                    resolvePinImageDisplaySizePx(
                        bitmap = content.bitmap,
                        screenRect = content.screenRect,
                        layoutMeta = content.layoutMeta,
                        screenWidthPx = metrics.widthPixels,
                        screenHeightPx = metrics.heightPixels,
                    )
                }
            }
            else -> null
        }
        val uiState = PinUiState()
        val barMinW = controlBarWidthPx(metrics)
        val slotH = controlBarSlotHeightPx(metrics)
        val panelSize = imageDisplaySize?.let { (contentW, contentH) ->
            max(contentW, barMinW) to (contentH + slotH)
        }
        val params = WindowManager.LayoutParams(
            panelSize?.first ?: WindowManager.LayoutParams.WRAP_CONTENT,
            panelSize?.second ?: WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayWindowTypes.overlayWindowType(hostContext),
            pinOverlayFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (placement != null) {
                x = placement.x
                y = placement.y
            } else {
                val imageRect = (content as? PinContent.Image)?.screenRect
                if (imageRect != null && !imageRect.isEmpty) {
                    val contentW = imageDisplaySize?.first ?: imageRect.width().coerceAtLeast(1)
                    val panelW = max(contentW, barMinW)
                    x = imageRect.left - (panelW - contentW) / 2
                    y = imageRect.top
                } else {
                    x = (metrics.widthPixels * 0.08f).roundToInt()
                    y = (metrics.heightPixels * 0.22f).roundToInt() + pins.size * 32
                }
            }
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        if (imageDisplaySize != null) {
            uiState.expandedWidthPx = imageDisplaySize.first
            uiState.expandedHeightPx = imageDisplaySize.second
            uiState.displayWidthPx.intValue = imageDisplaySize.first
            uiState.displayHeightPx.intValue = imageDisplaySize.second
        }

        val instance = PinInstance(
            id = id,
            content = content,
            composeView = composeView,
            owner = owner,
            params = params,
            uiState = uiState,
            offsetX = params.x,
            offsetY = params.y,
        )
        pins[id] = instance
        bindPinContent(instance)
        val added = runCatching { wm.addView(composeView, params) }.isSuccess
        if (!added) {
            pins.remove(id)
            owner.destroy()
            Toast.makeText(hostContext, R.string.stash_pin_add_failed, Toast.LENGTH_SHORT).show()
            return false
        }
        if (content is PinContent.Text || content is PinContent.Rich) {
            composeView.post {
                if (instance.uiState.expandedWidthPx <= 0) {
                    instance.uiState.expandedWidthPx = composeView.width.coerceAtLeast(1)
                }
                if (instance.uiState.expandedHeightPx <= 0) {
                    instance.uiState.expandedHeightPx = composeView.height.coerceAtLeast(1)
                }
                if (instance.uiState.displayWidthPx.intValue <= 0) {
                    instance.uiState.displayWidthPx.intValue = instance.uiState.expandedWidthPx
                }
            }
        }
        appContext = hostContext
        return true
    }

    private fun defaultPinX(context: Context): Int {
        val metrics = context.resources.displayMetrics
        return (metrics.widthPixels * 0.08f).roundToInt()
    }

    private fun defaultPinY(context: Context): Int {
        val metrics = context.resources.displayMetrics
        return (metrics.heightPixels * 0.22f).roundToInt() + pins.size * 32
    }

    private fun bindPinContent(instance: PinInstance) {
        instance.composeView.setContent {
            SlideIndexTheme {
                ScreenPinContent(
                    instance = instance,
                    onTap = { onPinTap(instance.id) },
                    onDragStart = {
                        if (instance.uiState.isEdgeDocked.value) {
                            undockForDrag(instance)
                        }
                        showDropOverlay()
                    },
                    onDrag = { dx, dy, localX, localY ->
                        dragPin(instance.id, dx, dy)
                        val (screenX, screenY) = viewToScreen(instance.composeView, localX, localY)
                        updateDropHighlight(screenX, screenY)
                    },
                    onDragEnd = { localX, localY ->
                        val (screenX, screenY) = viewToScreen(instance.composeView, localX, localY)
                        finishPinDrag(instance.id, screenX, screenY)
                        hideDropOverlay()
                    },
                    onClose = { removePin(instance.id) },
                    onAlphaChange = { instance.uiState.contentAlpha.floatValue = it },
                )
            }
        }
    }

    private fun undockForDrag(instance: PinInstance) {
        instance.uiState.animator?.cancel()
        instance.uiState.isEdgeDocked.value = false
        instance.uiState.showControls.value = false
        if (instance.uiState.expandedWidthPx > 0) {
            instance.uiState.displayWidthPx.intValue = instance.uiState.expandedWidthPx
        }
        if (instance.uiState.expandedHeightPx > 0) {
            instance.uiState.displayHeightPx.intValue = instance.uiState.expandedHeightPx
        }
        if (instance.content is PinContent.Image) {
            applyImagePinWindowLayout(instance)
            runCatching { windowManager?.updateViewLayout(instance.composeView, instance.params) }
        }
    }

    private fun onPinTap(pinId: String) {
        val instance = pins[pinId] ?: return
        if (instance.uiState.isEdgeDocked.value) {
            restoreFromEdge(instance)
            return
        }
        instance.uiState.showControls.value = !instance.uiState.showControls.value
    }

    private fun removePin(pinId: String) {
        val instance = pins.remove(pinId) ?: return
        instance.uiState.animator?.cancel()
        val wm = windowManager
        if (wm != null) {
            runCatching { wm.removeView(instance.composeView) }
        }
        instance.owner.destroy()
        recyclePinBitmaps(instance.content)
        if (pins.isEmpty()) {
            unregisterScreenOffReceiver()
        }
    }

    private fun dragPin(pinId: String, dx: Float, dy: Float) {
        val instance = pins[pinId] ?: return
        instance.offsetX += dx.roundToInt()
        instance.offsetY += dy.roundToInt()
        instance.params.x = instance.offsetX
        instance.params.y = instance.offsetY
        runCatching { windowManager?.updateViewLayout(instance.composeView, instance.params) }
    }

    private fun finishPinDrag(pinId: String, fingerX: Float, fingerY: Float) {
        val instance = pins[pinId] ?: return
        val zone = resolveDropZone(fingerX, fingerY)
        when (zone) {
            ScreenPinDropZone.STASH -> stashPin(pinId)
            ScreenPinDropZone.DELETE -> removePin(pinId)
            ScreenPinDropZone.NOTIFICATION -> postPinToNotification(pinId)
            ScreenPinDropZone.NONE -> checkEdgeSnap(instance)
        }
    }

    private fun resolveDropZone(fingerX: Float, fingerY: Float): ScreenPinDropZone {
        val overlay = dropOverlay ?: return ScreenPinDropZone.NONE
        val metrics = appContext?.resources?.displayMetrics
        val fallback = metrics?.let { computeFallbackDropBounds(it) }
        val overlayView = dropComposeView
        val x = fingerX.roundToInt()
        val y = fingerY.roundToInt()
        val delete = screenDropBounds(overlayView, overlay.deleteBounds, fallback?.deleteBounds)
        val notify = screenDropBounds(overlayView, overlay.notifyBounds, fallback?.notifyBounds)
        val stash = screenDropBounds(overlayView, overlay.stashBounds, fallback?.stashBounds)
        if (delete != null && hitDropTarget(delete, x, y, metrics)) return ScreenPinDropZone.DELETE
        if (notify != null && hitDropTarget(notify, x, y, metrics)) return ScreenPinDropZone.NOTIFICATION
        if (stash != null && hitDropTarget(stash, x, y, metrics)) return ScreenPinDropZone.STASH
        return ScreenPinDropZone.NONE
    }

    private fun screenDropBounds(
        overlayView: ComposeView?,
        boundsInOverlay: Rect,
        fallback: Rect?,
    ): Rect? {
        if (overlayView != null && !boundsInOverlay.isEmpty) {
            return overlayBoundsToScreen(overlayView, boundsInOverlay)
        }
        return fallback?.takeUnless { it.isEmpty }
    }

    private fun hitDropTarget(bounds: Rect, x: Int, y: Int, metrics: DisplayMetrics?): Boolean {
        if (bounds.isEmpty) return false
        val slop = metrics?.let { (DROP_TARGET_HIT_SLOP_DP * it.density).roundToInt() } ?: 0
        return x >= bounds.left - slop &&
            x <= bounds.right + slop &&
            y >= bounds.top - slop &&
            y <= bounds.bottom + slop
    }

    private fun stashPin(pinId: String) {
        val instance = pins[pinId] ?: return
        val context = appContext ?: return
        when (val content = instance.content) {
            is PinContent.Text -> {
                StashCoordinator.addText(content.body) { success ->
                    if (success) {
                        Toast.makeText(context, R.string.stash_saved, Toast.LENGTH_SHORT).show()
                        StashCoordinator.openStashPanel(context)
                        removePin(pinId)
                    }
                }
            }
            is PinContent.Image -> {
                val displayW = instance.uiState.expandedWidthPx.takeIf { it > 0 }
                    ?: instance.uiState.displayWidthPx.intValue
                val displayH = instance.uiState.expandedHeightPx.takeIf { it > 0 }
                    ?: instance.uiState.displayHeightPx.intValue
                StashCoordinator.addImage(
                    bitmap = content.bitmap,
                    pinDisplayWidthPx = displayW,
                    pinDisplayHeightPx = displayH,
                ) { success ->
                    if (success) {
                        Toast.makeText(context, R.string.stash_saved, Toast.LENGTH_SHORT).show()
                        StashCoordinator.openStashPanel(context)
                        removePin(pinId)
                    }
                }
            }
            is PinContent.Rich -> {
                val combinedText = richCombinedText(content.blocks)
                val firstImage = content.blocks.filterIsInstance<PinDisplayBlock.Image>().firstOrNull()?.bitmap
                when {
                    combinedText.isNotBlank() -> {
                        StashCoordinator.addText(combinedText) { success ->
                            if (success) {
                                Toast.makeText(context, R.string.stash_saved, Toast.LENGTH_SHORT).show()
                                StashCoordinator.openStashPanel(context)
                                removePin(pinId)
                            }
                        }
                    }
                    firstImage != null -> {
                        StashCoordinator.addImage(bitmap = firstImage) { success ->
                            if (success) {
                                Toast.makeText(context, R.string.stash_saved, Toast.LENGTH_SHORT).show()
                                StashCoordinator.openStashPanel(context)
                                removePin(pinId)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun postPinToNotification(pinId: String) {
        val instance = pins[pinId] ?: return
        val context = appContext ?: return
        val snapshot = buildNotificationSnapshot(instance)
        val (title, displayBitmap) = when (val content = instance.content) {
            is PinContent.Image -> {
                context.getString(R.string.stash_pin_notification_title) to content.bitmap
            }
            is PinContent.Text -> {
                content.body.take(36) to textBitmap(context, content.body)
            }
            is PinContent.Rich -> {
                val combined = richCombinedText(content.blocks)
                val preview = content.blocks.filterIsInstance<PinDisplayBlock.Image>().firstOrNull()?.bitmap
                (combined.take(36).ifBlank { context.getString(R.string.stash_pin_notification_title) }) to
                    (preview ?: textBitmap(context, combined))
            }
        }
        StashPinNotificationHelper.postPinSnapshot(context, snapshot, displayBitmap, title)
        Toast.makeText(context, R.string.stash_pin_notification_posted, Toast.LENGTH_SHORT).show()
        removePin(pinId)
    }

    private fun buildNotificationSnapshot(instance: PinInstance): PinNotificationSnapshot {
        val rect = (instance.content as? PinContent.Image)?.screenRect
        val layoutMeta = (instance.content as? PinContent.Image)?.layoutMeta
        return when (val content = instance.content) {
            is PinContent.Image -> PinNotificationSnapshot(
                type = PinNotificationSnapshot.TYPE_IMAGE,
                x = instance.params.x,
                y = instance.params.y,
                expandedWidthPx = instance.uiState.expandedWidthPx,
                expandedHeightPx = instance.uiState.expandedHeightPx,
                screenRectLeft = rect?.left,
                screenRectTop = rect?.top,
                screenRectRight = rect?.right,
                screenRectBottom = rect?.bottom,
                layoutScreenWidth = layoutMeta?.screenWidth,
                layoutScreenHeight = layoutMeta?.screenHeight,
                layoutCaptureWidth = layoutMeta?.captureWidth,
                layoutCaptureHeight = layoutMeta?.captureHeight,
            )
            is PinContent.Text -> PinNotificationSnapshot(
                type = PinNotificationSnapshot.TYPE_TEXT,
                text = content.body,
                x = instance.params.x,
                y = instance.params.y,
                expandedWidthPx = instance.uiState.expandedWidthPx,
                expandedHeightPx = instance.uiState.expandedHeightPx,
            )
            is PinContent.Rich -> PinNotificationSnapshot(
                type = PinNotificationSnapshot.TYPE_TEXT,
                text = richCombinedText(content.blocks),
                x = instance.params.x,
                y = instance.params.y,
                expandedWidthPx = instance.uiState.expandedWidthPx,
                expandedHeightPx = instance.uiState.expandedHeightPx,
            )
        }
    }

    private fun richCombinedText(blocks: List<PinDisplayBlock>): String =
        blocks.filterIsInstance<PinDisplayBlock.Text>()
            .joinToString("\n\n") { it.body.trim() }
            .trim()

    private fun recyclePinBitmaps(content: PinContent) {
        when (content) {
            is PinContent.Image -> content.bitmap.recycle()
            is PinContent.Rich -> {
                content.blocks.filterIsInstance<PinDisplayBlock.Image>().forEach { it.bitmap.recycle() }
            }
            is PinContent.Text -> Unit
        }
    }

    private fun textBitmap(context: Context, text: String): Bitmap {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 42f
            color = android.graphics.Color.BLACK
        }
        val bounds = android.graphics.Rect()
        val sample = text.lineSequence().firstOrNull().orEmpty().take(24)
        paint.getTextBounds(sample, 0, sample.length, bounds)
        val bmp = Bitmap.createBitmap(
            bounds.width().coerceAtLeast(1) + 32,
            bounds.height().coerceAtLeast(1) + 32,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawText(sample, 16f, 16f + bounds.height(), paint)
        return bmp
    }

    private fun checkEdgeSnap(instance: PinInstance) {
        val context = appContext ?: return
        val metrics = context.resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val width = currentPanelWidth(instance)
        val height = currentPanelHeight(instance)
        val x = instance.params.x
        val y = instance.params.y

        if (instance.uiState.isEdgeDocked.value) {
            val substantiallyOnScreen = x > -width / 2 &&
                x + width < screenW + width / 2 &&
                y > -height / 2 &&
                y + height < screenH + height / 2
            if (substantiallyOnScreen) {
                restoreFromEdge(instance)
            }
            return
        }

        val threshold = -width / 3
        val rightGap = screenW - (x + width)
        val bottomGap = screenH - (y + height)
        when {
            x < threshold -> snapToEdge(instance, horizontal = true, toStart = true, metrics)
            rightGap < threshold -> snapToEdge(instance, horizontal = true, toStart = false, metrics)
            y < threshold -> snapToEdge(instance, horizontal = false, toStart = true, metrics)
            bottomGap < threshold -> snapToEdge(instance, horizontal = false, toStart = false, metrics)
        }
    }

    private fun snapToEdge(
        instance: PinInstance,
        horizontal: Boolean,
        toStart: Boolean,
        metrics: DisplayMetrics,
    ) {
        if (instance.uiState.isEdgeDocked.value) return
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val dockSize = (EDGE_DOCK_SIZE_DP * metrics.density).roundToInt().coerceAtLeast(1)
        val halfDock = dockSize / 2
        val expandedW = instance.uiState.expandedWidthPx.coerceAtLeast(currentPinWidth(instance))
        val expandedH = instance.uiState.expandedHeightPx.coerceAtLeast(currentPinHeight(instance))
        if (instance.uiState.expandedWidthPx <= 0 || instance.uiState.expandedHeightPx <= 0) {
            instance.uiState.expandedWidthPx = expandedW
            instance.uiState.expandedHeightPx = expandedH
        }
        val targetX: Int
        val targetY: Int
        val targetDisplayW: Int
        val targetDisplayH: Int
        val dockedPanelW: Int
        val dockedPanelH: Int
        if (horizontal) {
            targetDisplayW = dockSize
            targetDisplayH = expandedH
            dockedPanelW = dockSize
            dockedPanelH = expandedH
            targetY = instance.params.y.coerceIn(0, (screenH - dockedPanelH).coerceAtLeast(0))
            targetX = if (toStart) -(dockedPanelW - halfDock) else screenW - halfDock
        } else {
            targetDisplayW = expandedW
            targetDisplayH = dockSize
            dockedPanelW = expandedW
            dockedPanelH = dockSize
            targetX = instance.params.x.coerceIn(0, (screenW - dockedPanelW).coerceAtLeast(0))
            targetY = if (toStart) -(dockedPanelH - halfDock) else screenH - halfDock
        }
        instance.uiState.isEdgeDocked.value = true
        instance.uiState.showControls.value = false
        animatePinLayout(instance, targetX, targetY, targetDisplayW, targetDisplayH)
    }

    private fun restoreFromEdge(instance: PinInstance) {
        if (!instance.uiState.isEdgeDocked.value) return
        instance.uiState.isEdgeDocked.value = false
        if (instance.content is PinContent.Image) {
            applyImagePinWindowLayout(instance)
        }
        val metrics = appContext?.resources?.displayMetrics ?: return
        val panelW = currentPanelWidth(instance)
        val panelH = currentPanelHeight(instance)
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val x = instance.params.x
        val y = instance.params.y
        val targetX = when {
            x < 0 -> 0
            x + panelW > screenW -> (screenW - panelW).coerceAtLeast(0)
            else -> x
        }
        val targetY = when {
            y < 0 -> 0
            y + panelH > screenH -> (screenH - panelH).coerceAtLeast(0)
            else -> y
        }
        animatePinLayout(
            instance,
            targetX,
            targetY,
            instance.uiState.expandedWidthPx.coerceAtLeast(1),
            instance.uiState.expandedHeightPx.coerceAtLeast(1),
        )
    }

    private fun animatePinLayout(
        instance: PinInstance,
        targetX: Int,
        targetY: Int,
        targetDisplayW: Int,
        targetDisplayH: Int,
    ) {
        instance.uiState.animator?.cancel()
        val startX = instance.params.x
        val startY = instance.params.y
        val startW = instance.uiState.displayWidthPx.intValue.coerceAtLeast(1)
        val startH = instance.uiState.displayHeightPx.intValue.coerceAtLeast(1)
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = EDGE_SNAP_ANIM_MS
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                instance.params.x = (startX + (targetX - startX) * t).roundToInt()
                instance.params.y = (startY + (targetY - startY) * t).roundToInt()
                instance.offsetX = instance.params.x
                instance.offsetY = instance.params.y
                instance.uiState.displayWidthPx.intValue =
                    (startW + (targetDisplayW - startW) * t).roundToInt().coerceAtLeast(1)
                instance.uiState.displayHeightPx.intValue =
                    (startH + (targetDisplayH - startH) * t).roundToInt().coerceAtLeast(1)
                if (instance.content is PinContent.Image) {
                    applyImagePinWindowLayout(instance)
                }
                runCatching { windowManager?.updateViewLayout(instance.composeView, instance.params) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (instance.content is PinContent.Image) {
                        applyImagePinWindowLayout(instance)
                        runCatching { windowManager?.updateViewLayout(instance.composeView, instance.params) }
                    }
                }
            })
        }
        instance.uiState.animator = animator
        animator.start()
    }

    private fun currentPanelWidth(instance: PinInstance): Int {
        return when (val content = instance.content) {
            is PinContent.Image -> {
                val metrics = appContext?.resources?.displayMetrics ?: return instance.params.width
                val contentW = instance.uiState.displayWidthPx.intValue.coerceAtLeast(1)
                if (instance.uiState.isEdgeDocked.value) {
                    contentW
                } else {
                    max(contentW, controlBarWidthPx(metrics))
                }
            }
            is PinContent.Text -> instance.composeView.width.coerceAtLeast(instance.params.width)
            is PinContent.Rich -> instance.composeView.width.coerceAtLeast(instance.params.width)
        }
    }

    private fun currentPanelHeight(instance: PinInstance): Int {
        return when (instance.content) {
            is PinContent.Image -> {
                val metrics = appContext?.resources?.displayMetrics ?: return instance.params.height
                val contentH = instance.uiState.displayHeightPx.intValue.coerceAtLeast(1)
                if (instance.uiState.isEdgeDocked.value) {
                    contentH
                } else {
                    contentH + controlBarSlotHeightPx(metrics)
                }
            }
            is PinContent.Text -> instance.composeView.height.coerceAtLeast(instance.params.height)
            is PinContent.Rich -> instance.composeView.height.coerceAtLeast(instance.params.height)
        }
    }

    private fun currentPinWidth(instance: PinInstance): Int {
        return when (val content = instance.content) {
            is PinContent.Image -> instance.uiState.expandedWidthPx.coerceAtLeast(1)
            is PinContent.Text -> instance.composeView.width.coerceAtLeast(1)
            is PinContent.Rich -> instance.composeView.width.coerceAtLeast(1)
        }
    }

    private fun currentPinHeight(instance: PinInstance): Int {
        return when (val content = instance.content) {
            is PinContent.Image -> instance.uiState.expandedHeightPx.coerceAtLeast(1)
            is PinContent.Text -> instance.composeView.height.coerceAtLeast(1)
            is PinContent.Rich -> instance.composeView.height.coerceAtLeast(1)
        }
    }

    private fun controlBarWidthPx(metrics: DisplayMetrics): Int {
        return (PIN_CONTROL_BAR_MIN_WIDTH_DP * metrics.density).roundToInt()
    }

    private fun controlBarSlotHeightPx(metrics: DisplayMetrics): Int {
        return (PIN_CONTROL_BAR_SLOT_HEIGHT_DP * metrics.density).roundToInt()
    }

    private fun imagePanelSizePx(
        metrics: DisplayMetrics,
        contentW: Int,
        contentH: Int,
        edgeDocked: Boolean,
    ): Pair<Int, Int> {
        val safeW = contentW.coerceAtLeast(1)
        val safeH = contentH.coerceAtLeast(1)
        return if (edgeDocked) {
            safeW to safeH
        } else {
            max(safeW, controlBarWidthPx(metrics)) to (safeH + controlBarSlotHeightPx(metrics))
        }
    }

    private fun applyImagePinWindowLayout(instance: PinInstance) {
        val context = appContext ?: return
        if (instance.content !is PinContent.Image) return
        val metrics = context.resources.displayMetrics
        val contentW = instance.uiState.displayWidthPx.intValue.coerceAtLeast(1)
        val contentH = instance.uiState.displayHeightPx.intValue.coerceAtLeast(1)
        val (panelW, panelH) = imagePanelSizePx(
            metrics = metrics,
            contentW = contentW,
            contentH = contentH,
            edgeDocked = instance.uiState.isEdgeDocked.value,
        )
        instance.params.width = panelW
        instance.params.height = panelH
    }

    private fun computeFallbackDropBounds(metrics: DisplayMetrics): DropOverlayState {
        val chipW = (DROP_TARGET_CHIP_WIDTH_DP * metrics.density).roundToInt()
        val chipH = (DROP_TARGET_CHIP_HEIGHT_DP * metrics.density).roundToInt()
        val edge = (DROP_TARGET_EDGE_PADDING_DP * metrics.density).roundToInt()
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        return DropOverlayState().apply {
            notifyBounds = Rect(
                edge,
                screenH / 2 - chipH / 2,
                edge + chipW,
                screenH / 2 + chipH / 2,
            )
            deleteBounds = Rect(
                screenW / 2 - chipW / 2,
                screenH - edge - chipH,
                screenW / 2 + chipW / 2,
                screenH - edge,
            )
            stashBounds = Rect(
                screenW - edge - chipW,
                screenH / 2 - chipH / 2,
                screenW - edge,
                screenH / 2 + chipH / 2,
            )
        }
    }

    private fun showDropOverlay() {
        if (dropComposeView != null) return
        val context = appContext ?: return
        val wm = windowManager ?: return
        val metrics = context.resources.displayMetrics
        val state = computeFallbackDropBounds(metrics)
        val owner = OverlayComposeOwner()
        val composeView = OverlayCompose.createComposeView(context, owner)
        composeView.setContent {
            SlideIndexTheme {
                val highlighted by state.highlighted
                ScreenPinDropTargets(
                    highlighted = highlighted,
                    onDeleteBounds = { state.deleteBounds = it },
                    onNotifyBounds = { state.notifyBounds = it },
                    onStashBounds = { state.stashBounds = it },
                )
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(context),
            pinOverlayFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val added = runCatching { wm.addView(composeView, params) }.isSuccess
        if (!added) {
            owner.destroy()
            return
        }
        dropOverlay = state
        dropComposeView = composeView
        dropOwner = owner
    }

    private fun updateDropHighlight(fingerX: Float, fingerY: Float) {
        dropOverlay?.highlighted?.value = resolveDropZone(fingerX, fingerY)
    }

    private fun hideDropOverlay() {
        dropOverlay?.highlighted?.value = ScreenPinDropZone.NONE
        val view = dropComposeView
        val owner = dropOwner
        if (view != null) {
            runCatching { windowManager?.removeView(view) }
        }
        owner?.destroy()
        dropComposeView = null
        dropOwner = null
        dropOverlay = null
    }

    private fun ensureScreenOffReceiver(context: Context) {
        if (screenOffReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) dismissAll()
            }
        }
        screenOffReceiver = receiver
        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
    }

    private fun unregisterScreenOffReceiver() {
        val receiver = screenOffReceiver ?: return
        appContext?.let { runCatching { it.unregisterReceiver(receiver) } }
        screenOffReceiver = null
    }
}

@Composable
private fun ScreenPinContent(
    instance: PinInstance,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (dx: Float, dy: Float, localX: Float, localY: Float) -> Unit,
    onDragEnd: (localX: Float, localY: Float) -> Unit,
    onClose: () -> Unit,
    onAlphaChange: (Float) -> Unit,
) {
    val showControls by instance.uiState.showControls
    val alpha by instance.uiState.contentAlpha
    val isDocked by instance.uiState.isEdgeDocked
    val displayW = instance.uiState.displayWidthPx.intValue
    val displayH = instance.uiState.displayHeightPx.intValue
    val density = LocalDensity.current
    val slotHeight = PIN_CONTROL_BAR_SLOT_HEIGHT_DP.dp
    val barMinWidth = PIN_CONTROL_BAR_MIN_WIDTH_DP.dp

    var appeared by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        appeared = true
    }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (appeared) 1f else 0.8f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.7f,
            stiffness = 300f
        )
    )
    val entryAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(200)
    )

    Column(
        modifier = Modifier
            .wrapContentWidth()
            .wrapContentHeight()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = entryAlpha
            }
            .pointerInput(instance.id) {
                detectPinDragAndTap(
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onTap = onTap,
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val content = instance.content) {
            is PinContent.Text -> {
                val config = LocalConfiguration.current
                val maxWidth = (config.screenWidthDp * TEXT_PIN_MAX_WIDTH_FRACTION).dp
                Box(
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .alpha(alpha)
                        .shadow(6.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                ) {
                    Text(
                        text = content.body,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
            is PinContent.Image -> {
                val contentW = with(density) { displayW.toDp().coerceAtLeast(1.dp) }
                val contentH = with(density) { displayH.toDp().coerceAtLeast(1.dp) }
                Image(
                    bitmap = content.bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(contentW, contentH)
                        .alpha(alpha),
                    contentScale = ContentScale.Fit,
                )
            }
            is PinContent.Rich -> {
                val config = LocalConfiguration.current
                val maxWidth = (config.screenWidthDp * TEXT_PIN_MAX_WIDTH_FRACTION).dp
                val maxHeight = (config.screenHeightDp * RICH_PIN_MAX_HEIGHT_FRACTION).dp
                Box(
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .alpha(alpha)
                        .shadow(6.dp, RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = maxHeight)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        content.blocks.forEach { block ->
                            when (block) {
                                is PinDisplayBlock.Text -> {
                                    Text(
                                        text = block.body,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                is PinDisplayBlock.Image -> {
                                    Image(
                                        bitmap = block.bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = RICH_PIN_IMAGE_MAX_HEIGHT_DP.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!isDocked) {
            Box(
                modifier = Modifier
                    .widthIn(min = barMinWidth)
                    .height(slotHeight)
                    .padding(top = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (showControls) {
                    PinControlBar(
                        alpha = alpha,
                        onAlphaChange = onAlphaChange,
                        onClose = onClose,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PinControlBar(
    alpha: Float,
    onAlphaChange: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(20.dp)
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    Row(
        modifier = modifier
            .shadow(6.dp, pillShape, clip = false)
            .clip(pillShape)
            .border(0.5.dp, outlineColor, pillShape)
            .background(surfaceColor)
            .padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slider(
            value = alpha,
            onValueChange = onAlphaChange,
            valueRange = PIN_CONTROL_ALPHA_MIN..1f,
            modifier = Modifier
                .weight(1f)
                .height(32.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            ),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.shell_panel_close),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private suspend fun PointerInputScope.detectPinDragAndTap(
    onDragStart: () -> Unit,
    onDrag: (dx: Float, dy: Float, localX: Float, localY: Float) -> Unit,
    onDragEnd: (localX: Float, localY: Float) -> Unit,
    onTap: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val pointerId = down.id
        val touchSlop = viewConfiguration.touchSlop
        var dragged = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            if (!change.pressed) {
                if (dragged) {
                    onDragEnd(change.position.x, change.position.y)
                } else {
                    onTap()
                }
                break
            }
            val offset = change.position - down.position
            if (!dragged && offset.getDistance() > touchSlop) {
                dragged = true
                onDragStart()
            }
            if (dragged) {
                val delta = change.positionChange()
                if (delta != Offset.Zero) {
                    change.consume()
                    onDrag(delta.x, delta.y, change.position.x, change.position.y)
                }
            }
        }
    }
}

@Composable
private fun ScreenPinDropTargets(
    highlighted: ScreenPinDropZone,
    onDeleteBounds: (Rect) -> Unit,
    onNotifyBounds: (Rect) -> Unit,
    onStashBounds: (Rect) -> Unit,
) {
    val stashActive = highlighted == ScreenPinDropZone.STASH
    val deleteActive = highlighted == ScreenPinDropZone.DELETE
    val notifyActive = highlighted == ScreenPinDropZone.NOTIFICATION
    Box(modifier = Modifier.fillMaxSize()) {
        DropTargetChip(
            text = stringResource(R.string.stash_drop_target_notification),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp),
            active = notifyActive,
            onBoundsInWindow = onNotifyBounds,
        )
        DropTargetChip(
            text = stringResource(R.string.stash_drop_target_delete),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            active = deleteActive,
            onBoundsInWindow = onDeleteBounds,
        )
        DropTargetChip(
            text = stringResource(R.string.stash_drop_target_stash),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp),
            active = stashActive,
            onBoundsInWindow = onStashBounds,
        )
    }
}

@Composable
private fun DropTargetChip(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean,
    onBoundsInWindow: (Rect) -> Unit,
) {
    val bg = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
    }
    val fg = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier.onGloballyPositioned { coords ->
            val bounds = coords.boundsInParent()
            onBoundsInWindow(
                Rect(
                    bounds.left.roundToInt(),
                    bounds.top.roundToInt(),
                    bounds.right.roundToInt(),
                    bounds.bottom.roundToInt(),
                ),
            )
        },
    ) {
        Box(
            Modifier
                .shadow(8.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(bg)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(text = text, color = fg, fontSize = 14.sp)
        }
    }
}
