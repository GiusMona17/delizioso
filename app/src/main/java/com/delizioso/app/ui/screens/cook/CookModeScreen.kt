package com.delizioso.app.ui.screens.cook

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delizioso.app.data.local.StepEntity
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayCheckbox
import com.delizioso.app.ui.components.ClayChip
import com.delizioso.app.ui.components.ClayRoundButton
import com.delizioso.app.ui.components.ClayStepPod
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.clayCard
import com.delizioso.app.ui.theme.ClayShadow
import com.delizioso.app.ui.theme.clayBevel
import com.delizioso.app.ui.theme.clayInset
import com.delizioso.app.ui.theme.clayOuter
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.delizioso.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookModeScreen(
    recipeId: Long,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: CookViewModel = viewModel(
        key = "cook-$recipeId",
        factory = CookViewModel.factory(recipeId),
    ),
) {
    val details by viewModel.details.collectAsStateWithLifecycle()
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()
    val completed by viewModel.completed.collectAsStateWithLifecycle()
    val gathered by viewModel.gathered.collectAsStateWithLifecycle()
    var showIngredients by remember { mutableStateOf(false) }

    // Hands are floury and the phone is propped on the counter: letting the screen
    // time out mid-step is the single most annoying thing this screen could do.
    KeepScreenOn()

    val d = details
    val steps = d?.steps?.sortedBy { it.position }.orEmpty()
    val listState = rememberLazyListState()

    // Keep the active step in view as the cook advances.
    LaunchedEffect(currentStep) {
        if (currentStep < steps.size) listState.animateScrollToItem(currentStep)
    }

    val onLastStep = steps.isNotEmpty() && currentStep >= steps.size - 1

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clayBevel(RoundedCornerShape(0.dp), light = ClayShadow.highlight, dark = ClayShadow.accentSoft)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClayRoundButton(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.cook_exit),
                onClick = onBack,
                container = MaterialTheme.colorScheme.surfaceContainerLowest,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.cook_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            ClayRoundButton(
                icon = Icons.Filled.Restaurant,
                contentDescription = stringResource(R.string.cook_ingredients),
                onClick = { showIngredients = true },
                container = MaterialTheme.colorScheme.surfaceContainerLowest,
            )
        }

        if (steps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (d == null) "" else stringResource(R.string.cook_no_steps),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        Text(
            stringResource(R.string.cook_step_of, (currentStep + 1).coerceAtMost(steps.size), steps.size),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        StepProgressBar(
            progress = completed.size.toFloat() / steps.size,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(steps, key = { _, step -> step.id }) { index, step ->
                StepCard(
                    index = index,
                    step = step,
                    active = index == currentStep,
                    done = index in completed,
                    onToggle = { viewModel.toggleCompleted(index) },
                    onFocus = { viewModel.goTo(index) },
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        ClayButton(
            text = if (onLastStep) stringResource(R.string.cook_finish) else stringResource(R.string.cook_next_step),
            icon = if (onLastStep) Icons.Filled.Check else Icons.Filled.ArrowDownward,
            onClick = {
                if (onLastStep) {
                    viewModel.toggleCompleted(currentStep)
                    viewModel.markCooked()
                    onFinished()
                } else {
                    viewModel.next()
                }
            },
            container = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }

    if (showIngredients && d != null) {
        ModalBottomSheet(
            onDismissRequest = { showIngredients = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.cook_ingredients),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    ClayRoundButton(
                        icon = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.topbar_close),
                        onClick = { showIngredients = false },
                        container = MaterialTheme.colorScheme.surfaceContainerLowest,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                d.ingredients.sortedBy { it.position }.forEach { ingredient ->
                    val checked = ingredient.id in gathered
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 22.dp)
                            .clickable { viewModel.toggleGathered(ingredient.id) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ClayCheckbox(checked = checked, onCheckedChange = { viewModel.toggleGathered(ingredient.id) })
                        Text(
                            ingredient.rawText ?: listOfNotNull(ingredient.quantity, ingredient.unit, ingredient.name).joinToString(" "),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                            modifier = Modifier.weight(1f).padding(start = 14.dp),
                        )
                        ingredient.note?.takeIf { it.isNotBlank() }?.let {
                            ClayChip(
                                it,
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
                ClayButton(
                    text = stringResource(R.string.cook_add_missing),
                    icon = Icons.Filled.ShoppingCart,
                    onClick = {
                        viewModel.addMissingToShoppingList()
                        showIngredients = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StepProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "cookProgress")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .clayInset(MaterialTheme.colorScheme.surfaceContainer, cornerRadius = 7.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(14.dp)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clayBevel(PillShape, light = ClayShadow.highlight, dark = ClayShadow.innerAccent),
        )
    }
}

@Composable
private fun StepCard(
    index: Int,
    step: StepEntity,
    active: Boolean,
    done: Boolean,
    onToggle: () -> Unit,
    onFocus: () -> Unit,
) {
    val container = when {
        active -> MaterialTheme.colorScheme.surfaceContainerLowest
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (active) Modifier.clayOuter(shape = RoundedCornerShape(28.dp), elevation = 24.dp, dark = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                else Modifier
            )
            .clip(RoundedCornerShape(28.dp))
            .background(container)
            .then(
                if (active) Modifier.border(2.dp, MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(28.dp))
                else Modifier
            )
            .clickable(onClick = onFocus)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClayStepPod(done = done, onToggle = onToggle)
            Text(
                stringResource(R.string.cook_step_label, index + 1),
                style = MaterialTheme.typography.headlineMedium,
                color = when {
                    active -> MaterialTheme.colorScheme.primary
                    done -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f).padding(start = 16.dp),
            )
        }
        Text(
            step.text,
            style = if (active) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
        )
        if (active) {
            StepTimer.parseSeconds(step.text)?.let { seconds -> InlineTimer(totalSeconds = seconds) }
        }
    }
}

/** Countdown pill shown inside the active step, per the cook-mode mockup. */
@Composable
private fun InlineTimer(totalSeconds: Int) {
    var remaining by remember(totalSeconds) { mutableStateOf(totalSeconds) }
    var running by remember(totalSeconds) { mutableStateOf(false) }

    LaunchedEffect(running, remaining) {
        if (running && remaining > 0) {
            delay(1_000)
            remaining -= 1
        } else if (remaining == 0) {
            running = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayInset(MaterialTheme.colorScheme.surfaceContainer, cornerRadius = 28.dp)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.HourglassEmpty,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 14.dp).size(22.dp),
        )
        Text(
            StepTimer.format(remaining),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 14.dp),
        )
        ClayButton(
            text = when {
                remaining == 0 -> stringResource(R.string.cook_timer_done)
                running -> stringResource(R.string.cook_timer_pause)
                else -> stringResource(R.string.cook_timer_start)
            },
            icon = if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            onClick = {
                if (remaining == 0) remaining = totalSeconds else running = !running
            },
            container = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * Holds the screen awake for as long as this composable is on screen, and lets go
 * again on the way out so the phone is not left burning battery on the counter.
 */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
