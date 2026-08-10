package com.delizioso.app.ui.screens.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.delizioso.app.DeliziosoApplication
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.local.MealSlot
import com.delizioso.app.data.local.PlannedMealEntity
import com.delizioso.app.data.local.PlannedMealWithRecipe
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.data.local.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalCoroutinesApi::class)
class PlannerViewModel(
    private val repository: RecipeRepository,
    private val preferences: UserPreferences,
) : ViewModel() {

    /** All recipes for the add-meal picker. */
    val recipes: StateFlow<List<RecipeWithDetails>> =
        repository.allWithDetails.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /** Monday of the week containing the selected day. */
    val weekStart: StateFlow<LocalDate> = _selectedDate
        .map { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
        )

    /** Meals in the visible week (drives the day strip and the meal sections). */
    val weekMeals: StateFlow<List<PlannedMealWithRecipe>> = weekStart
        .flatMapLatest { start -> repository.mealsBetween(start.toEpochDay(), start.plusDays(6).toEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Meals in the month containing the selected day (for the calendar view). */
    val monthMeals: StateFlow<List<PlannedMealWithRecipe>> = _selectedDate
        .map { it.withDayOfMonth(1) }
        .flatMapLatest { first -> repository.mealsBetween(first.toEpochDay(), first.plusMonths(1).minusDays(1).toEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun shiftWeek(delta: Int) {
        _selectedDate.value = _selectedDate.value.plusWeeks(delta.toLong())
    }

    fun shiftMonth(delta: Int) {
        _selectedDate.value = _selectedDate.value.plusMonths(delta.toLong())
    }

    fun addMeal(recipeId: Long, epochDay: Long, slot: String = MealSlot.DINNER) {
        viewModelScope.launch {
            repository.addMeal(
                PlannedMealEntity(
                    recipeId = recipeId,
                    dateEpochDay = epochDay,
                    slot = slot,
                    servings = preferences.defaultServings.first(),
                )
            )
        }
    }

    fun removeMeal(id: Long) {
        viewModelScope.launch { repository.removeMeal(id) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                PlannerViewModel(app.container.recipeRepository, app.container.preferences)
            }
        }
    }
}
