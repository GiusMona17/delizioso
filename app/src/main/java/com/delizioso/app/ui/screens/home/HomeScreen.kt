package com.delizioso.app.ui.screens.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delizioso.app.R
import com.delizioso.app.data.local.MealSlot
import com.delizioso.app.data.local.PlannedMealWithRecipe
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayChip
import com.delizioso.app.ui.components.ClayRecipeMiniCard
import com.delizioso.app.ui.components.ClaySectionHeader
import com.delizioso.app.ui.components.ClayTopBar
import com.delizioso.app.ui.components.RecipeImage
import com.delizioso.app.ui.theme.ClayShadow
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.Secondary
import com.delizioso.app.ui.theme.clayBevel
import com.delizioso.app.ui.theme.clayCard
import com.delizioso.app.ui.theme.clayOuter
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(
    onRecipeClick: (Long) -> Unit,
    onStartCook: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenImport: () -> Unit,
    onCreateRecipe: () -> Unit,
    onOpenGrocery: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        ClayTopBar(
            title = stringResource(R.string.app_name),
            onMenu = onOpenGrocery,
            menuIcon = Icons.Filled.ShoppingCart,
            menuDescription = stringResource(R.string.home_action_grocery),
            onProfile = onProfileClick,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 1. Greeting & Date Banner
            GreetingHeader(state = state)

            // 2. Week Overview Strip
            if (state.weekOverview.isNotEmpty()) {
                WeekOverviewStrip(
                    days = state.weekOverview,
                    onOpenPlanner = onOpenPlanner,
                )
            }

            // 3. Upcoming / Next Meal Hero
            UpcomingMealHero(
                upcoming = state.upcomingMeal,
                onRecipeClick = onRecipeClick,
                onStartCook = onStartCook,
                onOpenPlanner = onOpenPlanner,
            )

            // 4. Daily Nutrition Widget
            if (state.dailyNutrition.nutrients.caloriesKcal > 0 || state.nutritionGoals != null) {
                DailyNutritionCard(
                    recap = state.dailyNutrition,
                    goals = state.nutritionGoals,
                    onOpenPlanner = onOpenPlanner,
                )
            }

            // 5. Quick Actions
            QuickActionsSection(
                onCreateRecipe = onCreateRecipe,
                onOpenImport = onOpenImport,
                onOpenPlanner = onOpenPlanner,
                onOpenGrocery = onOpenGrocery,
            )

            // 5. Daily Inspiration
            state.dailyInspiration?.let { inspiration ->
                DailyInspirationCard(
                    details = inspiration,
                    onClick = { onRecipeClick(inspiration.recipe.id) },
                )
            }

            // 6. Recent Recipes
            if (state.recentRecipes.isNotEmpty()) {
                RecentRecipesSection(
                    recipes = state.recentRecipes,
                    onRecipeClick = onRecipeClick,
                    onViewAll = onOpenLibrary,
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun GreetingHeader(state: HomeUiState) {
    val greetingText = when (state.greeting) {
        TimeOfDayGreeting.MORNING -> stringResource(R.string.home_greeting_morning)
        TimeOfDayGreeting.AFTERNOON -> stringResource(R.string.home_greeting_afternoon)
        TimeOfDayGreeting.EVENING -> stringResource(R.string.home_greeting_evening)
    }

    val formattedDate = try {
        state.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    } catch (_: Exception) {
        state.date.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "$greetingText 👋",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = formattedDate,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeekOverviewStrip(
    days: List<DayOverview>,
    onOpenPlanner: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainer, cornerRadius = 24.dp)
            .clickable(onClick = onOpenPlanner)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.home_week_overview),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.home_view_all),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            days.forEach { day ->
                val dayLetter = day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                val isToday = day.isToday

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clip(PillShape)
                        .background(
                            if (isToday) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                        .clayBevel(
                            PillShape,
                            light = ClayShadow.highlight,
                            dark = if (isToday) ClayShadow.innerAccent else ClayShadow.insetDark
                        )
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = dayLetter,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${day.date.dayOfMonth}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                        color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.height(6.dp),
                    ) {
                        if (day.hasPlanned) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(PillShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        if (day.hasCooked) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(PillShape)
                                    .background(Secondary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingMealHero(
    upcoming: UpcomingMealState,
    onRecipeClick: (Long) -> Unit,
    onStartCook: (Long) -> Unit,
    onOpenPlanner: () -> Unit,
) {
    val slotLabel = stringResource(MealSlot.labelRes(upcoming.slot))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.home_next_meal_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ClayChip(slotLabel)
        }

        if (upcoming.isPlanned && upcoming.mainMeal?.recipe != null) {
            val mainMeal = upcoming.mainMeal
            val recipe = mainMeal.recipe

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayCard(container = MaterialTheme.colorScheme.surfaceContainer, cornerRadius = 32.dp)
                    .clickable { onRecipeClick(recipe.id) }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(24.dp)),
                ) {
                    RecipeImage(recipe.imageUri, modifier = Modifier.fillMaxSize())
                    val minutes = (recipe.prepTimeMinutes ?: 0) + (recipe.cookTimeMinutes ?: 0)
                    if (minutes > 0) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .clip(PillShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Text(stringResource(R.string.time_min, minutes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = recipe.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    recipe.caloriesKcal?.let { kcal ->
                        Text(
                            text = stringResource(R.string.macro_kcal, kcal.toInt()),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Sides attached
                if (upcoming.sideMeals.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        upcoming.sideMeals.forEach { side ->
                            side.recipe?.let { sideRecipe ->
                                ClayChip(
                                    text = "+ ${sideRecipe.title}",
                                    container = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }

                ClayButton(
                    text = stringResource(R.string.home_cook_now),
                    icon = Icons.Filled.PlayArrow,
                    onClick = { onStartCook(recipe.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            // Empty planned meal hero
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLow, cornerRadius = 32.dp)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Filled.RestaurantMenu,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    text = stringResource(R.string.home_no_meal_title, slotLabel),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.home_no_meal_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ClayButton(
                    text = stringResource(R.string.home_plan_btn, slotLabel),
                    icon = Icons.Filled.CalendarMonth,
                    onClick = onOpenPlanner,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun QuickActionsSection(
    onCreateRecipe: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenGrocery: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ClaySectionHeader(title = stringResource(R.string.home_quick_actions))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickActionTile(
                title = stringResource(R.string.home_action_create),
                icon = Icons.Filled.AddCircleOutline,
                onClick = onCreateRecipe,
                modifier = Modifier.weight(1f),
            )
            QuickActionTile(
                title = stringResource(R.string.home_action_import),
                icon = Icons.Filled.Download,
                onClick = onOpenImport,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickActionTile(
                title = stringResource(R.string.home_action_planner),
                icon = Icons.Filled.CalendarMonth,
                onClick = onOpenPlanner,
                modifier = Modifier.weight(1f),
            )
            QuickActionTile(
                title = stringResource(R.string.home_action_grocery),
                icon = Icons.Filled.ShoppingCart,
                onClick = onOpenGrocery,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainer, cornerRadius = 24.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clayBevel(PillShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DailyInspirationCard(
    details: RecipeWithDetails,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                stringResource(R.string.home_daily_inspiration),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLow, cornerRadius = 24.dp)
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RecipeImage(
                details.recipe.imageUri,
                placeholderIconSize = 24.dp,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_inspiration_sub),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = details.recipe.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val minutes = (details.recipe.prepTimeMinutes ?: 0) + (details.recipe.cookTimeMinutes ?: 0)
                if (minutes > 0) {
                    Text(
                        text = stringResource(R.string.time_min, minutes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentRecipesSection(
    recipes: List<RecipeWithDetails>,
    onRecipeClick: (Long) -> Unit,
    onViewAll: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.home_recent_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.home_view_all),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(PillShape)
                    .clickable(onClick = onViewAll)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(recipes, key = { it.recipe.id }) { details ->
                ClayRecipeMiniCard(
                    details = details,
                    onClick = { onRecipeClick(details.recipe.id) },
                )
            }
        }
    }
}

@Composable
private fun DailyNutritionCard(
    recap: com.delizioso.app.data.nutrition.DayNutritionRecap,
    goals: com.delizioso.app.data.local.NutritionGoals?,
    onOpenPlanner: () -> Unit,
) {
    val nutrients = recap.nutrients
    val dist = recap.distribution
    val targetKcal = goals?.targetCaloriesKcal

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainer, cornerRadius = 24.dp)
            .clickable(onClick = onOpenPlanner)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.RestaurantMenu,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(R.string.nutrition_daily_recap),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (targetKcal != null && nutrients.caloriesKcal >= targetKcal) {
                ClayChip(
                    text = stringResource(R.string.nutrition_target_reached),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        // Calories & Goal progress
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${nutrients.caloriesKcal}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }

            if (targetKcal != null) {
                Text(
                    stringResource(R.string.nutrition_goal_of, targetKcal),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Progress bar if goal is set
        if (targetKcal != null && targetKcal > 0) {
            val progress = (nutrients.caloriesKcal.toFloat() / targetKcal).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(8.dp)
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        // Macro breakdown bar
        if (dist.proteinPct > 0 || dist.carbsPct > 0 || dist.fatPct > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(PillShape),
            ) {
                if (dist.proteinPct > 0) {
                    Box(
                        modifier = Modifier
                            .weight(dist.proteinPct.toFloat().coerceAtLeast(0.01f))
                            .fillMaxSize()
                            .background(Color(0xFF2E7D32))
                    )
                }
                if (dist.carbsPct > 0) {
                    Box(
                        modifier = Modifier
                            .weight(dist.carbsPct.toFloat().coerceAtLeast(0.01f))
                            .fillMaxSize()
                            .background(Color(0xFFF57C00))
                    )
                }
                if (dist.fatPct > 0) {
                    Box(
                        modifier = Modifier
                            .weight(dist.fatPct.toFloat().coerceAtLeast(0.01f))
                            .fillMaxSize()
                            .background(Color(0xFFC2185B))
                    )
                }
            }
        }

        // Macro chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MacroMiniBadge(
                label = stringResource(R.string.macro_protein_label),
                amountG = nutrients.proteinG,
                pct = dist.proteinPct,
                color = Color(0xFF2E7D32),
            )
            MacroMiniBadge(
                label = stringResource(R.string.macro_carbs_label),
                amountG = nutrients.carbsG,
                pct = dist.carbsPct,
                color = Color(0xFFF57C00),
            )
            MacroMiniBadge(
                label = stringResource(R.string.macro_fat_label),
                amountG = nutrients.fatG,
                pct = dist.fatPct,
                color = Color(0xFFC2185B),
            )
        }
    }
}

@Composable
private fun MacroMiniBadge(
    label: String,
    amountG: Int,
    pct: Int,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(PillShape)
                .background(color)
        )
        Column {
            Text(
                "${amountG}g",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "$label ($pct%)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

