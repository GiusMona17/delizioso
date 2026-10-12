package com.delizioso.app.ui.screens.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delizioso.app.R
import com.delizioso.app.data.local.MealSlot
import com.delizioso.app.data.local.QuickMealEntity
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayChip
import com.delizioso.app.ui.components.ClayFilterChip
import com.delizioso.app.ui.components.ClayRoundButton
import com.delizioso.app.ui.components.ClayTextField
import com.delizioso.app.ui.theme.ClayShadow
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.clayBevel
import com.delizioso.app.ui.theme.clayCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMealsScreen(
    onBack: () -> Unit,
    viewModel: QuickMealsViewModel = viewModel(factory = QuickMealsViewModel.Factory),
) {
    val meals by viewModel.visibleMeals.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedSlot by viewModel.selectedSlot.collectAsStateWithLifecycle()

    var editingMeal by remember { mutableStateOf<QuickMealEntity?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var mealToDelete by remember { mutableStateOf<QuickMealEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clayBevel(RoundedCornerShape(0.dp), light = ClayShadow.highlight, dark = ClayShadow.accentSoft)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClayRoundButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                container = MaterialTheme.colorScheme.surfaceContainerLowest,
            )
            Text(
                stringResource(R.string.quick_meals_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            ClayRoundButton(
                icon = Icons.Filled.Add,
                contentDescription = stringResource(R.string.quick_meals_create_new),
                onClick = { showCreateSheet = true },
                container = MaterialTheme.colorScheme.primaryContainer,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            ClayTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = stringResource(R.string.planner_search),
                leadingIcon = Icons.Filled.Search,
                cornerRadius = 24.dp,
                modifier = Modifier.fillMaxWidth(),
            )

            // Slot filter chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    ClayFilterChip(
                        text = stringResource(R.string.quick_meals_filter_all),
                        selected = selectedSlot == null,
                        onClick = { viewModel.onSlotSelected(null) },
                    )
                }
                items(MealSlot.all) { slot ->
                    ClayFilterChip(
                        text = stringResource(MealSlot.labelRes(slot)),
                        selected = selectedSlot == slot,
                        onClick = { viewModel.onSlotSelected(slot) },
                    )
                }
            }

            if (meals.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.quick_meals_empty),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.quick_meals_empty_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                    ClayButton(
                        text = stringResource(R.string.quick_meals_create_new),
                        icon = Icons.Filled.Add,
                        onClick = { showCreateSheet = true },
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(meals, key = { it.id }) { meal ->
                        QuickMealCatalogCard(
                            meal = meal,
                            onEdit = { editingMeal = meal },
                            onDelete = { mealToDelete = meal },
                        )
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCreateSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            QuickMealSheet(
                slotTitle = stringResource(R.string.quick_meals_create_new),
                initialDefaultSlot = selectedSlot,
                estimator = viewModel.quickMealEstimator,
                onSave = { name, slot, kcal, p, f, c ->
                    viewModel.saveMeal(
                        QuickMealEntity(
                            name = name,
                            defaultSlot = slot,
                            caloriesKcal = kcal,
                            proteinG = p,
                            fatG = f,
                            carbsG = c,
                        )
                    )
                    showCreateSheet = false
                },
                onClose = { showCreateSheet = false },
            )
        }
    }

    editingMeal?.let { meal ->
        ModalBottomSheet(
            onDismissRequest = { editingMeal = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            QuickMealSheet(
                slotTitle = meal.name,
                initialMeal = meal,
                estimator = viewModel.quickMealEstimator,
                onSave = { name, slot, kcal, p, f, c ->
                    viewModel.saveMeal(
                        meal.copy(
                            name = name,
                            defaultSlot = slot,
                            caloriesKcal = kcal,
                            proteinG = p,
                            fatG = f,
                            carbsG = c,
                        )
                    )
                    editingMeal = null
                },
                onClose = { editingMeal = null },
            )
        }
    }

    mealToDelete?.let { meal ->
        AlertDialog(
            onDismissRequest = { mealToDelete = null },
            title = { Text(stringResource(R.string.quick_meals_delete_confirm_title)) },
            text = { Text(stringResource(R.string.quick_meals_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMeal(meal.id)
                    mealToDelete = null
                }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { mealToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun QuickMealCatalogCard(
    meal: QuickMealEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

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
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 22.dp)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(26.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                meal.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (macroText.isNotBlank()) {
                Text(macroText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        meal.defaultSlot?.let { slot ->
            ClayChip(
                text = stringResource(MealSlot.labelRes(slot)),
                container = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        Box {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(PillShape)
                    .clickable { menuOpen = true }
                    .padding(6.dp),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit)) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}
