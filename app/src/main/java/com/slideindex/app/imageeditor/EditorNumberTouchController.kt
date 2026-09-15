package com.slideindex.app.imageeditor

import com.slideindex.app.imageeditor.model.EditAction
import com.slideindex.app.imageeditor.model.EditorMode
import com.slideindex.app.imageeditor.model.EditorPoint
import com.slideindex.app.imageeditor.state.EditorSession
import com.slideindex.app.imageeditor.state.EditorUiState
import com.slideindex.app.imageeditor.utils.EditorRenderUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Touch handling for [EditorMode.NUMBER] used by [com.slideindex.app.imageeditor.ui.ImageEditorView].
 */
class EditorNumberTouchController(
    private val buildActionId: () -> String,
    private val suggestedBadgeSize: () -> Float,
    private val minBadgeSize: () -> Float,
    private val invalidate: () -> Unit,
) {
    var selectedNumberId: String? = null
        private set

    private var draggingNumberId: String? = null
    private var dragStartImagePoint: EditorPoint? = null
    private var dragStartNumberAnchor: EditorPoint? = null
    private var dragStartNumberSize: Float? = null
    private var dragStartNumberRadius: Float = 1f
    private var numberGestureMoved = false
    private var pendingTapPoint: EditorPoint? = null
    private var isResizeGesture = false

    fun clearSelectionIfLeavingMode(mode: EditorMode) {
        if (mode != EditorMode.NUMBER) {
            selectedNumberId = null
        }
    }

    fun clearSelection() {
        selectedNumberId = null
    }

    fun onActionDown(
        session: EditorSession?,
        uiState: EditorUiState,
        point: EditorPoint,
    ): Boolean {
        val existing = findNumberAtPoint(uiState, point)
            ?: findNumberHandleAtPoint(uiState, point)
        selectedNumberId = existing?.id
        if (existing != null) {
            draggingNumberId = existing.id
            dragStartImagePoint = point
            dragStartNumberAnchor = existing.anchor
            dragStartNumberSize = existing.badgeSize
            dragStartNumberRadius = max(
                distance(
                    existing.anchor,
                    EditorRenderUtils.buildNumberBadgeResizeHandleCenter(existing),
                ),
                1f,
            )
            isResizeGesture = isResizeHandleHit(existing, point)
            numberGestureMoved = false
            pendingTapPoint = null
            return true
        }
        pendingTapPoint = point
        draggingNumberId = null
        numberGestureMoved = false
        isResizeGesture = false
        return true
    }

    fun onActionMove(
        session: EditorSession?,
        point: EditorPoint,
    ) {
        val tap = pendingTapPoint
        if (tap != null) {
            if (!numberGestureMoved) {
                val dx = point.x - tap.x
                val dy = point.y - tap.y
                if (abs(dx) > 4f || abs(dy) > 4f) {
                    numberGestureMoved = true
                }
            }
            return
        }
        val id = draggingNumberId ?: return
        val startPoint = dragStartImagePoint ?: return
        if (isResizeGesture) {
            val anchor = dragStartNumberAnchor ?: return
            val baseSize = dragStartNumberSize ?: return
            val radius = max(distance(anchor, point), 1f)
            if (!numberGestureMoved && abs(radius - dragStartNumberRadius) > 4f) {
                numberGestureMoved = true
            }
            val scale = radius / dragStartNumberRadius
            session?.updateNumberAction(id) { badge ->
                badge.copy(badgeSize = max(baseSize * scale, minBadgeSize()))
            }
        } else {
            val anchor = dragStartNumberAnchor ?: return
            val dx = point.x - startPoint.x
            val dy = point.y - startPoint.y
            if (!numberGestureMoved && (abs(dx) > 4f || abs(dy) > 4f)) {
                numberGestureMoved = true
            }
            session?.updateNumberAction(id) { badge ->
                badge.copy(
                    anchor = EditorPoint(anchor.x + dx, anchor.y + dy),
                )
            }
        }
        invalidate()
    }

    fun onActionUp(session: EditorSession?) {
        if (pendingTapPoint != null && !numberGestureMoved) {
            val anchor = pendingTapPoint ?: return
            session?.addNumberBadge(anchor, suggestedBadgeSize(), buildActionId())
            selectedNumberId = session?.state?.visibleActions
                ?.filterIsInstance<EditAction.NumberBadge>()
                ?.lastOrNull()
                ?.id
        }
        draggingNumberId = null
        dragStartImagePoint = null
        dragStartNumberAnchor = null
        dragStartNumberSize = null
        dragStartNumberRadius = 1f
        pendingTapPoint = null
        numberGestureMoved = false
        isResizeGesture = false
    }

    fun resetGesture() {
        draggingNumberId = null
        dragStartImagePoint = null
        dragStartNumberAnchor = null
        dragStartNumberSize = null
        dragStartNumberRadius = 1f
        pendingTapPoint = null
        numberGestureMoved = false
        isResizeGesture = false
    }

    fun selectedBadge(uiState: EditorUiState): EditAction.NumberBadge? = selectedNumberAction(uiState)

    private fun selectedNumberAction(uiState: EditorUiState): EditAction.NumberBadge? {
        val id = selectedNumberId ?: return null
        return uiState.visibleActions.filterIsInstance<EditAction.NumberBadge>().firstOrNull { it.id == id }
    }

    private fun findNumberAtPoint(uiState: EditorUiState, point: EditorPoint): EditAction.NumberBadge? {
        return uiState.visibleActions
            .filterIsInstance<EditAction.NumberBadge>()
            .lastOrNull { isNumberBadgeBodyHit(it, point) }
    }

    private fun isNumberBadgeBodyHit(action: EditAction.NumberBadge, point: EditorPoint): Boolean {
        val radius = EditorRenderUtils.numberBadgeBodyHitRadius(action.badgeSize)
        return distance(action.anchor, point) <= radius
    }

    private fun findNumberHandleAtPoint(uiState: EditorUiState, point: EditorPoint): EditAction.NumberBadge? {
        return uiState.visibleActions
            .filterIsInstance<EditAction.NumberBadge>()
            .lastOrNull { isResizeHandleHit(it, point) }
    }

    private fun isResizeHandleHit(action: EditAction.NumberBadge, point: EditorPoint): Boolean {
        val center = EditorRenderUtils.buildNumberBadgeResizeHandleCenter(action)
        return distance(center, point) <= handleHitRadius(action)
    }

    private fun handleHitRadius(action: EditAction.NumberBadge): Float =
        max(14f, action.badgeSize * 0.18f)

    private fun distance(a: EditorPoint, b: EditorPoint): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}
