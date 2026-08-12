package com.delizioso.app.ui.screens.cook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.delizioso.app.DeliziosoApplication
import com.delizioso.app.data.ImageStore
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.data.local.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class CookViewModel(
    private val repository: RecipeRepository,
    private val preferences: UserPreferences,
    private val recipeId: Long,
) : ViewModel() {

    val details: StateFlow<RecipeWithDetails?> =
        repository.byId(recipeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 0-based index of the active step. */
    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    /** Step indices the cook has ticked off. */
    private val _completed = MutableStateFlow<Set<Int>>(emptySet())
    val completed: StateFlow<Set<Int>> = _completed.asStateFlow()

    /** Ingredient ids already gathered (cook mode drawer). */
    private val _gathered = MutableStateFlow<Set<Long>>(emptySet())
    val gathered: StateFlow<Set<Long>> = _gathered.asStateFlow()

    fun goTo(index: Int) {
        _currentStep.value = index.coerceAtLeast(0)
    }

    /** Tick the active step off and move on. */
    fun next() {
        val index = _currentStep.value
        _completed.update { it + index }
        _currentStep.update { it + 1 }
    }

    fun prev() = _currentStep.update { (it - 1).coerceAtLeast(0) }

    fun toggleCompleted(index: Int) {
        _completed.update { if (index in it) it - index else it + index }
    }

    fun toggleGathered(ingredientId: Long) {
        _gathered.update { if (ingredientId in it) it - ingredientId else it + ingredientId }
    }

    /** Put every ingredient the cook has NOT ticked off onto the shopping list. */
    fun addMissingToShoppingList() {
        val recipe = details.value ?: return
        val ingredients = recipe.ingredients
        val gathered = _gathered.value
        viewModelScope.launch {
            ingredients
                .filter { it.id !in gathered }
                .forEach { ingredient ->
                    preferences.addGroceryCustomItem(
                        line = ingredient.rawText
                            ?: listOfNotNull(ingredient.quantity, ingredient.unit, ingredient.name).joinToString(" "),
                        source = recipe.recipe.title,
                    )
                }
        }
    }

    /** Flag today's planned meals for this recipe as cooked. */
    /**
     * Adopt a photo just taken of the finished dish, dropping the old one.
     *
     * This is the moment the app has a real picture to show: imported reels rarely
     * yield a usable thumbnail, and the plate is right there.
     */
    fun setPhoto(context: android.content.Context, path: String) {
        viewModelScope.launch {
            val previous = repository.currentImage(recipeId)
            repository.setImage(recipeId, path)
            withContext(Dispatchers.IO) { ImageStore.deleteIfOwned(context, previous) }
        }
    }

    fun markCooked() {
        viewModelScope.launch { repository.markMealsCooked(recipeId, LocalDate.now().toEpochDay()) }
    }

    companion object {
        fun factory(recipeId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                CookViewModel(app.container.recipeRepository, app.container.preferences, recipeId)
            }
        }
    }
}
