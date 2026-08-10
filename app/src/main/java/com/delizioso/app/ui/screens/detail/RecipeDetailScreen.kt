package com.delizioso.app.ui.screens.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
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
import com.delizioso.app.data.ImageStore
import com.delizioso.app.data.Quantities
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.toStructuredRecipe
import com.delizioso.app.data.ai.AiUnavailableException
import com.delizioso.app.data.ai.NanoAdvisor
import com.delizioso.app.data.ai.ChatMessage
import com.delizioso.app.data.ai.MacrosEstimate
import com.delizioso.app.data.ai.NanoChat
import com.delizioso.app.data.ai.NanoInference
import com.delizioso.app.data.import.StructuredRecipe
import com.delizioso.app.data.local.IngredientEntity
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
import com.delizioso.app.ui.theme.Primary
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

sealed interface MacrosState {
    data object Hidden : MacrosState
    data object Loading : MacrosState
    data class Loaded(val macros: MacrosEstimate) : MacrosState
    data object ConsentNeeded : MacrosState
    data class Error(val message: String, val retryable: Boolean) : MacrosState
}

/** A conversation about one recipe; [streaming] is the answer being typed out. */
data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val streaming: String? = null,
    val error: String? = null,
    /** AICore is fetching Gemini Nano — minutes on first run, not seconds. */
    val preparingModel: Boolean = false,
) {
    val busy: Boolean get() = streaming != null
}

class RecipeDetailViewModel(
    private val repository: RecipeRepository,
    private val advisor: NanoAdvisor,
    private val chatModel: NanoChat,
    private val preferences: UserPreferences,
    private val recipeId: Long,
) : ViewModel() {

    val details: StateFlow<RecipeWithDetails?> =
        repository.byId(recipeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _macros = MutableStateFlow<MacrosState>(MacrosState.Hidden)
    val macros: StateFlow<MacrosState> = _macros.asStateFlow()

    private val _chat = MutableStateFlow(ChatState())
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

    /** Estimate macros per serving with the on-device model. */
    fun estimateMacros() {
        val d = details.value ?: return
        viewModelScope.launch {
            _macros.value = MacrosState.Loading
            try {
                val estimate = advisor.macros(d.toStructuredRecipe())
                _macros.value = MacrosState.Loaded(estimate)
                repository.updateMacros(recipeId, estimate.kcal, estimate.proteinG, estimate.fatG, estimate.carbsG)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiUnavailableException) {
                _macros.value = if (e.retryable) {
                    MacrosState.Error(e.message ?: "On-device AI is busy", true)
                } else {
                    MacrosState.ConsentNeeded
                }
            } catch (e: Exception) {
                _macros.value = MacrosState.Error(e.message ?: "Macro estimate failed", false)
            }
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
        )
        viewModelScope.launch {
            try {
                // First run downloads the model; say so rather than showing a
                // "Thinking…" spinner for several minutes.
                if (chatModel.availability() == NanoInference.Availability.DOWNLOADABLE) {
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
                        error = e.message ?: "The on-device AI could not answer",
                    )
                }
            }
        }
    }

    fun clearChatError() = _chat.update { it.copy(error = null) }

    fun grantConsentAndRetry() {
        viewModelScope.launch {
            preferences.setAiConsent(true)
            estimateMacros()
        }
    }

    companion object {
        fun factory(recipeId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                RecipeDetailViewModel(
                    repository = app.container.recipeRepository,
                    advisor = app.container.nanoAdvisor,
                    chatModel = app.container.nanoChat,
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
    val macrosState by viewModel.macros.collectAsStateWithLifecycle()
    val chat by viewModel.chat.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(TAB_INGREDIENTS) }
    var servings by rememberSaveable { mutableStateOf(0) }
    var showPlanner by remember { mutableStateOf(false) }
    var addedToList by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
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
                        contentDescription = "Back",
                        onClick = onBack,
                        container = MaterialTheme.colorScheme.surfaceContainerLowest,
                    )
                    Spacer(Modifier.weight(1f))
                    ClayRoundButton(
                        icon = Icons.Filled.CalendarMonth,
                        contentDescription = "Add to planner",
                        onClick = { showPlanner = true },
                        container = MaterialTheme.colorScheme.surfaceContainerLowest,
                    )
                    Spacer(Modifier.width(12.dp))
                    ClayRoundButton(
                        icon = if (d.recipe.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favourite",
                        onClick = { viewModel.toggleFavorite(d.recipe.isFavorite) },
                        container = MaterialTheme.colorScheme.surfaceContainerLowest,
                        tint = if (d.recipe.isFavorite) MaterialTheme.colorScheme.error else Primary,
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
                    options = listOf("Ingredients", "Instructions"),
                    selectedIndex = tab,
                    onSelect = { tab = it },
                )
                if (tab == TAB_INGREDIENTS) {
                    IngredientList(
                        ingredients = d.ingredients.sortedBy { it.position },
                        factor = factor,
                        ticked = ticked.value,
                        onToggle = { id ->
                            ticked.value = if (id in ticked.value) ticked.value - id else ticked.value + id
                        },
                    )
                    ClayButton(
                        text = if (addedToList) "Added to shopping list" else "Add all to shopping list",
                        icon = if (addedToList) Icons.Filled.Check else Icons.Filled.ShoppingCart,
                        onClick = {
                            viewModel.addToShoppingList(factor)
                            addedToList = true
                        },
                        container = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = Primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    StepList(d)
                }
                AiPanel(
                    macros = macrosState,
                    onEstimate = viewModel::estimateMacros,
                    onGrant = viewModel::grantConsentAndRetry,
                    onOpenChat = { showChat = true },
                )
                SourceSection(d)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClayButton(
                        text = "Edit recipe",
                        icon = Icons.Filled.Edit,
                        onClick = onEdit,
                        container = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = Primary,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "Delete this recipe",
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
            text = "Start Cooking",
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
                onDismissError = viewModel::clearChatError,
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
                    Text("$minutes min", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                StepperButton(Icons.Filled.Remove, "One serving fewer") { onServingsChange(servings - 1) }
                Text("$servings", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                StepperButton(Icons.Filled.Add, "One serving more") { onServingsChange(servings + 1) }
                Text("Servings", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        recipe.macrosKcal?.let { kcal ->
            val perServing = if ((recipe.servings ?: 0) > 0) servings.toDouble() / recipe.servings!! else 1.0
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text("${kcal.toInt()} kcal", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    macroLine(recipe.macrosProteinG, recipe.macrosCarbsG, recipe.macrosFatG),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (perServing != 1.0) {
                    Text(
                        "per original serving",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
        if (details.tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                details.tags.take(3).forEach { ClayTagChip(it.name) }
            }
        }
    }
}

private fun macroLine(protein: Float?, carbs: Float?, fat: Float?): String = listOfNotNull(
    protein?.let { "P: ${it.toInt()}g" },
    carbs?.let { "C: ${it.toInt()}g" },
    fat?.let { "F: ${it.toInt()}g" },
).joinToString(" • ")

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
        Icon(icon, contentDescription = description, tint = Primary, modifier = Modifier.size(16.dp))
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

@Composable
private fun StepList(details: RecipeWithDetails) {
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
                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = Primary)
                }
                Text(
                    step.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SourceSection(details: RecipeWithDetails) {
    val source = details.source ?: return
    Text(
        text = listOfNotNull(source.author, source.url).joinToString(" · "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
}

/** On-device AI: per-serving macro estimates, plus the way into the chat. */
@Composable
private fun AiPanel(
    macros: MacrosState,
    onEstimate: () -> Unit,
    onGrant: () -> Unit,
    onOpenChat: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ClaySectionHeader(title = "AI Insights")
        when (macros) {
            is MacrosState.Hidden -> {
                ClayButton(
                    text = "Estimate macros",
                    icon = Icons.Filled.LocalFireDepartment,
                    onClick = onEstimate,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is MacrosState.Loading -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
                    Text("Estimating macros…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is MacrosState.ConsentNeeded -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayCard(container = MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("On-device AI needs your consent", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "Estimates are generated by Gemini Nano on your phone — nothing leaves the device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    ClayButton(text = "Enable on-device AI", onClick = onGrant, modifier = Modifier.fillMaxWidth())
                }
            }
            is MacrosState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayCard(container = MaterialTheme.colorScheme.errorContainer)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(macros.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    if (macros.retryable) {
                        ClayButton(text = "Retry", onClick = onEstimate, container = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            is MacrosState.Loaded -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Estimates per serving — not nutrition facts.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MacroChip("${macros.macros.kcal?.toInt() ?: "—"} kcal")
                        MacroChip("${macros.macros.proteinG?.toInt() ?: "—"}g protein")
                        MacroChip("${macros.macros.fatG?.toInt() ?: "—"}g fat")
                        MacroChip("${macros.macros.carbsG?.toInt() ?: "—"}g carbs")
                    }
                    Text(
                        "Re-estimate",
                        style = MaterialTheme.typography.labelLarge,
                        color = Primary,
                        modifier = Modifier
                            .clip(PillShape)
                            .clickable(onClick = onEstimate)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        ClayButton(
            text = "Ask about this recipe",
            icon = Icons.AutoMirrored.Filled.Chat,
            onClick = onOpenChat,
            container = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = Primary,
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
