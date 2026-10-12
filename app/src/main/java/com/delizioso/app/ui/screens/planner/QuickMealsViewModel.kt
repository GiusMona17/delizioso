package com.delizioso.app.ui.screens.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.delizioso.app.DeliziosoApplication
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.ai.QuickMealEstimator
import com.delizioso.app.data.local.QuickMealEntity
import com.delizioso.app.data.local.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QuickMealsViewModel(
    private val repository: RecipeRepository,
    private val preferences: UserPreferences,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSlot = MutableStateFlow<String?>(null)
    val selectedSlot: StateFlow<String?> = _selectedSlot.asStateFlow()

    val quickMealEstimator = QuickMealEstimator {
        preferences.aiConsentGiven.first()
    }

    val visibleMeals: StateFlow<List<QuickMealEntity>> = combine(
        repository.quickMeals,
        _searchQuery,
        _selectedSlot,
    ) { meals, query, slot ->
        meals.filter { meal ->
            val matchesQuery = query.isBlank() || meal.name.contains(query, ignoreCase = true)
            val matchesSlot = slot == null || meal.defaultSlot == slot || meal.defaultSlot == null
            matchesQuery && matchesSlot
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalCount: StateFlow<Int> = repository.quickMeals.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onSlotSelected(slot: String?) {
        _selectedSlot.value = slot
    }

    fun saveMeal(meal: QuickMealEntity) {
        viewModelScope.launch {
            if (meal.id == 0L) {
                repository.saveQuickMeal(meal)
            } else {
                repository.updateQuickMeal(meal)
            }
        }
    }

    fun deleteMeal(id: Long) {
        viewModelScope.launch {
            repository.deleteQuickMeal(id)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                QuickMealsViewModel(app.container.recipeRepository, app.container.preferences)
            }
        }
    }
}
