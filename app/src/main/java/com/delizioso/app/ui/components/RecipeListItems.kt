package com.delizioso.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.delizioso.app.R
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.clayCard

/**
 * The denser library layouts.
 *
 * [ClayRecipeCard] gives one recipe the whole width and a big photo, which stops
 * paying once the library is long: these trade image size for how many recipes
 * fit on screen, and the choice is the user's.
 */

/** Two-per-row tile: a small cover, the title, and the time. */
@Composable
fun ClayRecipeTile(
    details: RecipeWithDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: (() -> Unit)? = null,
) {
    val recipe = details.recipe
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLow, cornerRadius = 24.dp)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            RecipeImage(recipe.imageUri, placeholderIconSize = 28.dp, modifier = Modifier.fillMaxSize())
            if (onToggleFavorite != null) {
                FavouriteDot(
                    isFavourite = recipe.isFavorite,
                    onClick = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
        }
        Text(
            recipe.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        TotalTime(details)
    }
}

/** One line per recipe: thumbnail, title, and the time it takes. */
@Composable
fun ClayRecipeRow(
    details: RecipeWithDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: (() -> Unit)? = null,
) {
    val recipe = details.recipe
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLow, cornerRadius = 20.dp)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))) {
            RecipeImage(recipe.imageUri, placeholderIconSize = 22.dp, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                recipe.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TotalTime(details)
        }
        if (onToggleFavorite != null) {
            FavouriteDot(isFavourite = recipe.isFavorite, onClick = onToggleFavorite)
        }
    }
}

@Composable
private fun TotalTime(details: RecipeWithDetails) {
    val minutes = (details.recipe.prepTimeMinutes ?: 0) + (details.recipe.cookTimeMinutes ?: 0)
    if (minutes <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(
            Icons.Filled.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            stringResource(R.string.time_min, minutes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FavouriteDot(isFavourite: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(30.dp).clip(PillShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(R.string.detail_favourite),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
    }
}
