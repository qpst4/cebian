package com.slideindex.app.imageeditor.state

import com.slideindex.app.imageeditor.model.EditAction
import com.slideindex.app.imageeditor.model.EditorMode
import com.slideindex.app.imageeditor.model.EditorPoint
import com.slideindex.app.imageeditor.model.NumberBadgeStyle
import com.slideindex.app.imageeditor.model.ShapeType
import kotlin.math.max

class EditorSession {
    interface Listener {
        fun onStateChanged(state: EditorUiState)
    }

    private val listeners = linkedSetOf<Listener>()
    var state: EditorUiState = EditorUiState()
        private set

    fun addListener(listener: Listener) {
        listeners += listener
        listener.onStateChanged(state)
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    fun switchMode(mode: EditorMode) {
        updateState { it.copy(currentMode = mode) }
    }

    fun updateColor(color: Int) {
        updateState { it.copy(currentColor = color) }
    }

    fun updateStrokeWidth(strokeWidth: Float) {
        val width = max(strokeWidth, 1f)
        updateState { current ->
            when (current.currentMode) {
                EditorMode.DOODLE -> current.copy(doodleStrokeWidth = width)
                EditorMode.ERASER -> current.copy(eraserStrokeWidth = width)
                EditorMode.SHAPE -> current.copy(shapeStrokeWidth = width)
                EditorMode.MOSAIC -> current.copy(mosaicStrokeWidth = width)
                EditorMode.NAVIGATE, EditorMode.CROP, EditorMode.TEXT, EditorMode.NUMBER -> current
            }
        }
    }

    fun updateStrokeWidthForMode(mode: EditorMode, strokeWidth: Float) {
        val width = max(strokeWidth, 1f)
        updateState { current ->
            when (mode) {
                EditorMode.DOODLE -> current.copy(doodleStrokeWidth = width)
                EditorMode.ERASER -> current.copy(eraserStrokeWidth = width)
                EditorMode.SHAPE -> current.copy(shapeStrokeWidth = width)
                EditorMode.MOSAIC -> current.copy(mosaicStrokeWidth = width)
                EditorMode.NAVIGATE, EditorMode.CROP, EditorMode.TEXT, EditorMode.NUMBER -> current
            }
        }
    }

    fun updateShapeType(shapeType: ShapeType) {
        updateState { it.copy(currentShapeType = shapeType) }
    }

    fun updateNumberStyle(style: NumberBadgeStyle) {
        updateState { it.copy(currentNumberStyle = style) }
    }

    fun updateNumberBadgeSize(size: Float) {
        updateState { it.copy(numberBadgeSize = max(size, 12f)) }
    }

    fun updateNumberAction(id: String, transform: (EditAction.NumberBadge) -> EditAction.NumberBadge) {
        updateState { current ->
            current.copy(
                undoStack = current.undoStack.map { action ->
                    if (action is EditAction.NumberBadge && action.id == id) transform(action) else action
                },
            )
        }
    }

    fun addAction(action: EditAction) {
        updateState {
            it.copy(
                undoStack = it.undoStack + action,
                redoStack = emptyList(),
            )
        }
    }

    fun nextNumberValue(): Int = state.nextNumberValue()

    fun addNumberBadge(anchor: EditorPoint, badgeSize: Float, id: String) {
        addAction(
            EditAction.NumberBadge(
                id = id,
                number = nextNumberValue(),
                anchor = anchor,
                color = state.currentColor,
                style = state.currentNumberStyle,
                badgeSize = badgeSize,
            ),
        )
    }

    fun replaceActions(actions: List<EditAction>) {
        updateState {
            it.copy(undoStack = actions, redoStack = emptyList())
        }
    }

    fun updateTextAction(id: String, transform: (EditAction.Text) -> EditAction.Text) {
        updateState { current ->
            current.copy(
                undoStack = current.undoStack.map { action ->
                    if (action is EditAction.Text && action.id == id) transform(action) else action
                },
            )
        }
    }

    fun updateShapeAction(id: String, transform: (EditAction.Shape) -> EditAction.Shape) {
        updateState { current ->
            current.copy(
                undoStack = current.undoStack.map { action ->
                    if (action is EditAction.Shape && action.id == id) transform(action) else action
                },
            )
        }
    }

    fun removeAction(id: String) {
        updateState { current ->
            current.copy(
                undoStack = current.undoStack.filterNot { it.id == id },
                redoStack = current.redoStack.filterNot { it.id == id },
            )
        }
    }

    fun undo() {
        updateState { current ->
            if (current.undoStack.isEmpty()) return@updateState current
            val last = current.undoStack.last()
            current.copy(
                undoStack = current.undoStack.dropLast(1),
                redoStack = current.redoStack + last,
            )
        }
    }

    fun redo() {
        updateState { current ->
            if (current.redoStack.isEmpty()) return@updateState current
            val last = current.redoStack.last()
            current.copy(
                undoStack = current.undoStack + last,
                redoStack = current.redoStack.dropLast(1),
            )
        }
    }

    fun clearActions() {
        updateState { it.copy(undoStack = emptyList(), redoStack = emptyList()) }
    }

    private fun updateState(transform: (EditorUiState) -> EditorUiState) {
        state = transform(state)
        listeners.forEach { it.onStateChanged(state) }
    }
}
