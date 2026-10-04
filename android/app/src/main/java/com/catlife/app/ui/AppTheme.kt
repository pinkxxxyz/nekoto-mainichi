package com.catlife.app.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.catlife.app.R

val KleeOneFamily = FontFamily(
    Font(
        R.font.klee_one_regular,
        weight = FontWeight.Normal
    ),
    Font(
        R.font.klee_one_semibold,
        weight = FontWeight.SemiBold
    )
)

private val defaultTypography = Typography()

val KorokkeTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(
        fontFamily = KleeOneFamily,
        fontWeight = FontWeight.SemiBold
    ),
    displayMedium = defaultTypography.displayMedium.copy(
        fontFamily = KleeOneFamily,
        fontWeight = FontWeight.SemiBold
    ),
    displaySmall = defaultTypography.displaySmall.copy(
        fontFamily = KleeOneFamily,
        fontWeight = FontWeight.SemiBold
    ),
    headlineLarge = defaultTypography.headlineLarge.copy(
        fontFamily = KleeOneFamily,
        fontWeight = FontWeight.SemiBold
    ),
    headlineMedium = defaultTypography.headlineMedium.copy(
        fontFamily = KleeOneFamily,
        fontWeight = FontWeight.SemiBold
    ),
    headlineSmall = defaultTypography.headlineSmall.copy(
        fontFamily = KleeOneFamily,
        fontWeight = FontWeight.SemiBold
    ),
    titleLarge = defaultTypography.titleLarge.copy(
        fontFamily = KleeOneFamily,
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = defaultTypography.titleMedium.copy(
        fontFamily = KleeOneFamily,
        fontWeight = FontWeight.SemiBold
    ),
    titleSmall = defaultTypography.titleSmall.copy(
        fontFamily = KleeOneFamily,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = defaultTypography.bodyLarge.copy(
        fontFamily = KleeOneFamily,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = defaultTypography.bodyMedium.copy(
        fontFamily = KleeOneFamily,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = defaultTypography.bodySmall.copy(
        fontFamily = KleeOneFamily,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = defaultTypography.labelLarge.copy(
        fontFamily = KleeOneFamily,
        fontWeight = FontWeight.SemiBold
    ),
    labelMedium = defaultTypography.labelMedium.copy(
        fontFamily = KleeOneFamily,
        fontWeight = FontWeight.Normal
    ),
    labelSmall = defaultTypography.labelSmall.copy(
        fontFamily = KleeOneFamily,
        fontWeight = FontWeight.Normal
    )
)
