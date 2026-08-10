package com.delizioso.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.delizioso.app.R

/**
 * Quicksand across all levels (per DESIGN.md). One variable font file serves every
 * weight via the wght axis (300–700, API 26+).
 *
 * The `variationSettings` are what actually move the axis — without them every
 * weight renders at the file's default instance (300 / Light), which is far too
 * thin for the mockups' "plump" headlines.
 */
@OptIn(ExperimentalTextApi::class)
private fun quicksand(weight: FontWeight) = Font(
    resId = R.font.quicksand_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Quicksand = FontFamily(
    quicksand(FontWeight.Light),
    quicksand(FontWeight.Normal),
    quicksand(FontWeight.Medium),
    quicksand(FontWeight.SemiBold),
    quicksand(FontWeight.Bold),
)

/**
 * Type scale per DESIGN.md:
 *  - headline-xl 32/40 Bold -0.02em
 *  - headline-lg 24/32 Bold
 *  - headline-lg-mobile 22/28 Bold
 *  - body-lg 18/26 Medium
 *  - body-md 16/24 Medium
 *  - label-md 14/20 SemiBold +0.01em
 *  - label-sm 12/16 Bold +0.03em
 */
val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Quicksand,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.64).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Quicksand,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineMedium = TextStyle(
        fontFamily = Quicksand,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleLarge = TextStyle(
        fontFamily = Quicksand,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontFamily = Quicksand,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyMedium = TextStyle(
        fontFamily = Quicksand,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelLarge = TextStyle(
        fontFamily = Quicksand,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.14.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Quicksand,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.36.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Quicksand,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
)
