// Kyant0/AndroidLiquidGlass — Apache-2.0. Adapted for com.slideindex.app.
package com.slideindex.app.ui.miuix.bottombar.liquid

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls

fun BackdropEffectScope.vibrancy() {
    colorControls(
        brightness = 0f,
        contrast = 1f,
        saturation = 1.5f,
    )
}
