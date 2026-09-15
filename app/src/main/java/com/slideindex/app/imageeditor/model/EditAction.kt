package com.slideindex.app.imageeditor.model

sealed interface EditAction {
    val id: String

    data class Doodle(
        override val id: String,
        val points: List<EditorPoint>,
        val color: Int,
        val strokeWidth: Float,
        val isEraser: Boolean = false,
    ) : EditAction

    data class Shape(
        override val id: String,
        val type: ShapeType,
        val start: EditorPoint,
        val end: EditorPoint,
        val color: Int,
        val strokeWidth: Float,
    ) : EditAction

    data class Text(
        override val id: String,
        val text: String,
        val anchor: EditorPoint,
        val color: Int,
        val textSize: Float,
    ) : EditAction

    data class Mosaic(
        override val id: String,
        val points: List<EditorPoint>,
        val strokeWidth: Float,
    ) : EditAction

    data class NumberBadge(
        override val id: String,
        val number: Int,
        val anchor: EditorPoint,
        val color: Int,
        val style: NumberBadgeStyle,
        val badgeSize: Float,
    ) : EditAction
}
