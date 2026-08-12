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

/**
 * Culinary Clay after dark.
 *
 * Not an inversion: the surfaces stay warm and stop well short of black, because
 * the claymorphism needs room for a shadow *below* the container and a highlight
 * *above* it. On true black the shadow has nowhere to go and every card flattens
 * into the background. Mint lightens to stay legible on a dark ground, and peach
 * and olive are desaturated so they read as accents rather than glow.
 */
val DarkBackground = Color(0xFF141513)
val DarkSurfaceLowest = Color(0xFF0F100E)
val DarkSurfaceLow = Color(0xFF1B1D1A)
val DarkSurfaceContainer = Color(0xFF1F211E)
val DarkSurfaceHigh = Color(0xFF262825)
val DarkSurfaceHighest = Color(0xFF2E302C)
val DarkSurfaceVariant = Color(0xFF3F443D)
val DarkOnSurface = Color(0xFFE3E3DE)
val DarkOnSurfaceVariant = Color(0xFFC2C9BD)
val DarkOutline = Color(0xFF8C9488)
val DarkOutlineVariant = Color(0xFF424940)

val DarkPrimary = Color(0xFF8BE58F)
val DarkOnPrimary = Color(0xFF00390D)
val DarkPrimaryContainer = Color(0xFF005316)
val DarkOnPrimaryContainer = Color(0xFFA6F2A6)

val DarkSecondary = Color(0xFFE3C0A0)
val DarkOnSecondary = Color(0xFF422C16)
val DarkSecondaryContainer = Color(0xFF5A422A)
val DarkOnSecondaryContainer = Color(0xFFFFDCBE)

val DarkTertiary = Color(0xFFCAC99F)
val DarkOnTertiary = Color(0xFF323215)
val DarkTertiaryContainer = Color(0xFF484828)
val DarkOnTertiaryContainer = Color(0xFFE6E5B9)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

private val darkColorScheme: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = Primary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkBackground,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkPrimary,
    inverseSurface = InverseOnSurface,
    inverseOnSurface = InverseSurface,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceBright = DarkSurfaceHighest,
    surfaceDim = DarkSurfaceLowest,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainerLowest = DarkSurfaceLowest,
)

fun appColorScheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme else lightColorScheme
