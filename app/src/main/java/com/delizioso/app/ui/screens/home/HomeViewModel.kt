package com.delizioso.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.delizioso.app.DeliziosoApplication
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.local.MealSlot
import com.delizioso.app.data.local.NutritionGoals
import com.delizioso.app.data.local.PantryItemEntity
import com.delizioso.app.data.local.PlannedMealWithRecipe
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.data.local.UserPreferences
import com.delizioso.app.data.nutrition.DayNutritionRecap
import com.delizioso.app.data.nutrition.NutritionAggregator
import com.delizioso.app.data.pantry.PantryMatcher
import com.delizioso.app.data.pantry.RecipePantryMatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

enum class TimeOfDayGreeting {
    MORNING,
    AFTERNOON,
    EVENING,
}

data class UpcomingMealState(
    val slot: String,
    val isPlanned: Boolean,
    val mainMeal: PlannedMealWithRecipe? = null,
    val sideMeals: List<PlannedMealWithRecipe> = emptyList(),
)

data class DayOverview(
    val date: LocalDate,
    val isToday: Boolean,
    val hasPlanned: Boolean,
    val hasCooked: Boolean,
)

data class HomeUiState(
    val greeting: TimeOfDayGreeting = TimeOfDayGreeting.MORNING,
    val date: LocalDate = LocalDate.now(),
    val upcomingMeal: UpcomingMealState = UpcomingMealState(slot = MealSlot.DINNER, isPlanned = false),
    val dailyNutrition: DayNutritionRecap = DayNutritionRecap(date = LocalDate.now()),
    val nutritionGoals: NutritionGoals? = null,
    val dailyInspiration: RecipeWithDetails? = null,
    val pantryMatches: List<RecipePantryMatch> = emptyList(),
    val inStockPantryCount: Int = 0,
    val recentRecipes: List<RecipeWithDetails> = emptyList(),
    val weekOverview: List<DayOverview> = emptyList(),
    val totalRecipeCount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: RecipeRepository,
    private val preferences: UserPreferences,
) : ViewModel() {

    private val today = LocalDate.now()
    private val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    val uiState: StateFlow<HomeUiState> = combine(
        repository.allWithDetails,
        repository.mealsBetween(monday.toEpochDay(), monday.plusDays(6).toEpochDay()),
        preferences.nutritionGoals,
        repository.pantryItems,
    ) { allRecipes, weekMeals, goals, pantryItems ->
        val hour = LocalTime.now().hour

        val greeting = when {
            hour in 5..11 -> TimeOfDayGreeting.MORNING
            hour in 12..17 -> TimeOfDayGreeting.AFTERNOON
            else -> TimeOfDayGreeting.EVENING
        }

        val targetSlot = when {
            hour in 5..10 -> MealSlot.BREAKFAST
            hour in 11..15 -> MealSlot.LUNCH
            hour in 16..22 -> MealSlot.DINNER
            else -> MealSlot.BREAKFAST
        }

        val todayMeals = weekMeals.filter { it.meal.dateEpochDay == today.toEpochDay() }
        val slotMeals = todayMeals.filter { it.meal.slot == targetSlot }
            .ifEmpty { todayMeals }

        val mainMeal = slotMeals.firstOrNull { !it.meal.isSide } ?: slotMeals.firstOrNull()
        val sideMeals = if (mainMeal != null) slotMeals.filter { it.meal.id != mainMeal.meal.id && it.meal.isSide } else emptyList()

        val upcomingState = if (mainMeal != null) {
            UpcomingMealState(
                slot = mainMeal.meal.slot,
                isPlanned = true,
                mainMeal = mainMeal,
                sideMeals = sideMeals,
            )
        } else {
            UpcomingMealState(
                slot = targetSlot,
                isPlanned = false,
            )
        }

        val inStock = pantryItems.filter { it.inStock }
        val pantryMatches = if (inStock.isNotEmpty() && allRecipes.isNotEmpty()) {
            PantryMatcher.rank(allRecipes, inStock)
                .filter { it.matchPercentage >= 40 }
                .take(6)
        } else emptyList()

        val inspiration = if (allRecipes.isNotEmpty()) {
            val seedIndex = ((today.toEpochDay().hashCode() and 0x7FFFFFFF) % allRecipes.size)
            allRecipes[seedIndex]
        } else null

        val recents = allRecipes.take(6)

        val days = (0..6).map { offset ->
            val day = monday.plusDays(offset.toLong())
            val dayMeals = weekMeals.filter { it.meal.dateEpochDay == day.toEpochDay() }
            DayOverview(
                date = day,
                isToday = day == today,
                hasPlanned = dayMeals.any { !it.meal.cooked },
                hasCooked = dayMeals.any { it.meal.cooked },
            )
        }

        val dailyNutrition = NutritionAggregator.computeDayRecap(today, weekMeals, allRecipes)

        HomeUiState(
            greeting = greeting,
            date = today,
            upcomingMeal = upcomingState,
            dailyNutrition = dailyNutrition,
            nutritionGoals = goals,
            dailyInspiration = inspiration,
            pantryMatches = pantryMatches,
            inStockPantryCount = inStock.size,
            recentRecipes = recents,
            weekOverview = days,
            totalRecipeCount = allRecipes.size,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(),
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                HomeViewModel(app.container.recipeRepository, app.container.preferences)
            }
        }
    }
}
