package com.catlife.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import com.catlife.app.R

@Composable
internal fun appColorScheme(): ColorScheme {
    val background = colorResource(R.color.home_background)
    val surface = colorResource(R.color.home_card_background)
    val charcoal = colorResource(R.color.home_button_background)
    val light = colorResource(R.color.home_button_content)
    val selection = colorResource(R.color.ui_selection_background)
    val muted = colorResource(R.color.ui_muted_content)
    val outline = colorResource(R.color.ui_outline)
    return lightColorScheme(
        primary = charcoal, onPrimary = light,
        primaryContainer = selection, onPrimaryContainer = charcoal,
        secondary = charcoal, onSecondary = light,
        secondaryContainer = selection, onSecondaryContainer = charcoal,
        tertiary = charcoal, onTertiary = light,
        tertiaryContainer = selection, onTertiaryContainer = charcoal,
        background = background, onBackground = charcoal,
        surface = surface, onSurface = charcoal,
        surfaceVariant = selection, onSurfaceVariant = muted,
        surfaceTint = charcoal,
        inverseSurface = charcoal, inverseOnSurface = light, inversePrimary = light,
        outline = muted, outlineVariant = outline,
        surfaceBright = surface, surfaceDim = selection,
        surfaceContainer = surface, surfaceContainerHigh = surface,
        surfaceContainerHighest = selection, surfaceContainerLow = background,
        surfaceContainerLowest = surface,
    )
}
