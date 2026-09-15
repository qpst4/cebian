package com.slideindex.app.imageeditor.state

import android.graphics.Color
import com.slideindex.app.imageeditor.model.EditAction
import com.slideindex.app.imageeditor.model.EditorMode
import com.slideindex.app.imageeditor.model.ShapeType

data class EditorUiState(
    val currentMode: EditorMode = EditorMode.NAVIGATE,
    val currentColor: Int = Color.RED,
    val doodleStrokeWidth: Float = 16f,
    val eraserStrokeWidth: Float = 28f,
    val shapeStrokeWidth: Float = 18f,
    val mosaicStrokeWidth: Float = 42f,
    val currentShapeType: ShapeType = ShapeType.RECTANGLE,
    val undoStack: List<EditAction> = emptyList(),
    val redoStack: List<EditAction> = emptyList(),
) {
    val currentStrokeWidth: Float
        get() = brushWidthFor(currentMode)

    val visibleActions: List<EditAction>
        get() = undoStack

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    fun brushWidthFor(mode: EditorMode): Float = when (mode) {
        EditorMode.DOODLE -> doodleStrokeWidth
        EditorMode.ERASER -> eraserStrokeWidth
        EditorMode.SHAPE -> shapeStrokeWidth
        EditorMode.MOSAIC -> mosaicStrokeWidth
        EditorMode.NAVIGATE, EditorMode.CROP, EditorMode.TEXT -> doodleStrokeWidth
    }

    companion object {
        @JvmStatic
        fun defaults(): EditorUiState = EditorUiState()
    }
}
