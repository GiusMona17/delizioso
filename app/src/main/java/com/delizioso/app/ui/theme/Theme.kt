package com.delizioso.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Delizioso! theme — Culinary Clay design system.
 *
 * The clay shadows travel with the colour scheme: neumorphism is drawn, not
 * tinted, so a dark palette needs its own shadow set rather than an inverted one.
 * [LocalClayShadows] carries it to every `clay*` modifier.
 */
@Composable
fun DeliziosoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalClayShadows provides if (darkTheme) ClayShadows.Dark else ClayShadows.Light
    ) {
        MaterialTheme(
            colorScheme = appColorScheme(darkTheme),
            typography = AppTypography,
            shapes = ClayShapes,
            content = content,
        )
    }
}
