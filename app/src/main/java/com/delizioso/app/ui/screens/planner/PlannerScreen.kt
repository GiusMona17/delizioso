package com.delizioso.app.ui.screens.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delizioso.app.data.local.MealSlot
import com.delizioso.app.data.local.PlannedMealWithRecipe
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.ui.components.ClayAddPanel
import com.delizioso.app.ui.components.ClayChip
import com.delizioso.app.ui.components.ClayGroupLabel
import com.delizioso.app.ui.components.ClayRecipeRow
import com.delizioso.app.ui.components.ClayRoundButton
import com.delizioso.app.ui.components.ClayTextField
import com.delizioso.app.ui.components.ClayTopBar
import com.delizioso.app.ui.components.RecipeImage
import com.delizioso.app.ui.components.mealSlotIcon
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.Secondary
import com.delizioso.app.ui.theme.clayCard
import com.delizioso.app.ui.theme.ClayShadow
import com.delizioso.app.ui.theme.clayBevel
import com.delizioso.app.ui.theme.clayOuter
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.delizioso.app.R

private enum class PlannerView { WEEK, MONTH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    onRecipeClick: (Long) -> Unit,
    onOpenGrocery: () -> Unit,
    onProfileClick: () -> Unit,
    viewModel: PlannerViewModel = viewModel(factory = PlannerViewModel.Factory),
) {
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val weekStart by viewModel.weekStart.collectAsStateWithLifecycle()
    val weekMeals by viewModel.weekMeals.collectAsStateWithLifecycle()
    val monthMeals by viewModel.monthMeals.collectAsStateWithLifecycle()
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()

    var view by rememberSaveable { mutableStateOf(PlannerView.WEEK) }
    var pickerRequest by remember { mutableStateOf<PickerRequest?>(null) }

    Column(Modifier.fillMaxSize()) {
        ClayTopBar(
            title = stringResource(R.string.planner_title),
            onMenu = onOpenGrocery,
            menuIcon = Icons.Filled.ShoppingCart,
            menuDescription = stringResource(R.string.planner_menu_grocery),
            onProfile = onProfileClick,
        )

        if (view == PlannerView.WEEK) {
            DayStrip(
                weekStart = weekStart,
                selected = selectedDate,
                meals = weekMeals,
                onSelect = viewModel::selectDate,
                onShiftWeek = viewModel::shiftWeek,
            )
            ViewToggle(
                label = stringResource(R.string.planner_switch_monthly),
                icon = Icons.Filled.CalendarViewMonth,
                onClick = { view = PlannerView.MONTH },
            )
            val dayMeals = weekMeals.filter { it.meal.dateEpochDay == selectedDate.toEpochDay() }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                MealSlot.all.forEach { slot ->
                    item(key = "label-$slot") {
                        ClayGroupLabel(stringResource(MealSlot.labelRes(slot)), icon = mealSlotIcon(slot), modifier = Modifier.padding(top = 12.dp))
                    }
                    val slotMeals = dayMeals.filter { it.meal.slot == slot }
                    val mains = slotMeals.filter { !it.meal.isSide }
                    val sides = slotMeals.filter { it.meal.isSide }

                    if (slotMeals.isEmpty()) {
                        item(key = "empty-$slot") {
                            ClayAddPanel(
                                text = stringResource(R.string.planner_add_recipe, stringResource(MealSlot.labelRes(slot)).lowercase()),
                                onClick = { pickerRequest = PickerRequest(slot, isSide = false) },
                                accent = if (slot == MealSlot.SNACK) Secondary else MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        items(mains, key = { it.meal.id }) { planned ->
                            PlannedMealRow(
                                planned = planned,
                                recipes = recipes,
                                onOpen = onRecipeClick,
                                onRemove = { viewModel.removeMeal(planned.meal.id) },
                            )
                        }
                        items(sides, key = { it.meal.id }) { planned ->
                            PlannedMealRow(
                                planned = planned,
                                recipes = recipes,
                                onOpen = onRecipeClick,
                                onRemove = { viewModel.removeMeal(planned.meal.id) },
                            )
                        }
                        item(key = "actions-$slot") {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Text(
                                    stringResource(R.string.planner_add_more),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(PillShape)
                                        .clickable { pickerRequest = PickerRequest(slot, isSide = false) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                                Text(
                                    stringResource(R.string.planner_add_side),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier
                                        .clip(PillShape)
                                        .clickable { pickerRequest = PickerRequest(slot, isSide = true) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        } else {
            MonthCalendar(
                anchor = selectedDate,
                meals = monthMeals,
                onShiftMonth = viewModel::shiftMonth,
                onSwitchToWeekly = { view = PlannerView.WEEK },
                onDayClick = {
                    viewModel.selectDate(it)
                    view = PlannerView.WEEK
                },
            )
        }
    }

    pickerRequest?.let { req ->
        ModalBottomSheet(
            onDismissRequest = { pickerRequest = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            RecipePickerSheet(
                title = stringResource(
                    R.string.planner_add_title,
                    stringResource(MealSlot.labelRes(req.slot)),
                    selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                ),
                initialIsSide = req.isSide,
                recipes = recipes,
                onPick = { recipeId, isSide ->
                    viewModel.addMeal(recipeId, selectedDate.toEpochDay(), req.slot, isSide = isSide)
                    pickerRequest = null
                },
                onClose = { pickerRequest = null },
            )
        }
    }
}

private data class PickerRequest(val slot: String, val isSide: Boolean)

// ---- Week strip ------------------------------------------------------------

@Composable
private fun DayStrip(
    weekStart: LocalDate,
    selected: LocalDate,
    meals: List<PlannedMealWithRecipe>,
    onSelect: (LocalDate) -> Unit,
    onShiftWeek: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.planner_prev_week)) { onShiftWeek(-1) }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            items((0..6).toList(), key = { it }) { offset ->
                val day = weekStart.plusDays(offset.toLong())
                val dayMeals = meals.filter { it.meal.dateEpochDay == day.toEpochDay() }
                DayPill(
                    day = day,
                    selected = day == selected,
                    planned = dayMeals.any { !it.meal.cooked },
                    cooked = dayMeals.any { it.meal.cooked },
                    onClick = { onSelect(day) },
                )
            }
        }
        ArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.planner_next_week)) { onShiftWeek(1) }
    }
}

@Composable
private fun DayPill(
    day: LocalDate,
    selected: Boolean,
    planned: Boolean,
    cooked: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clayOuter(shape = PillShape, elevation = if (selected) 14.dp else 8.dp)
            .clip(PillShape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .clayBevel(PillShape, light = ClayShadow.highlight, dark = if (selected) ClayShadow.innerAccent else ClayShadow.insetDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${day.dayOfMonth}",
            style = MaterialTheme.typography.headlineMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(8.dp)) {
            if (planned) Dot(MaterialTheme.colorScheme.primary)
            if (cooked) Dot(Secondary)
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(6.dp).clip(PillShape).background(color))
}

@Composable
private fun ArrowButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    ClayRoundButton(
        icon = icon,
        contentDescription = description,
        onClick = onClick,
        container = MaterialTheme.colorScheme.surfaceContainerLowest,
    )
}

@Composable
private fun ViewToggle(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .clayOuter(shape = PillShape, elevation = 10.dp)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clayBevel(PillShape, light = ClayShadow.highlight, dark = ClayShadow.innerAccent)
                .clickable(onClick = onClick)
                .padding(horizontal = 22.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

// ---- Meal row --------------------------------------------------------------

@Composable
private fun PlannedMealRow(
    planned: PlannedMealWithRecipe,
    recipes: List<RecipeWithDetails>,
    onOpen: (Long) -> Unit,
    onRemove: () -> Unit,
) {
    val recipe = planned.recipe ?: return
    val details = recipes.firstOrNull { it.recipe.id == recipe.id } ?: RecipeWithDetails(recipe = recipe)
    var menuOpen by remember { mutableStateOf(false) }

    ClayRecipeRow(
        details = details,
        onClick = { onOpen(recipe.id) },
        extraBadge = if (planned.meal.isSide) {
            {
                ClayChip(
                    text = stringResource(R.string.planner_side_badge),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        } else null,
        trailing = {
            Box {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.planner_meal_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(PillShape)
                        .clickable { menuOpen = true }
                        .padding(8.dp),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.planner_remove)) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        },
    )
}

// ---- Month calendar --------------------------------------------------------

@Composable
private fun MonthCalendar(
    anchor: LocalDate,
    meals: List<PlannedMealWithRecipe>,
    onShiftMonth: (Int) -> Unit,
    onSwitchToWeekly: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
) {
    val first = anchor.withDayOfMonth(1)
    val mealsByDay = meals.groupBy { it.meal.dateEpochDay }
    // Sunday-first grid, matching the mockup's S M T W T F S header.
    val leadingBlanks = first.dayOfWeek.value % 7
    val cells = List(leadingBlanks) { 0 } + (1..first.lengthOfMonth()).toList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clayCard(container = MaterialTheme.colorScheme.surfaceContainer, cornerRadius = 28.dp)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                ArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.planner_prev_month)) { onShiftMonth(-1) }
                Text(
                    "${first.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${first.year}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                ArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.planner_next_month)) { onShiftMonth(1) }
            }
            ViewToggle(label = stringResource(R.string.planner_switch_weekly), icon = Icons.Filled.CalendarViewWeek, onClick = onSwitchToWeekly)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clayCard(container = MaterialTheme.colorScheme.surfaceContainer, cornerRadius = 28.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val weeks = cells.chunked(7)
            weeks.forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    week.forEach { dayNumber ->
                        if (dayNumber == 0) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val day = first.withDayOfMonth(dayNumber)
                            val dayMeals = mealsByDay[day.toEpochDay()].orEmpty()
                            MonthDayCell(
                                day = day,
                                selected = day == anchor,
                                planned = dayMeals.any { !it.meal.cooked },
                                cooked = dayMeals.any { it.meal.cooked },
                                onClick = { onDayClick(day) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (week.size < 7) {
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            ) {
                LegendEntry(MaterialTheme.colorScheme.primary, stringResource(R.string.planner_legend_planned))
                LegendEntry(Secondary, stringResource(R.string.planner_legend_cooked))
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    day: LocalDate,
    selected: Boolean,
    planned: Boolean,
    cooked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .then(
                if (selected) {
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clayBevel(RoundedCornerShape(14.dp), light = ClayShadow.highlight, dark = ClayShadow.innerAccent)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "${day.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(8.dp)) {
                if (planned) Dot(MaterialTheme.colorScheme.primary)
                if (cooked) Dot(Secondary)
            }
        }
    }
}

@Composable
private fun LegendEntry(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(12.dp).clip(PillShape).background(color))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- Recipe picker ---------------------------------------------------------

@Composable
private fun RecipePickerSheet(
    title: String,
    initialIsSide: Boolean,
    recipes: List<RecipeWithDetails>,
    onPick: (Long, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filterTab by rememberSaveable { mutableStateOf(if (initialIsSide) 2 else 0) }
    val sideCategories = remember {
        setOf("Sauce", "Bread", "Side", "Dressing & Marinade", "Base & Broth", "Preserve")
    }

    val visible = remember(recipes, query, filterTab) {
        recipes.filter { details ->
            val matchesQuery = query.isBlank() || details.recipe.title.contains(query, ignoreCase = true)
            if (!matchesQuery) return@filter false

            val hasSideTag = details.tags.any { it.name in sideCategories }
            when (filterTab) {
                1 -> !hasSideTag // Mains
                2 -> hasSideTag // Sides & Sauces
                else -> true // All
            }
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
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ClayRoundButton(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.topbar_close),
                onClick = onClose,
                container = MaterialTheme.colorScheme.surfaceContainerLowest,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val tabs = listOf(
                0 to stringResource(R.string.planner_filter_all),
                1 to stringResource(R.string.planner_filter_mains),
                2 to stringResource(R.string.planner_filter_sides),
            )
            tabs.forEach { (index, label) ->
                val selected = filterTab == index
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)
                        .clayBevel(PillShape)
                        .clickable { filterTab = index }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
        if (visible.isEmpty()) {
            Text(
                stringResource(R.string.planner_no_recipes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.height(340.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible, key = { it.recipe.id }) { details ->
                    val isSideRecipe = details.tags.any { it.name in sideCategories }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 22.dp)
                            .clickable {
                                onPick(details.recipe.id, initialIsSide || filterTab == 2 || isSideRecipe)
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RecipeImage(
                            details.recipe.imageUri,
                            placeholderIconSize = 20.dp,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)),
                        )
                        Text(
                            details.recipe.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 14.dp),
                        )
                        if (isSideRecipe) {
                            ClayChip(
                                text = stringResource(R.string.planner_side_badge),
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        } else {
                            val minutes = (details.recipe.prepTimeMinutes ?: 0) + (details.recipe.cookTimeMinutes ?: 0)
                            if (minutes > 0) ClayChip(stringResource(R.string.time_min, minutes))
                        }
                    }
                }
            }
        }
    }
}
