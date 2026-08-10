package com.delizioso.app.ui.screens.grocery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.delizioso.app.DeliziosoApplication
import com.delizioso.app.data.GroceryAggregator
import com.delizioso.app.data.GroceryCategories
import com.delizioso.app.data.GroceryItem
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.local.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Custom (hand-typed) grocery lines carry this pseudo-recipe label. */
const val CUSTOM_ITEM_SOURCE = "Added by you"

@OptIn(ExperimentalCoroutinesApi::class)
class GroceryViewModel(
    private val repository: RecipeRepository,
    private val preferences: UserPreferences,
) : ViewModel() {

    private val rangeDays = MutableStateFlow(7)

    /** Consolidated list for the next `rangeDays` days plus the user's own items. */
    val items: StateFlow<List<GroceryItem>> = rangeDays
        .flatMapLatest { days ->
            val from = LocalDate.now()
            val to = from.plusDays(days.toLong() - 1)
            combine(
                repository.mealsBetween(from.toEpochDay(), to.toEpochDay()),
                repository.allWithDetails,
                preferences.groceryCustomItems,
            ) { meals, all, custom ->
                val plannedIds = meals.map { it.meal.recipeId }.toSet()
                val planned = all.filter { it.recipe.id in plannedIds }
                GroceryAggregator.aggregate(planned) + custom.sorted().map { line ->
                    GroceryItem(
                        name = line,
                        line = line,
                        isMerged = false,
                        recipeTitles = listOf(CUSTOM_ITEM_SOURCE),
                        category = GroceryCategories.of(line),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Lines the user has ticked off (persisted). */
    val checked: StateFlow<Set<String>> =
        preferences.groceryChecked.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val customItems: StateFlow<Set<String>> =
        preferences.groceryCustomItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleChecked(line: String) = viewModelScope.launch { preferences.toggleGroceryChecked(line) }

    fun clearChecked() = viewModelScope.launch { preferences.clearGroceryChecked() }

    fun addCustomItem(line: String) = viewModelScope.launch { preferences.addGroceryCustomItem(line) }

    fun removeCustomItem(line: String) = viewModelScope.launch { preferences.removeGroceryCustomItem(line) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                GroceryViewModel(app.container.recipeRepository, app.container.preferences)
            }
        }
    }
}
