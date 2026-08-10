package com.delizioso.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Culinary Clay — Material 3 token set from
 * stitch_reel_recipe_collector/culinary_clay/DESIGN.md
 */
// Surface / background (creamy white)
val CreamyWhite = Color(0xFFFBF9F8)
val SurfaceDim = Color(0xFFDBD9D9)
val SurfaceBright = Color(0xFFFBF9F8)
val SurfaceLowest = Color(0xFFFFFFFF)
val SurfaceLow = Color(0xFFF5F3F3)
val SurfaceContainer = Color(0xFFEFEDED)
val SurfaceHigh = Color(0xFFEAE8E7)
val SurfaceHighest = Color(0xFFE4E2E2)
val SurfaceVariant = Color(0xFFE4E2E2)
val OnSurface = Color(0xFF1B1C1C)
val OnSurfaceVariant = Color(0xFF3F4A3D)
val InverseSurface = Color(0xFF303030)
val InverseOnSurface = Color(0xFFF2F0F0)
val Outline = Color(0xFF6F7A6C)
val OutlineVariant = Color(0xFFBECAB9)
val SurfaceTint = Color(0xFF006E20)

// Primary (mint green)
val Primary = Color(0xFF006E20)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFF98FF98)
val OnPrimaryContainer = Color(0xFF007924)
val InversePrimary = Color(0xFF77DC7A)
val PrimaryFixed = Color(0xFF93F993)
val PrimaryFixedDim = Color(0xFF77DC7A)
val OnPrimaryFixed = Color(0xFF002105)
val OnPrimaryFixedVariant = Color(0xFF005316)

// Secondary (soft peach)
val Secondary = Color(0xFF74593F)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFFED9B8)
val OnSecondaryContainer = Color(0xFF795D43)
val SecondaryFixed = Color(0xFFFFDCBE)
val SecondaryFixedDim = Color(0xFFE3C0A0)
val OnSecondaryFixed = Color(0xFF2A1704)
val OnSecondaryFixedVariant = Color(0xFF5A422A)

// Tertiary (olive)
val Tertiary = Color(0xFF60603E)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFECEABE)
val OnTertiaryContainer = Color(0xFF6A6946)
val TertiaryFixed = Color(0xFFE6E5B9)
val TertiaryFixedDim = Color(0xFFCAC99F)
val OnTertiaryFixed = Color(0xFF1D1D03)
val OnTertiaryFixedVariant = Color(0xFF484828)

// Error
val Error = Color(0xFFBA1A1A)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF93000A)

private val lightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    inversePrimary = InversePrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = CreamyWhite,
    onBackground = OnSurface,
    surface = CreamyWhite,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceTint = SurfaceTint,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,
    outline = Outline,
    outlineVariant = OutlineVariant,
    surfaceBright = SurfaceBright,
    surfaceDim = SurfaceDim,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHighest,
    surfaceContainerLow = SurfaceLow,
    surfaceContainerLowest = SurfaceLowest,
)

// DESIGN.md ships only a light palette; reuse the light tokens until a dark one is specified.
private val darkColorScheme: ColorScheme = lightColorScheme

fun appColorScheme(): ColorScheme = lightColorScheme
