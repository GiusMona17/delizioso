package com.delizioso.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.delizioso.app.DeliziosoApplication
import com.delizioso.app.data.import.ImportException
import com.delizioso.app.data.import.RawImport
import com.delizioso.app.data.import.RecipeImporterRegistry
import com.delizioso.app.data.import.RecipeSource
import com.delizioso.app.data.import.StructuredRecipe
import com.delizioso.app.data.local.UserPreferences
import com.delizioso.app.data.search.MealDbMapper
import com.delizioso.app.data.search.OnlineSearchResult
import com.delizioso.app.data.search.RecipeSearchProvider
import com.delizioso.app.data.search.TheMealDbClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** What the search screen is showing. */
sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Results(val results: List<OnlineSearchResult>) : SearchUiState
    /** Nothing matched. [ingredients] names the combination, so the user can undo one. */
    data class Empty(val ingredients: List<String>) : SearchUiState
    data class Failed(val message: String) : SearchUiState
}

class OnlineSearchViewModel(
    private val providers: List<RecipeSearchProvider>,
    private val preferences: UserPreferences,
    private val mealDbClient: TheMealDbClient,
    private val importerRegistry: RecipeImporterRegistry,
) : ViewModel() {

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /** Suggested ingredient names; empty when the list could not load. */
    private val _ingredientNames = MutableStateFlow<List<String>>(emptyList())
    val ingredientNames: StateFlow<List<String>> = _ingredientNames.asStateFlow()

    private val _chosenIngredients = MutableStateFlow<List<String>>(emptyList())
    val chosenIngredients: StateFlow<List<String>> = _chosenIngredients.asStateFlow()

    /** Full meals already fetched for TheMealDB name searches. */
    private var loadedMeals: Map<String, JsonObject> = emptyMap()

    private var searchJob: Job? = null
    private var lastSearchedName: String? = null

    init {
        viewModelScope.launch {
            _ingredientNames.value = runCatching { mealDbClient.ingredientNames() }.getOrDefault(emptyList())
        }
    }

    fun searchByName(query: String) {
        if (query.isBlank()) return
        val trimmed = query.trim()
        lastSearchedName = trimmed
        _chosenIngredients.value = emptyList()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = SearchUiState.Loading
            _state.value = try {
                val enabled = preferences.enabledSources.first()
                val activeProviders = providers.filter { it.source in enabled }
                if (activeProviders.isEmpty()) {
                    SearchUiState.Empty(emptyList())
                } else {
                    val searchJobs = activeProviders.map { provider ->
                        async {
                            runCatching { provider.searchByName(trimmed) }.getOrDefault(emptyList())
                        }
                    }
                    val allResults = searchJobs.awaitAll().flatten()
                    if (allResults.isEmpty()) {
                        SearchUiState.Empty(emptyList())
                    } else {
                        SearchUiState.Results(allResults)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportException) {
                SearchUiState.Failed(e.message.orEmpty())
            } catch (e: Exception) {
                SearchUiState.Failed(e.message.orEmpty())
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

    private fun searchByIngredients() {
        val chosen = _chosenIngredients.value
        if (chosen.isEmpty()) return
        loadedMeals = emptyMap()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = SearchUiState.Loading
            _state.value = try {
                val enabled = preferences.enabledSources.first()
                val activeProviders = providers.filter { it.source in enabled }
                if (activeProviders.isEmpty()) {
                    SearchUiState.Empty(chosen)
                } else {
                    val results = mutableListOf<OnlineSearchResult>()

                    // 1. TheMealDB intersection search
                    if (RecipeSource.THE_MEAL_DB in enabled) {
                        val perIngredient = chosen
                            .map { name -> async { mealDbClient.mealsWithIngredient(name) } }
                            .awaitAll()
                        val shared = TheMealDbClient.intersect(perIngredient)
                        results.addAll(
                            shared.map {
                                OnlineSearchResult(
                                    id = it.id,
                                    title = it.title,
                                    thumbnailUrl = it.thumbnailUrl,
                                    source = RecipeSource.THE_MEAL_DB
                                )
                            }
                        )
                    }

                    // 2. Web search providers (GialloZafferano, Cookist) with combined ingredients
                    val webProviders = activeProviders.filter { it.source != RecipeSource.THE_MEAL_DB }
                    if (webProviders.isNotEmpty()) {
                        val combined = chosen.joinToString(" ")
                        val webSearches = webProviders.map { provider ->
                            async {
                                runCatching { provider.searchByName(combined) }.getOrDefault(emptyList())
                            }
                        }
                        results.addAll(webSearches.awaitAll().flatten())
                    }

                    if (results.isEmpty()) SearchUiState.Empty(chosen) else SearchUiState.Results(results)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportException) {
                SearchUiState.Failed(e.message.orEmpty())
            } catch (e: Exception) {
                SearchUiState.Failed(e.message.orEmpty())
            }
        }
    }

    /**
     * Fetch the full recipe for a chosen result and hand it back to [onReady].
     *
     * Dispatches to [TheMealDbClient.lookup] for TheMealDB results, or
     * to [RecipeImporterRegistry.import] for portal links.
     */
    fun openResult(result: OnlineSearchResult, onReady: (StructuredRecipe, RawImport) -> Unit) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val previous = _state.value
            _state.value = SearchUiState.Loading
            try {
                if (result.source == RecipeSource.THE_MEAL_DB) {
                    val meal = loadedMeals[result.id] ?: mealDbClient.lookup(result.id)
                    if (meal == null) {
                        _state.value = SearchUiState.Failed("")
                        return@launch
                    }
                    _state.value = previous
                    onReady(MealDbMapper.toRecipe(meal), MealDbMapper.toRawImport(meal))
                } else {
                    val rawImport = importerRegistry.import(result.id)
                    val structured = (rawImport.content as? com.delizioso.app.data.import.ImportContent.Structured)?.recipe
                        ?: StructuredRecipe(
                            title = result.title,
                            imageUrl = result.thumbnailUrl,
                        )
                    _state.value = previous
                    onReady(structured, rawImport)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportException) {
                _state.value = SearchUiState.Failed(e.message.orEmpty())
            } catch (e: Exception) {
                _state.value = SearchUiState.Failed(e.message.orEmpty())
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                OnlineSearchViewModel(
                    providers = app.container.searchProviders,
                    preferences = app.container.preferences,
                    mealDbClient = app.container.theMealDbClient,
                    importerRegistry = app.container.importRegistry,
                )
            }
        }
    }
}
