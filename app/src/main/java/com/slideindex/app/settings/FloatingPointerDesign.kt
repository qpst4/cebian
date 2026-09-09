package com.slideindex.app.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.slideindex.app.R

enum class FloatingPointerDesign(
    val id: String,
    @DrawableRes val drawableResId: Int = 0,
    val tipXFraction: Float = 0f,
    val tipYFraction: Float = 0f,
    @StringRes val labelResId: Int
) {
    RING(
        id = "ring",
        labelResId = R.string.floating_pointer_design_ring
    ),
    ARROW1("arrow1", R.drawable.qc_cursor_arrow1, 0.032f, 0.025f, R.string.floating_pointer_design_arrow_1),
    ARROW2("arrow2", R.drawable.qc_cursor_arrow2, 0.0992f, 0.04f, R.string.floating_pointer_design_arrow_2),
    ARROW3("arrow3", R.drawable.qc_cursor_arrow3, 0.03f, 0.03f, R.string.floating_pointer_design_arrow_3),
    ARROW4("arrow4", R.drawable.qc_cursor_arrow4, 0.0977f, 0.035f, R.string.floating_pointer_design_arrow_4),
    ARROW5("arrow5", R.drawable.qc_cursor_arrow5, 0.046f, 0.03f, R.string.floating_pointer_design_arrow_5),
    ARROW6("arrow6", R.drawable.qc_cursor_arrow6, 0.02f, 0.01f, R.string.floating_pointer_design_arrow_6),
    ARROW7("arrow7", R.drawable.qc_cursor_arrow7, 0.43f, 0.43f, R.string.floating_pointer_design_arrow_7),
    ARROW8("arrow8", R.drawable.qc_cursor_arrow8, 0.5f, 0.01f, R.string.floating_pointer_design_arrow_8),
    ARROW9("arrow9", R.drawable.qc_cursor_arrow9, 0.02f, 0.01f, R.string.floating_pointer_design_arrow_9),
    ARROW10("arrow10", R.drawable.qc_cursor_arrow10, 0.01f, 0.0f, R.string.floating_pointer_design_arrow_10),
    ARROW11("arrow11", R.drawable.qc_cursor_arrow11, 0.5f, 0.0f, R.string.floating_pointer_design_arrow_11),
    ARROW12("arrow12", R.drawable.qc_cursor_arrow12, 0.149f, 0.09f, R.string.floating_pointer_design_arrow_12),
    ARROW13("arrow13", R.drawable.qc_cursor_arrow13, 0.288f, 0.292f, R.string.floating_pointer_design_arrow_13),
    ARROW14("arrow14", R.drawable.qc_cursor_arrow14, 0.589f, 0.181f, R.string.floating_pointer_design_arrow_14),
    ARROW15("arrow15", R.drawable.qc_cursor_arrow15, 0.02f, 0.0214f, R.string.floating_pointer_design_arrow_15),
    ARROW16("arrow16", R.drawable.qc_cursor_arrow16, 0.01f, 0.01f, R.string.floating_pointer_design_arrow_16),
    ARROW17("arrow17", R.drawable.qc_cursor_arrow17, 0.0279f, 0.0279f, R.string.floating_pointer_design_arrow_17),
    ARROW18("arrow18", R.drawable.qc_cursor_arrow18, 0.244f, 0.243f, R.string.floating_pointer_design_arrow_18),
    ARROW19("arrow19", R.drawable.qc_cursor_arrow19, 0.5f, 0.0f, R.string.floating_pointer_design_arrow_19),
    HAND1("hand1", R.drawable.qc_cursor_hand1, 0.385f, 0.04f, R.string.floating_pointer_design_hand_1),
    HAND2("hand2", R.drawable.qc_cursor_hand2, 0.265f, 0.05f, R.string.floating_pointer_design_hand_2),
    HAND3("hand3", R.drawable.qc_cursor_hand3, 0.4444f, 0.0222f, R.string.floating_pointer_design_hand_3),
    HAND4("hand4", R.drawable.qc_cursor_hand4, 0.371f, 0.0229f, R.string.floating_pointer_design_hand_4),
    HAND5("hand5", R.drawable.qc_cursor_hand5, 0.259f, 0.0179f, R.string.floating_pointer_design_hand_5),
    HAND6("hand6", R.drawable.qc_cursor_hand6, 0.4223f, 0.0233f, R.string.floating_pointer_design_hand_6),
    CROSSHAIR1("crosshair1", R.drawable.qc_cursor_crosshair1, 0.5f, 0.5f, R.string.floating_pointer_design_crosshair_1),
    CROSSHAIR2("crosshair2", R.drawable.qc_cursor_crosshair2, 0.5f, 0.5f, R.string.floating_pointer_design_crosshair_2),
    CROSSHAIR3("crosshair3", R.drawable.qc_cursor_crosshair3, 0.5f, 0.5f, R.string.floating_pointer_design_crosshair_3),
    CROSSHAIR4("crosshair4", R.drawable.qc_cursor_crosshair4, 0.5f, 0.5f, R.string.floating_pointer_design_crosshair_4),
    CROSSHAIR5("crosshair5", R.drawable.qc_cursor_crosshair5, 0.5f, 0.5f, R.string.floating_pointer_design_crosshair_5),
    CROSSHAIR6("crosshair6", R.drawable.qc_cursor_crosshair6, 0.5f, 0.5f, R.string.floating_pointer_design_crosshair_6),
    CROSSHAIR7("crosshair7", R.drawable.qc_cursor_crosshair7, 0.5f, 0.5f, R.string.floating_pointer_design_crosshair_7),
    CROSSHAIR8("crosshair8", R.drawable.qc_cursor_crosshair8, 0.5f, 0.5f, R.string.floating_pointer_design_crosshair_8),
    ;

    val isRing: Boolean
        get() = this == RING

    val isBitmap: Boolean
        get() = !isRing

    companion object {
        val bitmapDesigns: List<FloatingPointerDesign> = entries.filter { it.isBitmap }

        fun fromId(id: String?): FloatingPointerDesign =
            entries.firstOrNull { it.id == id } ?: RING
    }
}
