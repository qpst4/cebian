package com.slideindex.app.imageeditor

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.R as MaterialR

/** 将 Compose [ColorScheme] 映射为 Material3 个性化颜色资源，供 ResourcesLoader 覆盖。 */
internal fun ColorScheme.toMaterialPersonalizedColorMap(): Map<Int, Int> =
    mapOf(
        MaterialR.color.material_personalized_color_primary to primary.toArgb(),
        MaterialR.color.material_personalized_color_on_primary to onPrimary.toArgb(),
        MaterialR.color.material_personalized_color_primary_inverse to inversePrimary.toArgb(),
        MaterialR.color.material_personalized_color_primary_container to primaryContainer.toArgb(),
        MaterialR.color.material_personalized_color_on_primary_container to onPrimaryContainer.toArgb(),
        MaterialR.color.material_personalized_color_secondary to secondary.toArgb(),
        MaterialR.color.material_personalized_color_on_secondary to onSecondary.toArgb(),
        MaterialR.color.material_personalized_color_secondary_container to secondaryContainer.toArgb(),
        MaterialR.color.material_personalized_color_on_secondary_container to onSecondaryContainer.toArgb(),
        MaterialR.color.material_personalized_color_tertiary to tertiary.toArgb(),
        MaterialR.color.material_personalized_color_on_tertiary to onTertiary.toArgb(),
        MaterialR.color.material_personalized_color_tertiary_container to tertiaryContainer.toArgb(),
        MaterialR.color.material_personalized_color_on_tertiary_container to onTertiaryContainer.toArgb(),
        MaterialR.color.material_personalized_color_background to background.toArgb(),
        MaterialR.color.material_personalized_color_on_background to onBackground.toArgb(),
        MaterialR.color.material_personalized_color_surface to surface.toArgb(),
        MaterialR.color.material_personalized_color_on_surface to onSurface.toArgb(),
        MaterialR.color.material_personalized_color_surface_variant to surfaceVariant.toArgb(),
        MaterialR.color.material_personalized_color_on_surface_variant to onSurfaceVariant.toArgb(),
        MaterialR.color.material_personalized_color_surface_inverse to inverseSurface.toArgb(),
        MaterialR.color.material_personalized_color_on_surface_inverse to inverseOnSurface.toArgb(),
        MaterialR.color.material_personalized_color_surface_bright to surfaceBright.toArgb(),
        MaterialR.color.material_personalized_color_surface_dim to surfaceDim.toArgb(),
        MaterialR.color.material_personalized_color_surface_container to surfaceContainer.toArgb(),
        MaterialR.color.material_personalized_color_surface_container_low to surfaceContainerLow.toArgb(),
        MaterialR.color.material_personalized_color_surface_container_high to surfaceContainerHigh.toArgb(),
        MaterialR.color.material_personalized_color_surface_container_lowest to surfaceContainerLowest.toArgb(),
        MaterialR.color.material_personalized_color_surface_container_highest to surfaceContainerHighest.toArgb(),
        MaterialR.color.material_personalized_color_outline to outline.toArgb(),
        MaterialR.color.material_personalized_color_outline_variant to outlineVariant.toArgb(),
        MaterialR.color.material_personalized_color_error to error.toArgb(),
        MaterialR.color.material_personalized_color_on_error to onError.toArgb(),
        MaterialR.color.material_personalized_color_error_container to errorContainer.toArgb(),
        MaterialR.color.material_personalized_color_on_error_container to onErrorContainer.toArgb(),
        MaterialR.color.material_personalized_color_control_activated to primary.toArgb(),
        MaterialR.color.material_personalized_color_control_normal to onSurfaceVariant.toArgb(),
        MaterialR.color.material_personalized_color_control_highlight to primary.copy(alpha = 0.12f).toArgb(),
        MaterialR.color.material_personalized_color_text_primary_inverse to inverseOnSurface.toArgb(),
        MaterialR.color.material_personalized_color_text_secondary_and_tertiary_inverse to inverseOnSurface.toArgb(),
        MaterialR.color.material_personalized_color_text_secondary_and_tertiary_inverse_disabled to
            inverseOnSurface.copy(alpha = 0.38f).toArgb(),
        MaterialR.color.material_personalized_color_text_primary_inverse_disable_only to
            inverseOnSurface.copy(alpha = 0.38f).toArgb(),
        MaterialR.color.material_personalized_color_text_hint_foreground_inverse to
            inverseOnSurface.copy(alpha = 0.6f).toArgb(),
    )
