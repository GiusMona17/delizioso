package com.delizioso.app.ui.screens.import

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.delizioso.app.DeliziosoApplication
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.ai.AiUnavailableException
import com.delizioso.app.data.ai.NanoInference
import com.delizioso.app.data.ai.NanoStructurer
import com.delizioso.app.data.import.ImportContent
import com.delizioso.app.data.import.ImportException
import com.delizioso.app.data.import.RawImport
import com.delizioso.app.data.import.RecipeImporterRegistry
import com.delizioso.app.data.import.StructuredRecipe
import com.delizioso.app.data.local.Platform
import com.delizioso.app.data.local.RecipeEntity
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.data.local.SourceEntity
import com.delizioso.app.data.local.StepEntity
import com.delizioso.app.data.local.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Fetching : ImportUiState
    data object Structuring : ImportUiState
    data class Ready(val recipe: StructuredRecipe, val raw: RawImport) : ImportUiState
    data class Error(val message: String, val retryable: Boolean) : ImportUiState
    data object AiConsentNeeded : ImportUiState
}

class ImportViewModel(
    private val registry: RecipeImporterRegistry,
    private val structurer: NanoStructurer,
    private val repository: RecipeRepository,
    private val preferences: UserPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    /** Recipes that came from a link or a scan — the "Recent Imports" rail. */
    val recentImports: StateFlow<List<RecipeWithDetails>> = repository.allWithDetails
        .map { all -> all.filter { details -> details.source?.platform?.let { it != Platform.MANUAL } == true }.take(10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var lastRaw: RawImport? = null
    private var lastUrl: String? = null

    fun importLink(url: String) {
        if (url.isBlank()) return
        lastUrl = url.trim()
        viewModelScope.launch {
            _state.value = ImportUiState.Fetching
            try {
                val raw = registry.import(lastUrl!!)
                lastRaw = raw
                structure(raw)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportException) {
                _state.value = ImportUiState.Error(e.message ?: "Import failed", e.retryable)
            } catch (e: Exception) {
                _state.value = ImportUiState.Error(e.message ?: "Unexpected error", false)
            }
        }
    }

    private suspend fun structure(raw: RawImport) {
        when (val content = raw.content) {
            is ImportContent.Structured -> {
                _state.value = ImportUiState.Ready(content.recipe, raw)
            }
            is ImportContent.RawText -> {
                when (structurer.availability()) {
                    NanoInference.Availability.UNAVAILABLE -> _state.value = ImportUiState.AiConsentNeeded
                    else -> {
                        _state.value = ImportUiState.Structuring
                        try {
                            structurer.ensureDownloaded()
                            val recipe = structurer.structure(content.text)
                            _state.value = ImportUiState.Ready(
                                recipe = recipe.copy(title = recipe.title.orEmpty().ifBlank { content.title.orEmpty() }),
                                raw = raw,
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: AiUnavailableException) {
                            _state.value = ImportUiState.Error(e.message ?: "AI structuring failed", false)
                        } catch (e: Exception) {
                            _state.value = ImportUiState.Error(e.message ?: "AI structuring failed", false)
                        }
                    }
                }
            }
        }
    }

    /** User accepted on-device AI terms — retry structuring. */
    fun grantConsent() {
        viewModelScope.launch {
            preferences.setAiConsent(true)
            lastRaw?.let { structure(it) }
        }
    }

    fun retry() {
        lastUrl?.let { importLink(it) }
    }

    /** Persist the (edited) recipe; returns the new recipe id. */
    suspend fun save(recipe: StructuredRecipe, raw: RawImport, tags: List<String> = emptyList()): Long {
        val details = toDetails(recipe, raw)
        val id = repository.save(details, tags)
        // Back to a blank slate, or returning to the Import tab would bounce
        // straight back into the preview of the recipe we just saved.
        _state.value = ImportUiState.Idle
        lastRaw = null
        lastUrl = null
        return id
    }

    private fun toDetails(recipe: StructuredRecipe, raw: RawImport): RecipeWithDetails {
        val source = SourceEntity(
            recipeId = 0,
            platform = raw.platform,
            url = raw.url,
            author = raw.author,
            rawText = (raw.content as? ImportContent.RawText)?.text,
        )
        val entity = RecipeEntity(
            title = recipe.title.orEmpty(),
            description = recipe.description,
            servings = recipe.servings,
            prepTimeMinutes = recipe.prepTimeMinutes,
            cookTimeMinutes = recipe.cookTimeMinutes,
            imageUri = recipe.imageUrl,
        )
        val ingredients = recipe.ingredients.mapIndexed { i, ing -> ing.copy(recipeId = 0, position = i) }
        val steps = recipe.steps.mapIndexed { i, s -> StepEntity(recipeId = 0, position = i + 1, text = s) }
        return RecipeWithDetails(recipe = entity, ingredients = ingredients, steps = steps, source = source)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                ImportViewModel(
                    registry = app.container.importRegistry,
                    structurer = app.container.nanoStructurer,
                    repository = app.container.recipeRepository,
                    preferences = app.container.preferences,
                )
            }
        }
    }
}
