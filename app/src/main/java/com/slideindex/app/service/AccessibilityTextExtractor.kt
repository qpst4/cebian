package com.slideindex.app.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.slideindex.app.overlay.FloatBallOcrRegions

/**
 * Accessibility text collection for float-ball point/rect pick.
 *
 * Point pick uses smallest text-bearing bounds at the coordinate (FV-style),
 * not QC [defpackage.vo] deepest-first-return, so large transparent overlays like
 * Douyin's full-screen "pause video" button do not mask comment TextViews beneath.
 *
 * Nodes with [AccessibilityNodeInfo.isVisibleToUser] false are still considered when they
 * carry primary [AccessibilityNodeInfo.getText] (WeChat 视频号 comment bodies).
 */
object AccessibilityTextExtractor {

    /** Lines shorter than this from a11y alone may trigger OCR when enabled. */
    private const val WEAK_A11Y_PICK_MIN_LONGEST_LINE = 24

    const val DEFAULT_MAX_TRAVERSAL_NODES = Int.MAX_VALUE
    /** Preview bounds during float-ball drag; keeps heavy apps (WeChat 视频号) responsive. */
    const val PREVIEW_MAX_TRAVERSAL_NODES = 800
    const val HEAVY_PREVIEW_MAX_TRAVERSAL_NODES = 400

    private const val FV_SKIP_WINDOW_MARKER = "*FV_SKIP_WINDOW*"
    private const val SKIP_MARKER_SCAN_DEPTH = 4
    /** When exact preview match is this much shorter, prefer longer Reddit post metadata. */
    private const val PREVIEW_LONGEST_UPGRADE_MIN_EXTRA = 40

    private class NodeTraversalBudget(private val maxNodes: Int) {
        var visited = 0
        fun consume(): Boolean {
            visited++
            return visited <= maxNodes
        }
    }

    private fun isSkipMarkerText(value: CharSequence?): Boolean =
        value?.toString() == FV_SKIP_WINDOW_MARKER

    private fun shouldSkipAccessibilityNode(node: AccessibilityNodeInfo): Boolean {
        if (isSkipMarkerText(node.contentDescription)) return true
        if (isSkipMarkerText(node.text)) return true
        if (isSkipMarkerText(node.stateDescription)) {
            return true
        }
        return false
    }

    /**
     * Visible nodes are always traversed. Invisible nodes are included only when they have
     * primary text — avoids hidden chrome while matching QC on WeChat comment TextViews.
     */
    private fun includeNodeForPickTraversal(node: AccessibilityNodeInfo): Boolean {
        if (node.isVisibleToUser) return true
        return !node.text.isNullOrBlank()
    }

    /** Preview pick also reads contentDescription on non-visible nodes (Reddit post headers). */
    private fun includeNodeForPreviewTextTraversal(node: AccessibilityNodeInfo): Boolean {
        if (includeNodeForPickTraversal(node)) return true
        return !node.contentDescription.isNullOrBlank()
    }

    internal fun isWeakA11yPickResult(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        val longestLine = trimmed.lines().maxOfOrNull { line -> line.trim().length } ?: 0
        return longestLine < WEAK_A11Y_PICK_MIN_LONGEST_LINE
    }

    internal fun preferLongerPickText(a11yText: String?, ocrText: String?): String? {
        if (ocrText.isNullOrBlank()) return a11yText?.let(::dedupeTextLines)
        if (a11yText.isNullOrBlank()) return dedupeTextLines(ocrText)
        val a11yLongest = a11yText.lines().maxOfOrNull { it.trim().length } ?: 0
        val ocrLongest = ocrText.lines().maxOfOrNull { it.trim().length } ?: 0
        return dedupeTextLines(if (ocrLongest > a11yLongest) ocrText else a11yText)
    }

    /** Collapse repeated lines from duplicate WeChat 视频号 comment nodes. */
    internal fun dedupeTextLines(text: String): String {
        val seen = LinkedHashSet<String>()
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && seen.add(it) }
            .joinToString("\n")
    }

    private fun shouldSkipWindowRoot(root: AccessibilityNodeInfo, service: AccessibilityService): Boolean {
        return containsSkipMarker(root, SKIP_MARKER_SCAN_DEPTH)
    }

    private fun shouldSkipPickWindow(window: AccessibilityWindowInfo): Boolean {
        return when (window.type) {
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
            AccessibilityWindowInfo.TYPE_INPUT_METHOD,
            -> true
            else -> false
        }
    }

    private fun containsSkipMarker(node: AccessibilityNodeInfo, depthRemaining: Int): Boolean {
        if (shouldSkipAccessibilityNode(node)) return true
        if (depthRemaining <= 0) return false
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (containsSkipMarker(child, depthRemaining - 1)) return true
            } finally {
                releaseNode(child)
            }
        }
        return false
    }

    fun collectTextAt(service: AccessibilityService, rawX: Float, rawY: Float): String? {
        val px = rawX.toInt()
        val py = rawY.toInt()
        var best: TextCandidate? = null
        val windowBounds = Rect()
        for (window in service.windows) {
            if (shouldSkipPickWindow(window)) continue
            window.getBoundsInScreen(windowBounds)
            if (!windowBounds.contains(px, py)) continue
            val root = window.root ?: continue
            if (shouldSkipWindowRoot(root, service)) {
                releaseNode(root)
                continue
            }
            try {
                best = pickBetterCandidate(best, findTextCandidateAtNode(root, px, py))
            } finally {
                releaseNode(root)
            }
        }
        val active = service.rootInActiveWindow
        if (active != null && !shouldSkipWindowRoot(active, service)) {
            try {
                best = pickBetterCandidate(best, findTextCandidateAtNode(active, px, py))
            } finally {
                releaseNode(active)
            }
        }
        val pointText = best?.text
        if (!pointText.isNullOrBlank()) return dedupeTextLines(pointText)

        val metrics = service.resources.displayMetrics
        val nearbyRect = FloatBallOcrRegions.expandPoint(metrics, rawX, rawY)
        val nearby = collectTextInRect(service, nearbyRect).trim()
        return nearby.ifBlank { null }?.let(::dedupeTextLines)
    }

    fun findControlBoundsAt(
        service: AccessibilityService,
        rawX: Float,
        rawY: Float,
        activeWindowOnly: Boolean = false,
        maxNodes: Int = DEFAULT_MAX_TRAVERSAL_NODES,
    ): Rect? {
        val px = rawX.toInt()
        val py = rawY.toInt()
        val nodeBounds = Rect()
        val budget = NodeTraversalBudget(maxNodes)
        if (activeWindowOnly) {
            val active = service.rootInActiveWindow ?: return null
            if (shouldSkipWindowRoot(active, service)) {
                releaseNode(active)
                return null
            }
            return try {
                findControlBoundsInNode(active, px, py, nodeBounds, budget)?.rect
            } finally {
                releaseNode(active)
            }
        }
        var best: BoundsCandidate? = null
        val windowBounds = Rect()
        for (window in service.windows) {
            if (shouldSkipPickWindow(window)) continue
            window.getBoundsInScreen(windowBounds)
            if (!windowBounds.contains(px, py)) continue
            val root = window.root ?: continue
            if (shouldSkipWindowRoot(root, service)) {
                releaseNode(root)
                continue
            }
            try {
                best = pickBetterBoundsCandidate(
                    best,
                    findControlBoundsInNode(root, px, py, nodeBounds, budget),
                )
            } finally {
                releaseNode(root)
            }
            if (budget.visited >= maxNodes) break
        }
        val active = service.rootInActiveWindow
        if (active != null && !shouldSkipWindowRoot(active, service)) {
            try {
                best = pickBetterBoundsCandidate(
                    best,
                    findControlBoundsInNode(active, px, py, nodeBounds, budget),
                )
            } finally {
                releaseNode(active)
            }
        }
        return best?.rect
    }

    private data class BoundsCandidate(
        val rect: Rect,
        val score: Int,
    )

    private fun pickBetterBoundsCandidate(
        current: BoundsCandidate?,
        next: BoundsCandidate?,
    ): BoundsCandidate? {
        if (next == null) return current
        if (current == null) return next
        return if (next.score < current.score) next else current
    }

    private fun controlTargetScore(node: AccessibilityNodeInfo): Int {
        if (!node.text.isNullOrBlank()) return 0
        if (!node.contentDescription.isNullOrBlank()) return 1
        val className = node.className?.toString().orEmpty()
        if (className.contains("TextView", ignoreCase = true)) return 2
        if (node.isClickable || node.isFocusable || node.isLongClickable) return 3
        return 4
    }

    private fun findControlBoundsInNode(
        node: AccessibilityNodeInfo,
        px: Int,
        py: Int,
        bounds: Rect,
        budget: NodeTraversalBudget,
    ): BoundsCandidate? {
        var best: BoundsCandidate? = null
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        val childBounds = Rect()
        stack.addLast(node)
        while (stack.isNotEmpty()) {
            if (!budget.consume()) break
            val current = stack.removeLast()
            val owned = current !== node
            try {
                if (shouldSkipAccessibilityNode(current)) continue
                current.getBoundsInScreen(bounds)
                if (!bounds.contains(px, py)) continue
                if (includeNodeForPickTraversal(current) && isMeaningfulPickTarget(current)) {
                    val area = bounds.width().coerceAtLeast(1) * bounds.height().coerceAtLeast(1)
                    val score = controlTargetScore(current) * 1_000_000 + area
                    best = pickBetterBoundsCandidate(best, BoundsCandidate(Rect(bounds), score))
                }
                for (i in current.childCount - 1 downTo 0) {
                    val child = current.getChild(i) ?: continue
                    var keepChild = false
                    try {
                        child.getBoundsInScreen(childBounds)
                        if (childBounds.contains(px, py)) {
                            stack.addLast(child)
                            keepChild = true
                        }
                    } finally {
                        if (!keepChild) releaseNode(child)
                    }
                }
            } finally {
                if (owned) releaseNode(current)
            }
        }
        return best
    }

    private fun isMeaningfulPickTarget(node: AccessibilityNodeInfo): Boolean {
        if (!node.text.isNullOrBlank()) return true
        if (!node.contentDescription.isNullOrBlank()) return true
        if (node.isClickable || node.isFocusable || node.isLongClickable) return true
        return false
    }

    fun collectTextInRect(service: AccessibilityService, rect: Rect): String {
        if (rect.width() <= 0 || rect.height() <= 0) return ""
        val normalized = Rect(rect)
        val entries = collectIntersectingTextEntries(service, normalized)
        return dedupeTextLines(joinSortedTexts(filterOutAncestorTextEntries(entries)))
    }

    /**
     * Text for a float-ball preview box: exact bounds for QQ rows; center-point / parent-chain
     * longest metadata for Reddit post headers; else leaf texts with ancestor filtering.
     */
    fun collectTextForPreviewRect(service: AccessibilityService, previewRect: Rect): String {
        if (previewRect.width() <= 0 || previewRect.height() <= 0) return ""
        val normalized = Rect(previewRect)
        val previewArea = normalized.width().coerceAtLeast(1) * normalized.height().coerceAtLeast(1)
        val px = normalized.exactCenterX().toInt()
        val py = normalized.exactCenterY().toInt()
        val scan = scanPreviewRect(service, normalized, px, py, previewArea)
        val exactEntry = scan.exactEntry
        if (exactEntry != null && boundsNearlyMatch(exactEntry, normalized)) {
            return dedupeTextLines(exactEntry.text)
        }
        val parentChainMetadata = scan.deepestNode?.let {
            findLongestMetadataOnParentChain(it, previewArea)
        }
        scan.deepestNode?.let { releaseNode(it) }
        val containedEntries = filterEntriesContainedInPreview(scan.intersectingEntries, normalized)
        val longestIntersecting = containedEntries.maxByOrNull { it.text.length }?.text
        val exactMatchesPreview = exactEntry != null && boundsNearlyMatch(exactEntry, normalized)
        pickBestPreviewMetadata(
            exactText = exactEntry?.text,
            exactMatchesPreview = exactMatchesPreview,
            exactArea = exactEntry?.area ?: 0,
            previewArea = previewArea,
            centerMetadata = scan.centerMetadata,
            parentChainMetadata = parentChainMetadata,
            intersectingLongest = longestIntersecting,
        )?.let { matched ->
            return dedupeTextLines(matched)
        }
        scan.smallestContaining?.text?.takeIf { it.isNotBlank() }?.let { matched ->
            return dedupeTextLines(matched)
        }
        return dedupeTextLines(joinSortedTexts(filterOutAncestorTextEntries(containedEntries)))
    }

    private data class PreviewRectScanResult(
        val exactEntry: TextEntry?,
        val intersectingEntries: List<TextEntry>,
        val centerMetadata: String?,
        val deepestNode: AccessibilityNodeInfo?,
        val smallestContaining: TextEntry?,
    )

    private fun scanPreviewRect(
        service: AccessibilityService,
        preview: Rect,
        px: Int,
        py: Int,
        previewArea: Int,
    ): PreviewRectScanResult {
        var exactEntry: TextEntry? = null
        var smallestContaining: TextEntry? = null
        var deepestNode: AccessibilityNodeInfo? = null
        var deepestArea = Int.MAX_VALUE
        var centerMetadata: String? = null
        var centerMetadataLen = 0
        val intersectingEntries = ArrayList<TextEntry>()
        val intersectSeen = LinkedHashSet<String>()
        val bounds = Rect()
        val childBounds = Rect()
        val windowBounds = Rect()
        val scannedRoots = HashSet<Int>()
        var qqExactReady = false

        fun visitNode(node: AccessibilityNodeInfo) {
            if (qqExactReady || shouldSkipAccessibilityNode(node)) return
            node.getBoundsInScreen(bounds)
            if (!Rect.intersects(bounds, preview) && !bounds.contains(px, py)) return
            val intersects = Rect.intersects(bounds, preview)
            val containsPoint = bounds.contains(px, py)
            if (includeNodeForPickTraversal(node) && boundsNearlyMatch(bounds, preview)) {
                nodeText(node)?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
                    exactEntry = pickBetterBoundsTextEntry(
                        exactEntry,
                        TextEntry(text, bounds.top, bounds.left, bounds.right, bounds.bottom),
                    )
                    exactEntry?.let { entry ->
                        if (boundsNearlyMatch(entry, preview) && entry.area >= previewArea / 2) {
                            qqExactReady = true
                            return
                        }
                    }
                }
            }
            if (includeNodeForPreviewTextTraversal(node)) {
                if (intersects) {
                    nodePreviewMetadata(node)?.let { raw ->
                        val text = raw.trim()
                        if (text.isNotEmpty() && intersectSeen.add(text)) {
                            intersectingEntries.add(
                                TextEntry(text, bounds.top, bounds.left, bounds.right, bounds.bottom),
                            )
                        }
                    }
                }
                if (rectContains(bounds, preview)) {
                    nodePreviewMetadata(node)?.let { raw ->
                        val text = raw.trim()
                        if (text.isNotEmpty()) {
                            smallestContaining = pickBetterBoundsTextEntry(
                                smallestContaining,
                                TextEntry(text, bounds.top, bounds.left, bounds.right, bounds.bottom),
                            )
                        }
                    }
                }
                if (containsPoint) {
                    val area = bounds.width().coerceAtLeast(1) * bounds.height().coerceAtLeast(1)
                    if (area < deepestArea) {
                        deepestArea = area
                        deepestNode?.let { releaseNode(it) }
                        deepestNode = copyNode(node)
                    }
                    if (isMetadataBoundsRelevant(bounds, preview, previewArea)) {
                        nodePreviewMetadata(node)?.let { raw ->
                            val text = raw.trim()
                            if (text.length > centerMetadataLen) {
                                centerMetadataLen = text.length
                                centerMetadata = text
                            }
                        }
                    }
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    child.getBoundsInScreen(childBounds)
                    if (!Rect.intersects(childBounds, preview) && !childBounds.contains(px, py)) continue
                    visitNode(child)
                } finally {
                    releaseNode(child)
                }
            }
        }

        fun scanRoot(root: AccessibilityNodeInfo) {
            if (qqExactReady) return
            val token = System.identityHashCode(root)
            if (!scannedRoots.add(token)) return
            visitNode(root)
        }

        for (window in service.windows) {
            if (shouldSkipPickWindow(window)) continue
            window.getBoundsInScreen(windowBounds)
            if (!Rect.intersects(windowBounds, preview) && !windowBounds.contains(px, py)) continue
            val root = window.root ?: continue
            if (shouldSkipWindowRoot(root, service)) {
                releaseNode(root)
                continue
            }
            try {
                scanRoot(root)
            } finally {
                releaseNode(root)
            }
        }
        val active = service.rootInActiveWindow
        if (active != null && !shouldSkipWindowRoot(active, service)) {
            try {
                scanRoot(active)
            } finally {
                releaseNode(active)
            }
        }
        return PreviewRectScanResult(
            exactEntry = exactEntry,
            intersectingEntries = intersectingEntries,
            centerMetadata = centerMetadata,
            deepestNode = deepestNode,
            smallestContaining = smallestContaining,
        )
    }

    internal fun filterEntriesContainedInPreview(
        entries: List<TextEntry>,
        preview: Rect,
        slackPx: Int = 3,
    ): List<TextEntry> {
        val left = preview.left - slackPx
        val top = preview.top - slackPx
        val right = preview.right + slackPx
        val bottom = preview.bottom + slackPx
        return entries.filter { entry ->
            entry.left >= left &&
                entry.top >= top &&
                entry.right <= right &&
                entry.bottom <= bottom
        }
    }

    internal fun boundsNearlyMatch(entry: TextEntry, target: Rect, slackPx: Int = 3): Boolean {
        return boundsNearlyMatch(
            Rect(entry.left, entry.top, entry.right, entry.bottom),
            target,
            slackPx,
        )
    }

    internal fun shouldUpgradePreviewExactToLongest(
        exactText: String,
        longestText: String?,
        previewArea: Int,
        exactArea: Int,
    ): Boolean {
        if (longestText.isNullOrBlank()) return false
        if (longestText.length < exactText.length + PREVIEW_LONGEST_UPGRADE_MIN_EXTRA) return false
        // QQ row: exact node already fills the preview — don't swap in list-wide metadata.
        if (previewArea > 0 && exactArea >= previewArea / 2) return false
        return true
    }

    internal fun pickBestPreviewMetadata(
        exactText: String?,
        exactMatchesPreview: Boolean,
        exactArea: Int = 0,
        previewArea: Int = 0,
        centerMetadata: String?,
        parentChainMetadata: String? = null,
        intersectingLongest: String?,
    ): String? {
        val exact = exactText?.trim()?.takeIf { it.isNotEmpty() }
        val alternative = listOfNotNull(centerMetadata, parentChainMetadata, intersectingLongest)
            .maxByOrNull { it.length }
        if (exact != null && exactMatchesPreview) {
            return exact
        }
        if (exact != null &&
            !shouldUpgradePreviewExactToLongest(exact, alternative, previewArea, exactArea)
        ) {
            return exact
        }
        return listOfNotNull(centerMetadata, parentChainMetadata, intersectingLongest, exact)
            .maxByOrNull { it.length }
    }

    internal data class TextCandidate(
        val text: String,
        val area: Int,
        val isPrimaryText: Boolean,
    )

    internal fun pickBetterCandidate(current: TextCandidate?, next: TextCandidate?): TextCandidate? {
        if (next == null) return current
        if (current == null) return next
        return TEXT_CANDIDATE_ORDER.compare(current, next).let { order ->
            if (order <= 0) current else next
        }
    }

    /**
     * Smallest text-bearing bounds at [px],[py]; scans the full subtree.
     */
    internal fun findTextAtNode(node: AccessibilityNodeInfo, px: Int, py: Int): String? =
        findTextCandidateAtNode(node, px, py)?.text

    internal fun findTextCandidateAtNode(
        node: AccessibilityNodeInfo,
        px: Int,
        py: Int,
    ): TextCandidate? {
        var best: TextCandidate? = null
        val bounds = Rect()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(node)
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            val owned = current !== node
            try {
                current.getBoundsInScreen(bounds)
                if (shouldSkipAccessibilityNode(current)) continue
                if (includeNodeForPickTraversal(current) && bounds.contains(px, py)) {
                    best = pickBetterCandidate(best, textCandidateAt(bounds, current))
                }
                for (i in 0 until current.childCount) {
                    current.getChild(i)?.let { stack.add(it) }
                }
            } finally {
                if (owned) releaseNode(current)
            }
        }
        return best
    }

    private val TEXT_CANDIDATE_ORDER = compareBy<TextCandidate> { it.area }
        .thenByDescending { it.isPrimaryText }
        .thenByDescending { it.text.length }

    internal fun joinSortedTexts(entries: List<TextEntry>, seen: LinkedHashSet<String> = LinkedHashSet()): String {
        return entries
            .sortedWith(compareBy<TextEntry> { it.top }.thenBy { it.left })
            .mapNotNull { entry ->
                val text = entry.text.trim()
                if (text.isEmpty() || !seen.add(text)) null else text
            }
            .joinToString("\n")
    }

    internal data class TextEntry(
        val text: String,
        val top: Int,
        val left: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val area: Int get() = (right - left).coerceAtLeast(1) * (bottom - top).coerceAtLeast(1)
    }

    internal fun filterOutAncestorTextEntries(entries: List<TextEntry>): List<TextEntry> {
        if (entries.size <= 1) return entries
        return entries.filter { candidate ->
            !entries.any { other ->
                candidate !== other &&
                    rectContains(candidate, other) &&
                    other.area < candidate.area
            }
        }
    }

    private fun rectContains(outer: TextEntry, inner: TextEntry): Boolean {
        return outer.left <= inner.left &&
            outer.top <= inner.top &&
            outer.right >= inner.right &&
            outer.bottom >= inner.bottom
    }

    private fun boundsNearlyMatch(a: Rect, b: Rect, slackPx: Int = 3): Boolean {
        return kotlin.math.abs(a.left - b.left) <= slackPx &&
            kotlin.math.abs(a.top - b.top) <= slackPx &&
            kotlin.math.abs(a.right - b.right) <= slackPx &&
            kotlin.math.abs(a.bottom - b.bottom) <= slackPx
    }

    private fun collectIntersectingTextEntries(
        service: AccessibilityService,
        rect: Rect,
        includeNode: (AccessibilityNodeInfo) -> Boolean = ::includeNodeForPickTraversal,
        readNodeText: (AccessibilityNodeInfo) -> String? = ::nodeText,
    ): List<TextEntry> {
        val entries = ArrayList<TextEntry>()
        val seen = LinkedHashSet<String>()
        val windowBounds = Rect()
        val scannedRoots = HashSet<Int>()
        fun scanRoot(root: AccessibilityNodeInfo) {
            val token = System.identityHashCode(root)
            if (!scannedRoots.add(token)) return
            collectIntersectingTexts(root, rect, entries, seen, includeNode, readNodeText)
        }
        for (window in service.windows) {
            if (shouldSkipPickWindow(window)) continue
            window.getBoundsInScreen(windowBounds)
            if (!Rect.intersects(windowBounds, rect)) continue
            val root = window.root ?: continue
            if (shouldSkipWindowRoot(root, service)) {
                releaseNode(root)
                continue
            }
            try {
                scanRoot(root)
            } finally {
                releaseNode(root)
            }
        }
        val active = service.rootInActiveWindow
        if (active != null && !shouldSkipWindowRoot(active, service)) {
            try {
                scanRoot(active)
            } finally {
                releaseNode(active)
            }
        }
        return entries
    }

    private fun findExactBoundsMatchEntry(
        service: AccessibilityService,
        target: Rect,
    ): TextEntry? {
        var best: TextEntry? = null
        val windowBounds = Rect()
        val scannedRoots = HashSet<Int>()
        fun scanRoot(root: AccessibilityNodeInfo) {
            val token = System.identityHashCode(root)
            if (!scannedRoots.add(token)) return
            best = pickBetterBoundsTextEntry(
                best,
                findBoundsTextEntry(
                    root,
                    target,
                    ::includeNodeForPickTraversal,
                    ::nodeText,
                ) { nodeBounds, preview -> boundsNearlyMatch(nodeBounds, preview) },
            )
        }
        for (window in service.windows) {
            if (shouldSkipPickWindow(window)) continue
            window.getBoundsInScreen(windowBounds)
            if (!Rect.intersects(windowBounds, target)) continue
            val root = window.root ?: continue
            if (shouldSkipWindowRoot(root, service)) {
                releaseNode(root)
                continue
            }
            try {
                scanRoot(root)
            } finally {
                releaseNode(root)
            }
        }
        val active = service.rootInActiveWindow
        if (active != null && !shouldSkipWindowRoot(active, service)) {
            try {
                scanRoot(active)
            } finally {
                releaseNode(active)
            }
        }
        return best
    }

    private fun findTextOnBoundsMatch(service: AccessibilityService, target: Rect): String? =
        findExactBoundsMatchEntry(service, target)?.text

    private fun findLongestMetadataOnParentChain(
        leaf: AccessibilityNodeInfo,
        previewArea: Int,
    ): String? {
        var best: String? = null
        val owned = ArrayList<AccessibilityNodeInfo>()
        val bounds = Rect()
        var current: AccessibilityNodeInfo? = copyNode(leaf)
        try {
            while (current != null && owned.size < 16) {
                current.getBoundsInScreen(bounds)
                if (isMetadataBoundsRelevant(bounds, previewArea = previewArea)) {
                    nodePreviewMetadata(current)?.let { text ->
                        if (best == null || text.length > best!!.length) best = text
                    }
                }
                owned.add(current)
                current = current.parent
            }
        } finally {
            owned.forEach { releaseNode(it) }
        }
        return best
    }

    private fun isMetadataBoundsRelevant(
        bounds: Rect,
        preview: Rect? = null,
        previewArea: Int,
        maxAreaMultiplier: Int = 40,
    ): Boolean {
        val area = bounds.width().coerceAtLeast(1) * bounds.height().coerceAtLeast(1)
        if (area > previewArea * maxAreaMultiplier) return false
        if (preview != null) {
            val containedInPreview = bounds.left >= preview.left - 3 &&
                bounds.top >= preview.top - 3 &&
                bounds.right <= preview.right + 3 &&
                bounds.bottom <= preview.bottom + 3
            if (containedInPreview) return true
        }
        return area <= previewArea * maxAreaMultiplier
    }

    private fun rectContains(outer: Rect, inner: Rect, slackPx: Int = 3): Boolean {
        return outer.left <= inner.left + slackPx &&
            outer.top <= inner.top + slackPx &&
            outer.right >= inner.right - slackPx &&
            outer.bottom >= inner.bottom - slackPx
    }

    private fun pickBetterBoundsTextEntry(current: TextEntry?, next: TextEntry?): TextEntry? {
        if (next == null) return current
        if (current == null) return next
        return if (next.area < current.area) next else current
    }

    private fun findBoundsTextEntry(
        node: AccessibilityNodeInfo,
        target: Rect,
        includeNode: (AccessibilityNodeInfo) -> Boolean,
        readNodeText: (AccessibilityNodeInfo) -> String?,
        boundsMatch: (nodeBounds: Rect, preview: Rect) -> Boolean,
    ): TextEntry? {
        var best: TextEntry? = null
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        val bounds = Rect()
        stack.add(node)
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            val owned = current !== node
            try {
                if (!includeNode(current)) continue
                if (shouldSkipAccessibilityNode(current)) continue
                current.getBoundsInScreen(bounds)
                if (boundsMatch(bounds, target)) {
                    readNodeText(current)?.let { raw ->
                        val text = raw.trim()
                        if (text.isNotEmpty()) {
                            val entry = TextEntry(
                                text = text,
                                top = bounds.top,
                                left = bounds.left,
                                right = bounds.right,
                                bottom = bounds.bottom,
                            )
                            best = pickBetterBoundsTextEntry(best, entry)
                        }
                    }
                }
                for (i in 0 until current.childCount) {
                    current.getChild(i)?.let { stack.add(it) }
                }
            } finally {
                if (owned) releaseNode(current)
            }
        }
        return best
    }

    private fun collectIntersectingTexts(
        root: AccessibilityNodeInfo,
        rect: Rect,
        out: MutableList<TextEntry>,
        seen: MutableSet<String>,
        includeNode: (AccessibilityNodeInfo) -> Boolean = ::includeNodeForPickTraversal,
        readNodeText: (AccessibilityNodeInfo) -> String? = ::nodeText,
    ) {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        val bounds = Rect()
        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            val owned = node !== root
            try {
                if (!includeNode(node)) continue
                if (shouldSkipAccessibilityNode(node)) continue
                node.getBoundsInScreen(bounds)
                if (!Rect.intersects(bounds, rect)) continue
                readNodeText(node)?.let { raw ->
                    val text = raw.trim()
                    if (text.isNotEmpty() && seen.add(text)) {
                        out.add(
                            TextEntry(
                                text = text,
                                top = bounds.top,
                                left = bounds.left,
                                right = bounds.right,
                                bottom = bounds.bottom,
                            ),
                        )
                    }
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { stack.add(it) }
                }
            } finally {
                if (owned) releaseNode(node)
            }
        }
    }

    private fun textCandidateAt(bounds: Rect, node: AccessibilityNodeInfo): TextCandidate? {
        if (shouldSkipAccessibilityNode(node)) return null
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) {
            return TextCandidate(
                text = text,
                area = bounds.width().coerceAtLeast(1) * bounds.height().coerceAtLeast(1),
                isPrimaryText = true,
            )
        }
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        if (description.isNotEmpty()) {
            return TextCandidate(
                text = description,
                area = bounds.width().coerceAtLeast(1) * bounds.height().coerceAtLeast(1),
                isPrimaryText = false,
            )
        }
        val stateDescription = node.stateDescription?.toString()?.trim().orEmpty()
        if (stateDescription.isNotEmpty()) {
            return TextCandidate(
                text = stateDescription,
                area = bounds.width().coerceAtLeast(1) * bounds.height().coerceAtLeast(1),
                isPrimaryText = false,
            )
        }
        return null
    }

    private fun previewNodeText(node: AccessibilityNodeInfo): String? {
        if (shouldSkipAccessibilityNode(node)) return null
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        if (description.length > text.length) return description
        if (text.isNotEmpty()) return text
        if (description.isNotEmpty()) return description
        val stateDescription = node.stateDescription?.toString()?.trim().orEmpty()
        if (stateDescription.isNotEmpty()) return stateDescription
        return null
    }

    private fun accessibilityActionLabels(node: AccessibilityNodeInfo): String? {
        val labels = LinkedHashSet<String>()
        for (action in node.actionList) {
            action.label?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { labels.add(it) }
        }
        return labels.joinToString(", ").takeIf { it.isNotEmpty() }
    }

    private fun aggregateDescendantPreviewTexts(
        root: AccessibilityNodeInfo,
        maxDepth: Int = 6,
    ): String? {
        val entries = ArrayList<TextEntry>()
        val seen = LinkedHashSet<String>()
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.add(root to 0)
        val bounds = Rect()
        while (stack.isNotEmpty()) {
            val (node, depth) = stack.removeFirst()
            val owned = node !== root
            try {
                if (!includeNodeForPreviewTextTraversal(node)) continue
                if (shouldSkipAccessibilityNode(node)) continue
                previewNodeText(node)?.let { raw ->
                    val text = raw.trim()
                    if (text.isNotEmpty() && seen.add(text)) {
                        node.getBoundsInScreen(bounds)
                        entries.add(
                            TextEntry(
                                text = text,
                                top = bounds.top,
                                left = bounds.left,
                                right = bounds.right,
                                bottom = bounds.bottom,
                            ),
                        )
                    }
                }
                if (depth < maxDepth) {
                    for (i in 0 until node.childCount) {
                        node.getChild(i)?.let { stack.add(it to depth + 1) }
                    }
                }
            } finally {
                if (owned) releaseNode(node)
            }
        }
        return joinSortedTexts(entries).takeIf { it.isNotBlank() }
    }

    private fun nodePreviewMetadata(node: AccessibilityNodeInfo): String? {
        if (shouldSkipAccessibilityNode(node)) return null
        val candidates = ArrayList<String>()
        previewNodeText(node)?.let { candidates.add(it) }
        accessibilityActionLabels(node)?.let { candidates.add(it) }
        node.paneTitle?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { candidates.add(it) }
        node.tooltipText?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { candidates.add(it) }
        if (previewNodeText(node) == null && node.childCount > 0) {
            aggregateDescendantPreviewTexts(node)?.let { candidates.add(it) }
        }
        return candidates.maxByOrNull { it.length }
    }

    private fun nodeText(node: AccessibilityNodeInfo): String? {
        if (shouldSkipAccessibilityNode(node)) return null
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) return text
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        if (description.isNotEmpty()) return description
        val stateDescription = node.stateDescription?.toString()?.trim().orEmpty()
        if (stateDescription.isNotEmpty()) return stateDescription
        return null
    }

    private fun copyNode(source: AccessibilityNodeInfo): AccessibilityNodeInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AccessibilityNodeInfo(source)
        } else {
            @Suppress("DEPRECATION")
            AccessibilityNodeInfo.obtain(source)
        }

    private fun releaseNode(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        @Suppress("DEPRECATION")
        node.recycle()
    }
}
