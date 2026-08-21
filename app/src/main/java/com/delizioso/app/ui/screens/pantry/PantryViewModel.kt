package com.delizioso.app.ui.screens.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.delizioso.app.DeliziosoApplication
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.local.PantryItemEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PantryViewModel(
    private val repository: RecipeRepository,
) : ViewModel() {

    val pantryItems: StateFlow<List<PantryItemEntity>> =
        repository.pantryItems.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    fun toggleStock(id: Long, inStock: Boolean) {
        viewModelScope.launch {
            repository.setPantryItemInStock(id, inStock)
        }
    }

    fun addItem(
        name: String,
        category: String = "Pantry",
        quantity: String? = null,
        expiresAtEpochDay: Long? = null,
    ) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.savePantryItem(
                PantryItemEntity(
                    name = trimmed,
                    category = category,
                    quantity = quantity?.trim()?.ifBlank { null },
                    expiresAtEpochDay = expiresAtEpochDay,
                    inStock = true,
                )
            )
        }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            repository.deletePantryItem(id)
        }
    }

    fun addStaple(name: String, category: String) {
        viewModelScope.launch {
            repository.savePantryItem(
                PantryItemEntity(
                    name = name,
                    category = category,
                    inStock = true,
                )
            )
        }
    }

    fun clearOutOfStock() {
        viewModelScope.launch {
            repository.clearOutOfStockPantryItems()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                PantryViewModel(app.container.recipeRepository)
            }
        }
    }
}
