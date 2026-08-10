package com.delizioso.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Delizioso! theme — Culinary Clay design system.
 *
 * DESIGN.md ships only a light palette; `darkTheme` is accepted for future use
 * but currently resolves to the light scheme (documented limitation).
 */
@Composable
fun DeliziosoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = appColorScheme(),
        typography = AppTypography,
        shapes = ClayShapes,
        content = content,
    )
}
