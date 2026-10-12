package com.delizioso.app.ui.screens.planner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.delizioso.app.R
import com.delizioso.app.ui.components.ClayRoundButton
import com.delizioso.app.ui.theme.clayCard

@Composable
fun AddMealChooserSheet(
    slotTitle: String,
    onPickRecipe: () -> Unit,
    onPickSavedQuickMeal: () -> Unit,
    onCreateNewQuickMeal: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.planner_choose_add_title, slotTitle),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            ClayRoundButton(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.topbar_close),
                onClick = onClose,
                container = MaterialTheme.colorScheme.surfaceContainerLowest,
            )
        }

        // 1. Recipe from library
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 22.dp)
                .clickable(onClick = onPickRecipe)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.planner_choose_recipe), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(stringResource(R.string.planner_choose_recipe_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // 2. Saved Quick Meal
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 22.dp)
                .clickable(onClick = onPickSavedQuickMeal)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text(stringResource(R.string.quick_meals_choose_saved), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(stringResource(R.string.quick_meals_choose_saved_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // 3. Create New Quick Meal
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 22.dp)
                .clickable(onClick = onCreateNewQuickMeal)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Text(stringResource(R.string.quick_meals_create_new), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(stringResource(R.string.quick_meals_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
