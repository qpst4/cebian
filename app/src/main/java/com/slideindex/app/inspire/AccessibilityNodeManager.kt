package com.slideindex.app.inspire

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * GestureEVO AccessibilityNodeManager — multi-window parallel accessibility traversal.
 */
object AccessibilityNodeManager {

    suspend fun getScreenContent(
        service: AccessibilityService,
        rect: Rect,
    ): List<ScreenContentNode> = processAccessibilityNodesInParallel(
        service = service,
        nodeProcessor = { node, bounds ->
            val text = node.text
            if (text.isNullOrEmpty()) {
                null
            } else if (!Rect.intersects(rect, bounds) && !bounds.contains(rect)) {
                null
            } else {
                getCharacterLocations(node, rect)
            }
        },
        nodeFilter = { _, bounds ->
            Rect.intersects(rect, bounds) || bounds.contains(rect)
        },
    )

    private suspend fun <T> processAccessibilityNodesInParallel(
        service: AccessibilityService,
        nodeProcessor: suspend (AccessibilityNodeInfo, Rect) -> T?,
        nodeFilter: (AccessibilityNodeInfo, Rect) -> Boolean,
    ): List<T> = withContext(Dispatchers.Default) {
        val roots = getNeedWindowsRoot(service)
        val workerCount = min(max(Runtime.getRuntime().availableProcessors(), 1), 4)
        val dispatcher = Dispatchers.Default.limitedParallelism(workerCount)
        coroutineScope {
            roots.map { root ->
                async {
                    processRootInParallel(root, workerCount, dispatcher, nodeProcessor, nodeFilter)
                }
            }.awaitAll().flatten()
        }
    }

    private suspend fun <T> processRootInParallel(
        rootNode: AccessibilityNodeInfo,
        workerCount: Int,
        computeDispatcher: kotlinx.coroutines.CoroutineDispatcher,
        nodeProcessor: suspend (AccessibilityNodeInfo, Rect) -> T?,
        nodeFilter: (AccessibilityNodeInfo, Rect) -> Boolean,
    ): List<T> = coroutineScope {
        val results = List(workerCount) { mutableListOf<T>() }
        val nodeChannel = Channel<Pair<AccessibilityNodeInfo, Rect>>(capacity = Channel.UNLIMITED)

        val processingJobs = List(workerCount) { workerIndex ->
            launch(computeDispatcher) {
                for ((node, bounds) in nodeChannel) {
                    val processed = nodeProcessor(node, bounds)
                    if (processed != null) {
                        results[workerIndex].add(processed)
                    }
                }
            }
        }

        launch {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(rootNode)
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (nodeFilter(node, bounds)) {
                    nodeChannel.send(node to bounds)
                }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    if (child.isVisibleToUser) {
                        queue.add(child)
                    }
                }
            }
            nodeChannel.close()
        }

        processingJobs.forEach { it.join() }
        results.flatten()
    }

    fun getNeedWindowsRoot(service: AccessibilityService): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        for (window in service.windows) {
            if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
            val root = window.root ?: continue
            if (root.childCount > 0) {
                roots.add(root)
            }
        }
        return roots
    }

    fun getCharacterLocations(nodeInfo: AccessibilityNodeInfo, rectOutline: Rect): ScreenContentNode {
        val text = nodeInfo.text ?: return ScreenContentNode(rectOutline, "")
        val extras = nodeInfo.extras
        extras.putInt(AccessibilityNodeInfoCompat.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
        extras.putInt(
            AccessibilityNodeInfoCompat.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH,
            text.length,
        )
        nodeInfo.refreshWithExtraData(
            AccessibilityNodeInfoCompat.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
            extras,
        )
        val builder = StringBuilder()
        val parcelableArray = nodeInfo.extras.getParcelableArray(
            AccessibilityNodeInfoCompat.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
        )
        if (parcelableArray != null) {
            parcelableArray.forEachIndexed { index, parcelable ->
                if (parcelable != null) {
                    val rect = Rect()
                    (parcelable as RectF).roundOut(rect)
                    if (Rect.intersects(rectOutline, rect) || rectOutline.contains(rect)) {
                        builder.append(text[index])
                    }
                }
            }
        }
        val cropped = builder.toString()
        if (cropped.isNotEmpty()) {
            return ScreenContentNode(rectOutline, cropped)
        }
        val nodeBounds = Rect()
        nodeInfo.getBoundsInScreen(nodeBounds)
        return if (
            Rect.intersects(rectOutline, nodeBounds) ||
                rectOutline.contains(nodeBounds) ||
                nodeBounds.contains(rectOutline)
        ) {
            ScreenContentNode(nodeBounds, text.toString())
        } else {
            ScreenContentNode(rectOutline, "")
        }
    }

    fun cropByRect(bitmap: Bitmap, rect: Rect): Bitmap? {
        val safe = Rect(
            rect.left.coerceIn(0, bitmap.width),
            rect.top.coerceIn(0, bitmap.height),
            rect.right.coerceIn(0, bitmap.width),
            rect.bottom.coerceIn(0, bitmap.height),
        )
        if (safe.width() <= 0 || safe.height() <= 0) return null
        return try {
            val cropped = Bitmap.createBitmap(bitmap, safe.left, safe.top, safe.width(), safe.height())
            // Full-screen crop may return the same instance; holder keeps a ref while managed closes.
            if (cropped === bitmap) {
                cropped.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            } else {
                cropped
            }
        } catch (_: Exception) {
            null
        }
    }

    fun cropByRect(managedBitmap: ManagedBitmap, rect: Rect): ManagedBitmap? {
        val acquired = managedBitmap.acquire() ?: return null
        return try {
            val cropped = cropByRect(acquired.requireBitmap(), rect)
            cropped?.let(ManagedBitmap::from)
        } finally {
            acquired.close()
        }
    }
}
