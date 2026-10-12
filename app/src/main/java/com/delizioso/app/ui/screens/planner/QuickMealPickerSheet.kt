package com.delizioso.app.ui.screens.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.delizioso.app.R
import com.delizioso.app.data.local.MealSlot
import com.delizioso.app.data.local.QuickMealEntity
import com.delizioso.app.ui.components.ClayChip
import com.delizioso.app.ui.components.ClayFilterChip
import com.delizioso.app.ui.components.ClayRoundButton
import com.delizioso.app.ui.components.ClayTextField
import com.delizioso.app.ui.theme.clayCard

@Composable
fun QuickMealPickerSheet(
    slotTitle: String,
    targetSlot: String,
    quickMeals: List<QuickMealEntity>,
    onPick: (QuickMealEntity) -> Unit,
    onCreateNew: () -> Unit,
    onManageCatalog: () -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Pre-select target slot if there are items with it, else show All (null)
    var selectedSlotFilter by rememberSaveable {
        mutableStateOf<String?>(if (quickMeals.any { it.defaultSlot == targetSlot }) targetSlot else null)
    }

    val visibleMeals = remember(quickMeals, query, selectedSlotFilter) {
        quickMeals.filter { meal ->
            val matchesQuery = query.isBlank() || meal.name.contains(query, ignoreCase = true)
            val matchesSlot = selectedSlotFilter == null || meal.defaultSlot == selectedSlotFilter || meal.defaultSlot == null
            matchesQuery && matchesSlot
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.quick_meals_pick_title, slotTitle),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ClayRoundButton(
                icon = Icons.Filled.Tune,
                contentDescription = stringResource(R.string.quick_meals_manage_catalog),
                onClick = onManageCatalog,
                container = MaterialTheme.colorScheme.surfaceContainerLowest,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp),
            )
            ClayRoundButton(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.topbar_close),
                onClick = onClose,
                container = MaterialTheme.colorScheme.surfaceContainerLowest,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Filter chips: All, Breakfast, Lunch, Dinner, Snack
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                ClayFilterChip(
                    text = stringResource(R.string.quick_meals_filter_all),
                    selected = selectedSlotFilter == null,
                    onClick = { selectedSlotFilter = null },
                )
            }
            items(MealSlot.all) { slot ->
                ClayFilterChip(
                    text = stringResource(MealSlot.labelRes(slot)),
                    selected = selectedSlotFilter == slot,
                    onClick = { selectedSlotFilter = slot },
                )
            }
        }

        ClayTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.planner_search),
            leadingIcon = Icons.Filled.Search,
            cornerRadius = 24.dp,
            modifier = Modifier.fillMaxWidth(),
        )

        // "+ Create New" Quick Action Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clayCard(container = MaterialTheme.colorScheme.primaryContainer, cornerRadius = 20.dp)
                .clickable(onClick = onCreateNew)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                stringResource(R.string.quick_meals_create_new),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        if (visibleMeals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.quick_meals_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.height(320.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visibleMeals, key = { it.id }) { meal ->
                    QuickMealPickerRow(meal = meal, onClick = { onPick(meal) })
                }
            }
        }
    }
}

@Composable
private fun QuickMealPickerRow(
    meal: QuickMealEntity,
    onClick: () -> Unit,
) {
    val macroParts = buildList {
        meal.caloriesKcal?.let { add("${it.toInt()} kcal") }
        meal.proteinG?.let { add("P: ${it.toInt()}g") }
        meal.fatG?.let { add("G: ${it.toInt()}g") }
        meal.carbsG?.let { add("C: ${it.toInt()}g") }
    }
    val macroText = macroParts.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 20.dp)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                meal.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (macroText.isNotBlank()) {
                Text(
                    macroText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        meal.defaultSlot?.let { slot ->
            ClayChip(
                text = stringResource(MealSlot.labelRes(slot)),
                container = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
