package com.delizioso.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape scale per DESIGN.md (rounded, pill-oriented — avoid sharp corners entirely):
 *  - sm 0.5rem = 8dp
 *  - DEFAULT 1rem = 16dp
 *  - md 1.5rem = 24dp  (minimum for standard cards)
 *  - lg 2rem = 32dp
 *  - xl 3rem = 48dp
 */
val ClayShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Fully rounded "pill" shape used for buttons, chips and the dock. */
val PillShape = RoundedCornerShape(percent = 50)

/** Card image inset radius (image sits comfortably within the clay frame). */
val CardImageRadius = 16.dp
