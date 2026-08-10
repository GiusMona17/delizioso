package com.delizioso.app.ui.screens.detail

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
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
import com.delizioso.app.data.Quantities
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.ai.AiUnavailableException
import com.delizioso.app.data.ai.NanoAdvisor
import com.delizioso.app.data.ai.RecipeAdvice
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
import com.delizioso.app.ui.components.RecipeImage
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.Primary
import com.delizioso.app.ui.theme.clayCard
import com.delizioso.app.ui.theme.clayInner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AdviceState {
    data object Hidden : AdviceState
    data object Loading : AdviceState
    data class Loaded(val advice: RecipeAdvice) : AdviceState
    data object ConsentNeeded : AdviceState
    data class Error(val message: String, val retryable: Boolean) : AdviceState
}

class RecipeDetailViewModel(
    private val repository: RecipeRepository,
    private val advisor: NanoAdvisor,
    private val preferences: UserPreferences,
    private val recipeId: Long,
) : ViewModel() {

    val details: StateFlow<RecipeWithDetails?> =
        repository.byId(recipeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _advice = MutableStateFlow<AdviceState>(AdviceState.Hidden)
    val advice: StateFlow<AdviceState> = _advice.asStateFlow()

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

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(recipeId)
            onDeleted()
        }
    }

    /** Generate AI macros + substitutions for this recipe (on-device, consent-gated). */
    fun requestAdvice() {
        val d = details.value ?: return
        viewModelScope.launch {
            _advice.value = AdviceState.Loading
            try {
                val recipe = StructuredRecipe(
                    title = d.recipe.title,
                    description = d.recipe.description,
                    servings = d.recipe.servings,
                    prepTimeMinutes = d.recipe.prepTimeMinutes,
                    cookTimeMinutes = d.recipe.cookTimeMinutes,
                    ingredients = d.ingredients,
                    steps = d.steps.map { it.text },
                )
                val advice = advisor.advice(recipe)
                _advice.value = AdviceState.Loaded(advice)
                advice.macros?.let {
                    repository.updateMacros(recipeId, it.kcal, it.proteinG, it.fatG, it.carbsG)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiUnavailableException) {
                _advice.value = if (e.retryable) {
                    AdviceState.Error(e.message ?: "On-device AI is busy", true)
                } else {
                    AdviceState.ConsentNeeded
                }
            } catch (e: Exception) {
                _advice.value = AdviceState.Error(e.message ?: "AI advice failed", false)
            }
        }
    }

    fun grantConsentAndRetry() {
        viewModelScope.launch {
            preferences.setAiConsent(true)
            requestAdvice()
        }
    }

    companion object {
        fun factory(recipeId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                RecipeDetailViewModel(
                    repository = app.container.recipeRepository,
                    advisor = app.container.nanoAdvisor,
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
    viewModel: RecipeDetailViewModel = viewModel(
        key = "recipe-detail-$recipeId",
        factory = RecipeDetailViewModel.factory(recipeId),
    ),
) {
    val details by viewModel.details.collectAsStateWithLifecycle()
    val advice by viewModel.advice.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(TAB_INGREDIENTS) }
    var servings by rememberSaveable { mutableStateOf(0) }
    var showPlanner by remember { mutableStateOf(false) }
    val ticked = remember { mutableStateOf(setOf<Long>()) }

    val d = details
    // Seed the servings stepper from the recipe once it loads.
    LaunchedEffect(d?.recipe?.servings) {
        if (servings == 0) servings = d?.recipe?.servings ?: 2
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
            RecipeImage(
                d.recipe.imageUri,
                placeholderIconSize = 72.dp,
                modifier = Modifier.fillMaxWidth().height(320.dp),
            )
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
                } else {
                    StepList(d)
                }
                AiAdvicePanel(advice, onRequest = viewModel::requestAdvice, onGrant = viewModel::grantConsentAndRetry)
                SourceSection(d)
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

        // Floating actions over the hero.
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

        ClayButton(
            text = "Start Cooking",
            icon = Icons.Filled.PlayArrow,
            onClick = onStartCooking,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
        )
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
            .clayInner(PillShape, cornerRadius = null)
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
                        .clayInner(PillShape, cornerRadius = null),
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

/** On-device AI: per-serving macro estimates + ingredient substitutions. */
@Composable
private fun AiAdvicePanel(
    advice: AdviceState,
    onRequest: () -> Unit,
    onGrant: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ClaySectionHeader(title = "AI Insights")
        when (advice) {
            is AdviceState.Hidden -> {
                ClayButton(
                    text = "Get macros & substitutions",
                    icon = Icons.Filled.LocalFireDepartment,
                    onClick = onRequest,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is AdviceState.Loading -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
                    Text("Asking the on-device AI…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is AdviceState.ConsentNeeded -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayCard(container = MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("On-device AI needs your consent", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "Macros and substitutions are generated by Gemini Nano on your phone — nothing leaves the device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    ClayButton(text = "Enable on-device AI", onClick = onGrant, modifier = Modifier.fillMaxWidth())
                }
            }
            is AdviceState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayCard(container = MaterialTheme.colorScheme.errorContainer)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(advice.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    if (advice.retryable) {
                        ClayButton(text = "Retry", onClick = onRequest, container = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            is AdviceState.Loaded -> {
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
                    advice.advice.macros?.let { macros ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MacroChip("${macros.kcal?.toInt() ?: "—"} kcal")
                            MacroChip("${macros.proteinG?.toInt() ?: "—"}g protein")
                            MacroChip("${macros.fatG?.toInt() ?: "—"}g fat")
                            MacroChip("${macros.carbsG?.toInt() ?: "—"}g carbs")
                        }
                    }
                    if (advice.advice.substitutions.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Try these swaps", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                            advice.advice.substitutions.forEach { sub ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                    Text(
                                        sub.ingredient,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(0.35f),
                                    )
                                    Text(
                                        sub.suggestion,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(0.65f),
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "Regenerate",
                        style = MaterialTheme.typography.labelLarge,
                        color = Primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .clickable(onClick = onRequest)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
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
