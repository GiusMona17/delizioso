package com.delizioso.app.ui.screens.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Schedule
import com.delizioso.app.data.hasFixedIngredients
import com.delizioso.app.data.ai.RecipeSwapEngine
import com.delizioso.app.data.ai.SwapPreset
import com.delizioso.app.export.SocialCardGenerator
import com.delizioso.app.export.RecipePdfExporter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.delizioso.app.DeliziosoApplication
import com.delizioso.app.data.Categories
import com.delizioso.app.data.ImageStore
import com.delizioso.app.data.Quantities
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.toStructuredRecipe
import com.delizioso.app.data.ai.ChatMessage
import com.delizioso.app.data.ai.RecipeChat
import com.delizioso.app.data.export.RecipeExport
import com.delizioso.app.data.export.RecipePrompt
import com.delizioso.app.data.import.ImportContent
import com.delizioso.app.data.import.SourceRefresher
import com.delizioso.app.data.nutrition.MacroCalculator
import com.delizioso.app.data.ai.NanoInference
import com.delizioso.app.data.import.StructuredRecipe
import com.delizioso.app.data.local.IngredientEntity
import com.delizioso.app.data.local.Platform
import com.delizioso.app.data.local.PlannedMealEntity
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.data.local.UserPreferences
import com.delizioso.app.ui.components.AddToPlannerSheet
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayCheckbox
import com.delizioso.app.ui.components.ClayChip
import com.delizioso.app.ui.components.ClayRoundButton
import com.delizioso.app.ui.components.ClaySectionHeader
import com.delizioso.app.ui.components.ClaySegmentedTabs
import com.delizioso.app.ui.components.ClayTagChip
import com.delizioso.app.ui.components.PhotoPickerArea
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.clayCard
import com.delizioso.app.ui.theme.clayBevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.delizioso.app.R

/** Progress of re-reading a recipe from the link it was imported from. */
sealed interface RefreshState {
    data object Idle : RefreshState
    data object Running : RefreshState
    data object Done : RefreshState
    data class Failed(val message: String) : RefreshState
}

/** A conversation about one recipe; [streaming] is the answer being typed out. */
data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val streaming: String? = null,
    val error: String? = null,
    /** AICore is fetching Gemini Nano — minutes on first run, not seconds. */
    val preparingModel: Boolean = false,
    /** Which model is answering, so the sheet can say so rather than guess. */
    val engine: RecipeChat.Engine = RecipeChat.Engine.NANO,
) {
    val busy: Boolean get() = streaming != null
}

class RecipeDetailViewModel(
    private val resources: android.content.res.Resources,
    private val repository: RecipeRepository,
    private val chatModel: RecipeChat,
    private val preferences: UserPreferences,
    private val refresher: SourceRefresher,
    private val recipeId: Long,
) : ViewModel() {

    /** Progress of "fetch this recipe from its source again". */
    private val _refresh = MutableStateFlow<RefreshState>(RefreshState.Idle)
    val refresh: StateFlow<RefreshState> = _refresh.asStateFlow()

    fun clearRefreshState() { _refresh.value = RefreshState.Idle }

    /**
     * Re-reads the source and replaces the recipe with what it says now.
     *
     * Destructive by design — that is the point, it undoes a bad parse or a bad
     * edit — so the screen asks first. The photo is the repository's to keep.
     */
    fun refreshFromSource() {
        val d = details.value ?: return
        val url = d.source?.url.orEmpty()
        viewModelScope.launch {
            _refresh.value = RefreshState.Running
            try {
                val fresh = refresher.refetch(url)
                val preserved = fresh.recipe.copy(
                    servings = fresh.recipe.servings ?: d.recipe.servings,
                    prepTimeMinutes = fresh.recipe.prepTimeMinutes ?: d.recipe.prepTimeMinutes,
                    cookTimeMinutes = fresh.recipe.cookTimeMinutes ?: d.recipe.cookTimeMinutes,
                )
                repository.update(
                    recipeId,
                    preserved,
                    fresh.recipe.categories.ifEmpty { d.tags.map { it.name } },
                )
                fresh.rawText?.let { repository.updateSourceText(recipeId, it) }
                _refresh.value = RefreshState.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _refresh.value = RefreshState.Failed(
                    e.message ?: resources.getString(R.string.detail_refresh_failed)
                )
            }
        }
    }

    val details: StateFlow<RecipeWithDetails?> =
        repository.byId(recipeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _chat = MutableStateFlow(ChatState(engine = chatModel.active()))
    val chat: StateFlow<ChatState> = _chat.asStateFlow()

    fun toggleFavorite(current: Boolean) {
        viewModelScope.launch { repository.setFavorite(recipeId, !current) }
    }

    fun addToPlanner(epochDay: Long, slot: String, servings: Int) {
        viewModelScope.launch {
            repository.addMeal(
                PlannedMealEntity(recipeId = recipeId, dateEpochDay = epochDay, slot = slot, servings = servings)
            )
        }
    }

    /** Copy every ingredient (scaled to the chosen servings) onto the shopping list. */
    fun addToShoppingList(factor: Double) {
        val d = details.value ?: return
        viewModelScope.launch {
            d.ingredients.sortedBy { it.position }.forEach { ingredient ->
                val line = listOfNotNull(
                    Quantities.scale(ingredient.quantity, factor),
                    ingredient.unit,
                    ingredient.name,
                ).joinToString(" ").trim()
                preferences.addGroceryCustomItem(line.ifBlank { ingredient.name }, source = d.recipe.title)
            }
        }
    }

    /** Replace the recipe photo with one from the gallery, dropping the old file. */
    fun onPhotoPicked(context: android.content.Context, uri: Uri) {
        viewModelScope.launch {
            val saved = runCatching {
                withContext(Dispatchers.IO) { ImageStore.saveToInternal(context, uri) }
            }.getOrNull() ?: return@launch
            val previous = repository.currentImage(recipeId)
            repository.setImage(recipeId, saved)
            withContext(Dispatchers.IO) { ImageStore.deleteIfOwned(context, previous) }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(recipeId)
            onDeleted()
        }
    }

    /** Ask a free-form question about this recipe; the answer streams in. */
    fun ask(question: String) {
        val d = details.value ?: return
        val trimmed = question.trim()
        if (trimmed.isEmpty() || _chat.value.busy) return
        val history = _chat.value.messages
        _chat.value = ChatState(
            messages = history + ChatMessage(ChatMessage.Role.USER, trimmed),
            streaming = "",
            engine = chatModel.active(),
        )
        viewModelScope.launch {
            try {
                // First run downloads the model; say so rather than showing a
                // "Thinking…" spinner for several minutes.
                if (chatModel.needsDownload()) {
                    _chat.update { it.copy(preparingModel = true) }
                }
                var answer = ""
                chatModel.ask(d.toStructuredRecipe(), history, trimmed).collect { soFar ->
                    answer = soFar
                    _chat.update { it.copy(streaming = soFar, preparingModel = false) }
                }
                _chat.update {
                    it.copy(
                        messages = it.messages + ChatMessage(ChatMessage.Role.ASSISTANT, answer),
                        streaming = null,
                        preparingModel = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _chat.update {
                    it.copy(
                        streaming = null,
                        preparingModel = false,
                        error = resources.getString(R.string.detail_ai_no_answer),
                    )
                }
            }
        }
    }

    val inStockPantry = repository.inStockPantryItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun askSwap(preset: SwapPreset) {
        val prompt = RecipeSwapEngine.buildPrompt(preset, inStockPantry.value)
        ask(prompt)
    }

    fun duplicateRecipe(onDuplicated: (Long) -> Unit) {
        val d = details.value ?: return
        viewModelScope.launch {
            val newTitle = "${d.recipe.title} (Variation)"
            val newRecipe = d.recipe.copy(
                id = 0L,
                title = newTitle,
                isFavorite = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            val newIngredients = d.ingredients.map { it.copy(id = 0L, recipeId = 0L) }
            val newSteps = d.steps.map { it.copy(id = 0L, recipeId = 0L) }
            val newDetails = RecipeWithDetails(
                recipe = newRecipe,
                ingredients = newIngredients,
                steps = newSteps,
                source = d.source?.copy(recipeId = 0L),
                tags = d.tags,
            )
            val newId = repository.save(newDetails, d.tags.map { it.name })
            onDuplicated(newId)
        }
    }

    fun clearChatError() = _chat.update { it.copy(error = null) }

    companion object {
        fun factory(recipeId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                RecipeDetailViewModel(
                    resources = app.resources,
                    repository = app.container.recipeRepository,
                    chatModel = app.container.recipeChat,
                    refresher = app.container.sourceRefresher,
                    preferences = app.container.preferences,
                    recipeId = recipeId,
                )
            }
        }
    }
}

private const val TAB_INGREDIENTS = 0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    recipeId: Long,
    onBack: () -> Unit,
    onStartCooking: () -> Unit,
    onEdit: () -> Unit,
    viewModel: RecipeDetailViewModel = viewModel(
        key = "recipe-detail-$recipeId",
        factory = RecipeDetailViewModel.factory(recipeId),
    ),
) {
    val details by viewModel.details.collectAsStateWithLifecycle()
    val chat by viewModel.chat.collectAsStateWithLifecycle()
    val refreshState by viewModel.refresh.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(TAB_INGREDIENTS) }
    var servings by rememberSaveable { mutableStateOf(0) }
    var showPlanner by remember { mutableStateOf(false) }
    var addedToList by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showRefreshConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onPhotoPicked(context, it) }
    }
    val ticked = remember { mutableStateOf(setOf<Long>()) }

    val d = details
    // Seed the stepper once the recipe has actually loaded — keying on the id means
    // a null first frame can't lock the default in before the real servings arrive.
    LaunchedEffect(d?.recipe?.id) {
        d?.let { servings = it.recipe.servings ?: 2 }
    }

    if (d == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val baseServings = d.recipe.servings ?: servings
    val factor = if (baseServings > 0 && servings > 0) servings.toDouble() / baseServings else 1.0
    val macros = remember(d) { MacroCalculator.of(d) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.fillMaxWidth()) {
                PhotoPickerArea(
                    imageUri = d.recipe.imageUri,
                    onPick = { photoPicker.launch("image/*") },
                    // A photo-less recipe doesn't deserve 320dp of empty hero.
                    height = if (d.recipe.imageUri.isNullOrBlank()) 200.dp else 320.dp,
                    cornerRadius = 0.dp,
                    // Clear the header card that overlaps the bottom of the hero.
                    badgeBottomPadding = 56.dp,
                )
                // The actions ride on the hero, so they scroll away with it.
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ClayRoundButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.topbar_back),
                        onClick = onBack,
                        container = MaterialTheme.colorScheme.surfaceContainerLowest,
                    )
                    Spacer(Modifier.weight(1f))
                    ClayRoundButton(
                        icon = Icons.Filled.ContentCopy,
                        contentDescription = stringResource(R.string.recipe_duplicate_action),
                        onClick = {
                            viewModel.duplicateRecipe {
                                Toast.makeText(context, context.getString(R.string.recipe_duplicated_toast), Toast.LENGTH_SHORT).show()
                            }
                        },
                        container = MaterialTheme.colorScheme.surfaceContainerLowest,
                    )
                    Spacer(Modifier.width(12.dp))
                    ClayRoundButton(
                        icon = Icons.Filled.CalendarMonth,
                        contentDescription = stringResource(R.string.detail_add_planner),
                        onClick = { showPlanner = true },
                        container = MaterialTheme.colorScheme.surfaceContainerLowest,
                    )
                    Spacer(Modifier.width(12.dp))
                    ClayRoundButton(
                        icon = if (d.recipe.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(R.string.detail_favourite),
                        onClick = { viewModel.toggleFavorite(d.recipe.isFavorite) },
                        container = MaterialTheme.colorScheme.surfaceContainerLowest,
                        tint = if (d.recipe.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .offset(y = (-32).dp)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                HeaderCard(
                    details = d,
                    servings = servings,
                    onServingsChange = { servings = it.coerceIn(1, 99) },
                )
                ClaySegmentedTabs(
                    options = listOf(stringResource(R.string.form_ingredients_title), stringResource(R.string.form_instructions_title)),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                )
                if (tab == TAB_INGREDIENTS) {
                    if (d.hasFixedIngredients()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clayCard(container = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f), cornerRadius = 18.dp)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = stringResource(R.string.detail_fixed_ingredients_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            ClayButton(
                                text = stringResource(R.string.detail_fixed_ingredients_action),
                                icon = Icons.Filled.AutoAwesome,
                                onClick = onEdit,
                                container = MaterialTheme.colorScheme.surfaceContainerLowest,
                                contentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    IngredientList(
                        ingredients = d.ingredients.sortedBy { it.position },
                        factor = factor,
                        ticked = ticked.value,
                        onToggle = { id ->
                            ticked.value = if (id in ticked.value) ticked.value - id else ticked.value + id
                        },
                    )
                    ClayButton(
                        text = if (addedToList) stringResource(R.string.detail_added_list) else stringResource(R.string.detail_add_list),
                        icon = if (addedToList) Icons.Filled.Check else Icons.Filled.ShoppingCart,
                        onClick = {
                            viewModel.addToShoppingList(factor)
                            addedToList = true
                        },
                        container = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    StepList(d, factor)
                }
                MacrosPanel(macros = macros, onOpenChat = { showChat = true })
                SourceSection(
                    details = d,
                    refreshState = refreshState,
                    onRefresh = { showRefreshConfirm = true },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClayButton(
                        text = stringResource(R.string.detail_edit),
                        icon = Icons.Filled.Edit,
                        onClick = onEdit,
                        container = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    ClayButton(
                        text = stringResource(R.string.detail_export),
                        icon = Icons.Filled.IosShare,
                        onClick = { showExport = true },
                        container = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    stringResource(R.string.detail_delete),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clip(PillShape)
                        .clickable { viewModel.delete(onBack) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                // Room for the floating "Start Cooking" pill.
                Spacer(Modifier.height(72.dp))
            }
        }

        ClayButton(
            text = stringResource(R.string.detail_start_cooking),
            icon = Icons.Filled.PlayArrow,
            onClick = onStartCooking,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
        )
    }

    if (showChat) {
        ModalBottomSheet(
            onDismissRequest = { showChat = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            RecipeChatSheet(
                recipeTitle = d.recipe.title,
                state = chat,
                onAsk = viewModel::ask,
                onAskSwap = viewModel::askSwap,
                onDismissError = viewModel::clearChatError,
            )
        }
    }

    if (showRefreshConfirm) {
        // A refresh throws away whatever is on screen — including edits the user
        // made by hand — so it asks before it does, not after.
        AlertDialog(
            onDismissRequest = { showRefreshConfirm = false },
            title = { Text(stringResource(R.string.detail_refresh_title)) },
            text = { Text(stringResource(R.string.detail_refresh_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRefreshConfirm = false
                    viewModel.refreshFromSource()
                }) { Text(stringResource(R.string.detail_refresh)) }
            },
            dismissButton = {
                TextButton(onClick = { showRefreshConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showExport) {
        ModalBottomSheet(
            onDismissRequest = { showExport = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            ExportSheet(
                details = d,
                markdown = RecipeExport.toMarkdown(d, macros),
                json = RecipeExport.toJson(d, macros),
                caption = d.source?.rawText?.takeIf { it.isNotBlank() },
                onDone = { showExport = false },
            )
        }
    }

    if (showPlanner) {
        ModalBottomSheet(
            onDismissRequest = { showPlanner = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            AddToPlannerSheet(
                details = d,
                onAdd = { epochDay, slot ->
                    viewModel.addToPlanner(epochDay, slot, servings)
                    showPlanner = false
                },
                onClose = { showPlanner = false },
            )
        }
    }
}

/** Title, time, servings stepper, macros and tags — the card that overlaps the hero. */
@Composable
private fun HeaderCard(
    details: RecipeWithDetails,
    servings: Int,
    onServingsChange: (Int) -> Unit,
) {
    val recipe = details.recipe
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLow, cornerRadius = 32.dp)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(recipe.title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
        recipe.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val minutes = (recipe.prepTimeMinutes ?: 0) + (recipe.cookTimeMinutes ?: 0)
            if (minutes > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.time_min, minutes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                StepperButton(Icons.Filled.Remove, stringResource(R.string.detail_serving_fewer)) { onServingsChange(servings - 1) }
                Text("$servings", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                StepperButton(Icons.Filled.Add, stringResource(R.string.detail_serving_more)) { onServingsChange(servings + 1) }
                Text(stringResource(R.string.form_servings), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (details.tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                details.tags.take(3).forEach { ClayTagChip(stringResource(Categories.displayNameRes(it.name))) }
            }
        }
    }
}

@Composable
private fun StepperButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clayBevel(PillShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun IngredientList(
    ingredients: List<IngredientEntity>,
    factor: Double,
    ticked: Set<Long>,
    onToggle: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ingredients.forEach { ingredient ->
            val checked = ingredient.id in ticked
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLow, cornerRadius = 20.dp)
                    .clickable { onToggle(ingredient.id) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClayCheckbox(checked = checked, onCheckedChange = { onToggle(ingredient.id) })
                Text(
                    ingredient.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(start = 14.dp),
                )
                val amount = listOfNotNull(Quantities.scale(ingredient.quantity, factor), ingredient.unit)
                    .joinToString(" ")
                    .trim()
                if (amount.isNotEmpty()) {
                    Text(amount, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** [factor] keeps the amounts written into the steps in step with the stepper. */
@Composable
private fun StepList(details: RecipeWithDetails, factor: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        details.steps.sortedBy { it.position }.forEachIndexed { index, step ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLow, cornerRadius = 20.dp)
                    .padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clayBevel(PillShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    Quantities.scaleInText(step.text, factor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * Where the recipe came from, and a way back to it.
 *
 * A reel's caption is never the whole story — the technique is in the video. The
 * link opens through the system, so Instagram or YouTube handle it in their own
 * app when installed rather than in a stripped-down web view.
 */
@Composable
private fun SourceSection(
    details: RecipeWithDetails,
    refreshState: RefreshState,
    onRefresh: () -> Unit,
) {
    val source = details.source ?: return
    val context = LocalContext.current
    val url = source.url?.takeIf { it.startsWith("http") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (url != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ClayButton(
                    text = stringResource(R.string.detail_open_source, sourceLabel(source.platform)),
                    icon = Icons.Filled.OpenInNew,
                    onClick = {
                        val view = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                        // No handler at all (no browser, link stripped): say so rather
                        // than crash on an unresolved intent.
                        runCatching { context.startActivity(view) }.onFailure {
                            Toast.makeText(context, R.string.detail_open_source_failed, Toast.LENGTH_SHORT).show()
                        }
                    },
                    container = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                ClayButton(
                    text = stringResource(
                        if (refreshState is RefreshState.Running) R.string.detail_refreshing
                        else R.string.detail_refresh
                    ),
                    icon = Icons.Filled.Refresh,
                    enabled = refreshState !is RefreshState.Running,
                    onClick = onRefresh,
                    container = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
            when (val s = refreshState) {
                is RefreshState.Failed -> Text(
                    stringResource(R.string.detail_refresh_error, s.message),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                is RefreshState.Done -> Text(
                    stringResource(R.string.detail_refresh_done),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                else -> Unit
            }
        }
        val caption = listOfNotNull(source.author, url).joinToString(" · ")
        if (caption.isNotBlank()) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/** Platform key → the name shown on the button ("Open on Instagram"). */
@Composable
private fun sourceLabel(platform: String): String = when (platform) {
    Platform.INSTAGRAM -> stringResource(R.string.source_instagram)
    Platform.TIKTOK -> stringResource(R.string.source_tiktok)
    Platform.YOUTUBE -> stringResource(R.string.source_youtube)
    Platform.FACEBOOK -> stringResource(R.string.source_facebook)
    Platform.MEALDB -> stringResource(R.string.source_mealdb)
    else -> stringResource(R.string.import_platform_web)
}

/**
 * Macros added up from the ingredient list, plus the way into the chat.
 *
 * There is no "estimate" button any more: the numbers come from a lookup table,
 * so they are already there when the screen opens. [MacroCalculator] returns null
 * rather than a total it can't stand behind, and the card says how much of the
 * recipe it recognised.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MacrosPanel(
    macros: MacroCalculator.Macros?,
    onOpenChat: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ClaySectionHeader(title = stringResource(R.string.detail_nutrition))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (macros == null) {
                Text(
                    stringResource(R.string.detail_macros_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Four chips do not fit one row at larger font sizes; wrapping
                // beats a chip whose label breaks across three lines.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MacroChip(stringResource(R.string.macro_kcal, macros.kcal.toInt()))
                    MacroChip(stringResource(R.string.macro_protein, macros.proteinG.toInt()))
                    MacroChip(stringResource(R.string.macro_fat, macros.fatG.toInt()))
                    MacroChip(stringResource(R.string.macro_carbs, macros.carbsG.toInt()))
                }
                Text(
                    stringResource(
                        if (macros.perServing) R.string.detail_macros_per_serving
                        else R.string.detail_macros_whole_recipe,
                        macros.matched,
                        macros.total,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Naming what was left out beats a bare count: it is usually a
                // spelling the table doesn't know, which the user can just fix.
                if (macros.unmatched.isNotEmpty()) {
                    Text(
                        stringResource(R.string.detail_macros_unmatched, macros.unmatched.joinToString(", ")),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
        ClayButton(
            text = stringResource(R.string.chat_title),
            icon = Icons.AutoMirrored.Filled.Chat,
            onClick = onOpenChat,
            container = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Export options: share plain text, copy Markdown, copy JSON, or copy/send prompt for AI. */
@Composable
private fun ExportSheet(
    details: RecipeWithDetails,
    markdown: String,
    json: String,
    caption: String?,
    onDone: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.export_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(R.string.export_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ClayButton(
            text = stringResource(R.string.export_social_card),
            icon = Icons.Filled.AutoAwesome,
            onClick = {
                val file = SocialCardGenerator.generateCard(context, details)
                val uri = SocialCardGenerator.getShareableUri(context, file)
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    android.content.Intent.createChooser(send, context.getString(R.string.export_social_card))
                )
                onDone()
            },
            container = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth(),
        )
        ClayButton(
            text = stringResource(R.string.export_pdf),
            icon = Icons.Filled.Description,
            onClick = {
                val file = RecipePdfExporter.exportPdf(context, details)
                val uri = RecipePdfExporter.getShareableUri(context, file)
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    android.content.Intent.createChooser(send, context.getString(R.string.export_pdf))
                )
                onDone()
            },
            container = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
        ClayButton(
            text = stringResource(R.string.export_share),
            icon = Icons.Filled.IosShare,
            onClick = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, markdown)
                }
                context.startActivity(
                    android.content.Intent.createChooser(send, context.getString(R.string.export_share))
                )
                onDone()
            },
            container = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
        ClayButton(
            text = stringResource(R.string.export_copy_markdown),
            icon = Icons.Filled.ContentCopy,
            onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(markdown))
                onDone()
            },
            container = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
        ClayButton(
            text = stringResource(R.string.export_copy_json),
            icon = Icons.Filled.DataObject,
            onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(json))
                onDone()
            },
            container = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
        val language = java.util.Locale.getDefault().getDisplayLanguage(java.util.Locale.ENGLISH)
        val prompt = if (caption != null) {
            RecipePrompt.forCaption(caption, language)
        } else {
            RecipePrompt.forRecipeWithDetails(details, language)
        }
        Text(
            stringResource(R.string.export_enrich_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ClayButton(
            text = stringResource(R.string.export_enrich_prompt),
            icon = Icons.Filled.AutoAwesome,
            onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(prompt))
                onDone()
            },
            container = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
        ClayButton(
            text = stringResource(R.string.export_share_enrich_prompt),
            icon = Icons.Filled.IosShare,
            onClick = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, prompt)
                }
                context.startActivity(
                    android.content.Intent.createChooser(send, context.getString(R.string.export_share_enrich_prompt))
                )
                onDone()
            },
            container = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MacroChip(text: String) {
    ClayChip(
        text = text,
        container = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}
