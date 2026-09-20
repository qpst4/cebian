package com.slideindex.app.clipboardoverlay

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.slideindex.app.R

internal object ClipboardOverlayChipBinder {
    fun bind(
        view: View,
        icon: android.graphics.drawable.Drawable?,
        label: CharSequence?,
        description: CharSequence?,
        tint: Boolean,
        onClick: () -> Unit,
    ) {
        val iconView = view.requireViewById<ImageView>(R.id.overlay_action_chip_icon)
        val textView = view.requireViewById<TextView>(R.id.overlay_action_chip_text)
        iconView.setImageDrawable(icon)
        if (!tint) {
            iconView.imageTintList = null
        }
        val hasText = !label.isNullOrEmpty()
        textView.text = label
        textView.visibility = if (hasText) View.VISIBLE else View.GONE
        setMargins(iconView, textView, hasText)
        view.setOnClickListener { onClick() }
        view.contentDescription = description
        view.visibility = View.VISIBLE
        view.alpha = 1f
    }

    private fun setMargins(iconView: View, textView: View, hasText: Boolean) {
        val iconParams = iconView.layoutParams as LinearLayout.LayoutParams
        val textParams = textView.layoutParams as LinearLayout.LayoutParams
        if (hasText) {
            iconParams.marginStart = iconView.resources.getDimensionPixelSize(R.dimen.overlay_action_chip_padding_start)
            iconParams.marginEnd = iconView.resources.getDimensionPixelSize(R.dimen.overlay_action_chip_spacing)
            textParams.marginStart = 0
            textParams.marginEnd = textView.resources.getDimensionPixelSize(R.dimen.overlay_action_chip_padding_end)
        } else {
            val paddingHorizontal =
                iconView.resources.getDimensionPixelSize(R.dimen.overlay_action_chip_icon_only_padding_horizontal)
            iconParams.marginStart = paddingHorizontal
            iconParams.marginEnd = paddingHorizontal
        }
        iconView.layoutParams = iconParams
        textView.layoutParams = textParams
    }
}
