package com.delizioso.app.ui.screens.import

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.content.Context
import android.net.Uri
import com.delizioso.app.DeliziosoApplication
import com.delizioso.app.data.ImageStore
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.ai.AiUnavailableException
import com.delizioso.app.data.ai.RecipeRefiner
import com.delizioso.app.data.ai.RefineState
import com.delizioso.app.data.ai.NanoInference
import com.delizioso.app.data.ai.NanoStructurer
import com.delizioso.app.data.import.ImportContent
import com.delizioso.app.data.import.ImportException
import com.delizioso.app.data.import.LoginWall
import com.delizioso.app.data.import.PastedRecipeParser
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.delizioso.app.R

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Fetching : ImportUiState
    data object Structuring : ImportUiState
    /**
     * [structuringFailed] means the AI could not read the caption and this is a
     * bare title+photo shell — the screen must say so rather than silently
     * presenting empty ingredient and step sections.
     */
    data class Ready(
        val recipe: StructuredRecipe,
        val raw: RawImport,
        val structuringFailed: Boolean = false,
    ) : ImportUiState
    data class Error(val message: String, val retryable: Boolean) : ImportUiState
    data object AiConsentNeeded : ImportUiState
}

class ImportViewModel(
    private val registry: RecipeImporterRegistry,
    private val structurer: NanoStructurer,
    private val repository: RecipeRepository,
    private val preferences: UserPreferences,
    private val appContext: Context,
    private val refiner: RecipeRefiner,
) : ViewModel() {

    val refine: StateFlow<RefineState> = refiner.state

    fun convertAndTranslate(recipe: StructuredRecipe, onRefined: (StructuredRecipe) -> Unit) {
        refiner.clearError()
        refiner.refine(viewModelScope, recipe, onRefined)
    }

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    /** Recipes that came from a link or a scan — the "Recent Imports" rail. */
    val recentImports: StateFlow<List<RecipeWithDetails>> = repository.allWithDetails
        .map { all -> all.filter { details -> details.source?.platform?.let { it != Platform.MANUAL } == true }.take(10) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var lastRaw: RawImport? = null
    private var lastUrl: String? = null

    /** A photo the user picked in the preview; overrides the source's thumbnail. */
    private val _pickedPhoto = MutableStateFlow<String?>(null)
    val pickedPhoto: StateFlow<String?> = _pickedPhoto.asStateFlow()

    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { ImageStore.saveToInternal(appContext, uri) } }
                .onSuccess { _pickedPhoto.value = it }
        }
    }

    fun clearPickedPhoto() {
        _pickedPhoto.value = null
    }

    /**
     * Import a recipe the user pasted as plain text.
     *
     * Deterministic and instant: [PastedRecipeParser] splits the block into
     * sections and the preview screen is where anything it misplaced gets fixed.
     */
    fun importText(text: String) {
        if (text.isBlank()) return
        val recipe = PastedRecipeParser.parse(text)
        val raw = RawImport(
            platform = Platform.MANUAL,
            url = null,
            author = null,
            content = ImportContent.RawText(text = text),
        )
        lastRaw = raw
        lastUrl = null
        _state.value = ImportUiState.Ready(
            recipe = recipe,
            raw = raw,
            structuringFailed = recipe.ingredients.isEmpty() || recipe.steps.isEmpty(),
        )
    }

    /**
     * Show a recipe found through online search in the preview.
     *
     * The search screen owns finding it; from here on it is an import like any
     * other, which is why this only sets the state the preview already reads.
     */
    fun importSearchResult(recipe: StructuredRecipe, raw: RawImport) {
        lastRaw = raw
        lastUrl = raw.url
        _state.value = ImportUiState.Ready(recipe = recipe, raw = raw)
    }

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
                _state.value = ImportUiState.Error(e.message ?: appContext.getString(R.string.import_failed), e.retryable)
            } catch (e: Exception) {
                _state.value = ImportUiState.Error(e.message ?: appContext.getString(R.string.import_unexpected), false)
            }
        }
    }

    private suspend fun structure(raw: RawImport) {
        when (val content = raw.content) {
            is ImportContent.Structured -> {
                _state.value = ImportUiState.Ready(content.recipe, raw)
            }
            is ImportContent.RawText -> {
                val isWall = LoginWall.matches(content.text)
                // Behind a login/consent wall the caption is wall text, but the page's
                // og:title often carries the FULL recipe (name + ingredients + method).
                val textToStructure = if (isWall) {
                    content.title?.takeIf { !LoginWall.matches(it) }
                } else {
                    content.text
                }
                if (textToStructure.isNullOrBlank()) {
                    // Wall with no usable recipe text: keep title + cover thumbnail as
                    // an editable shell so the reel still imports with its thumbnail.
                    fallbackToShell(content, raw)
                    return
                }
                when (structurer.availability()) {
                    NanoInference.Availability.UNAVAILABLE -> _state.value = ImportUiState.AiConsentNeeded
                    else -> {
                        _state.value = ImportUiState.Structuring
                        try {
                            structurer.ensureDownloaded()
                            val recipe = structurer.structure(textToStructure)
                            _state.value = ImportUiState.Ready(
                                recipe = recipe.copy(
                                    title = recipe.title.orEmpty().ifBlank {
                                        content.text.ifBlank { content.title.orEmpty() }
                                    }
                                ),
                                raw = raw,
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: AiUnavailableException) {
                            // Structuring failed (transient model output, or the caption
                            // was just a name): still land title + thumbnail as a shell,
                            // but tell the user so an empty form is never a mystery.
                            fallbackToShell(content, raw, e.message)
                        } catch (e: Exception) {
                            fallbackToShell(content, raw, e.message)
                        }
                    }
                }
            }
        }
    }

    /** Structuring failed / wall: keep title + cover thumbnail as an editable shell. */
    private fun fallbackToShell(
        content: ImportContent.RawText,
        raw: RawImport,
        reason: String? = null,
    ) {
        if (reason != null) {
            android.util.Log.w("Import", "structuring failed, falling back to shell: $reason")
        }
        _state.value = ImportUiState.Ready(
            recipe = StructuredRecipe(
                // Prefer the already-cleaned caption (recipe name); the raw og:title may
                // contain the whole recipe ("Name | INGREDIENTI: … | PROCEDIMENTO: …").
                title = shellTitle(content.text, content.title),
                imageUrl = raw.thumbnailUrl,
            ),
            raw = raw,
            structuringFailed = true,
        )
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
        // Prefer the user's own photo; otherwise cache the source thumbnail locally so
        // it outlives the CDN link.
        val photo = _pickedPhoto.value
            ?: raw.thumbnailUrl?.let { ImageStore.downloadToInternal(appContext, it) }
            ?: recipe.imageUrl?.let { ImageStore.downloadToInternal(appContext, it) }
        val details = toDetails(recipe, raw, photo)
        val id = repository.save(details, tags)
        // Back to a blank slate, or returning to the Import tab would bounce
        // straight back into the preview of the recipe we just saved.
        _state.value = ImportUiState.Idle
        lastRaw = null
        lastUrl = null
        _pickedPhoto.value = null
        return id
    }

    private fun toDetails(recipe: StructuredRecipe, raw: RawImport, photoPath: String?): RecipeWithDetails {
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
            imageUri = photoPath,
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
                    appContext = app,
                    refiner = RecipeRefiner(app.container.recipeTranslator),
                )
            }
        }
    }
}

/**
 * Title for the editable shell: the opening of the cleaned caption.
 *
 * A reel has no title, only a caption, so its first line is the best the app can
 * offer — a starting point the user edits, not a claim. Multi-line captions carry
 * the whole recipe ("Name\n\nMarinade\n- 700g..."), which must not become the
 * title, and captions that open with promo prose are cut at the first sentence so
 * the field holds a name rather than a paragraph.
 */
internal fun shellTitle(text: String, title: String?): String {
    val fromText = firstSentence(text)
    if (fromText.isNotBlank()) return fromText
    return firstSentence(title.orEmpty())
}

/** First non-blank line, truncated at its first sentence break. */
private fun firstSentence(text: String): String {
    val line = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    if (line.isEmpty()) return ""
    // Only cut where a sentence really ends and more follows: "Ragu 2.0" and
    // "Dr. Pepper cake" have to survive intact.
    val end = SENTENCE_END.find(line)?.range?.first
    val head = if (end != null && end >= MIN_TITLE_LENGTH) line.substring(0, end + 1) else line
    return head.trim().take(120)
}

/** A terminator followed by whitespace and a capital: a real sentence boundary. */
private val SENTENCE_END = Regex("""[.!?](?=\s+\p{Lu})""")

/**
 * A break this early in the line is an abbreviation, not the end of a name:
 * "Torta Dr. Pepper" must survive whole, while "Benvenuti in UDON LAB. Il nuovo
 * format..." is a promo sentence worth cutting.
 */
private const val MIN_TITLE_LENGTH = 12
