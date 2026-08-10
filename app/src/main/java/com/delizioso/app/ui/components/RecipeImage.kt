package com.delizioso.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

/**
 * Recipe photo with the clay "no photo yet" fallback.
 *
 * `imageUri` is a remote URL for imported recipes and an absolute internal-storage
 * path for photos the user picked — Coil needs a [File] for the latter.
 */
@Composable
fun RecipeImage(
    imageUri: String?,
    modifier: Modifier = Modifier,
    placeholderIconSize: Dp = 40.dp,
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                )
            )
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri.isNullOrBlank()) {
            Icon(
                Icons.Filled.Restaurant,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(placeholderIconSize),
            )
        } else {
            AsyncImage(
                model = if (imageUri.startsWith("/")) File(imageUri) else imageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
