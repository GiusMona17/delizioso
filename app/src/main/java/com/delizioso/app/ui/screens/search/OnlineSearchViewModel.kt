package com.delizioso.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.delizioso.app.data.import.ImportException
import com.delizioso.app.data.import.RawImport
import com.delizioso.app.data.import.StructuredRecipe
import com.delizioso.app.data.search.MealDbMapper
import com.delizioso.app.data.search.TheMealDbClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** What the search screen is showing. */
sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Results(val results: List<TheMealDbClient.SearchResult>) : SearchUiState
    /** Nothing matched. [ingredients] names the combination, so the user can undo one. */
    data class Empty(val ingredients: List<String>) : SearchUiState
    data class Failed(val message: String) : SearchUiState
}

class OnlineSearchViewModel(
    private val client: TheMealDbClient,
) : ViewModel() {

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /** The 992 names offered as suggestions; empty when the list could not load. */
    private val _ingredientNames = MutableStateFlow<List<String>>(emptyList())
    val ingredientNames: StateFlow<List<String>> = _ingredientNames.asStateFlow()

    private val _chosenIngredients = MutableStateFlow<List<String>>(emptyList())
    val chosenIngredients: StateFlow<List<String>> = _chosenIngredients.asStateFlow()

    /**
     * Full meals already fetched, keyed by id.
     *
     * `search.php` returns complete meals, so a name search populates this and
     * [openResult] can hand one straight to the preview with no second request.
     * Ingredient results carry only id/name/thumbnail, so an ingredient search
     * clears this and [openResult] falls back to `lookup`.
     */
    private var loadedMeals: Map<String, JsonObject> = emptyMap()

    /**
     * The in-flight search or lookup, if any.
     *
     * All three entry points share this one job because they all write to the
     * same [_state]: whichever the user triggered last must win, so starting one
     * cancels whatever came before it, regardless of which kind it was.
     */
    private var searchJob: Job? = null

    init {
        // Failure is not fatal: the picker falls back to free text.
        viewModelScope.launch {
            _ingredientNames.value = runCatching { client.ingredientNames() }.getOrDefault(emptyList())
        }
    }

    private var lastSearchedName: String? = null

    fun searchByName(query: String) {
        if (query.isBlank()) return
        val trimmed = query.trim()
        lastSearchedName = trimmed
        _chosenIngredients.value = emptyList()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = SearchUiState.Loading
            _state.value = try {
                val meals = client.searchByName(trimmed)
                loadedMeals = meals.mapNotNull { meal ->
                    MealDbMapper.mealId(meal)?.let { it to meal }
                }.toMap()
                if (meals.isEmpty()) {
                    SearchUiState.Empty(emptyList())
                } else {
                    SearchUiState.Results(
                        meals.mapNotNull { meal ->
                            val id = MealDbMapper.mealId(meal) ?: return@mapNotNull null
                            val recipe = MealDbMapper.toRecipe(meal)
                            TheMealDbClient.SearchResult(id, recipe.title.orEmpty(), recipe.imageUrl)
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportException) {
                SearchUiState.Failed(e.message.orEmpty())
            } catch (e: Exception) {
                SearchUiState.Failed("")
            }
        }
    }

    fun retry() {
        val chosen = _chosenIngredients.value
        if (chosen.isNotEmpty()) {
            searchByIngredients()
        } else {
            lastSearchedName?.let { searchByName(it) }
        }
    }

    fun addIngredient(name: String) {
        if (name.isBlank() || name in _chosenIngredients.value) return
        _chosenIngredients.value = _chosenIngredients.value + name
        searchByIngredients()
    }

    fun removeIngredient(name: String) {
        _chosenIngredients.value = _chosenIngredients.value - name
        if (_chosenIngredients.value.isEmpty()) _state.value = SearchUiState.Idle else searchByIngredients()
    }

    /** One request per ingredient, in parallel, then the intersection. */
    private fun searchByIngredients() {
        val chosen = _chosenIngredients.value
        if (chosen.isEmpty()) return
        // Ingredient-filter results carry no recipe, so a stale name-search cache
        // must not be consulted for them.
        loadedMeals = emptyMap()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = SearchUiState.Loading
            _state.value = try {
                val perIngredient = chosen
                    .map { name -> async { client.mealsWithIngredient(name) } }
                    .awaitAll()
                val shared = TheMealDbClient.intersect(perIngredient)
                if (shared.isEmpty()) SearchUiState.Empty(chosen) else SearchUiState.Results(shared)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportException) {
                SearchUiState.Failed(e.message.orEmpty())
            } catch (e: Exception) {
                SearchUiState.Failed("")
            }
        }
    }

    /**
     * Fetch the full recipe for a chosen result and hand it back.
     *
     * A name-search result is already a complete meal in [loadedMeals], so it is
     * used as-is. Only an ingredient-search result — id/name/thumbnail only —
     * needs the extra `lookup` round-trip.
     */
    fun openResult(id: String, onReady: (StructuredRecipe, RawImport) -> Unit) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            // The results are put back before handing off, so returning from the
            // preview shows the list the user chose from instead of a spinner
            // nothing will ever resolve.
            val previous = _state.value
            _state.value = SearchUiState.Loading
            try {
                val meal = loadedMeals[id] ?: client.lookup(id)
                if (meal == null) {
                    _state.value = SearchUiState.Failed("")
                    return@launch
                }
                _state.value = previous
                onReady(MealDbMapper.toRecipe(meal), MealDbMapper.toRawImport(meal))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportException) {
                _state.value = SearchUiState.Failed(e.message.orEmpty())
            } catch (e: Exception) {
                _state.value = SearchUiState.Failed("")
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { OnlineSearchViewModel(TheMealDbClient()) }
        }
    }
}
