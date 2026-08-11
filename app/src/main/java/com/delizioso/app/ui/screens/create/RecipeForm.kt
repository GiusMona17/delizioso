package com.delizioso.app.ui.screens.create

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.delizioso.app.data.Categories
import com.delizioso.app.data.import.IngredientParser
import com.delizioso.app.data.import.StructuredRecipe
import com.delizioso.app.ui.components.ClayLabelledField
import com.delizioso.app.ui.components.EditableLineRow
import com.delizioso.app.ui.components.FormSectionCard
import com.delizioso.app.ui.components.StepNumberPod
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.clayBevel
import androidx.compose.ui.res.stringResource
import com.delizioso.app.R

/**
 * Editable recipe form shared by "Create Recipe" and the import preview.
 * Holds its own field state; [RecipeFormState.toStructuredRecipe] reads it back out.
 */
class RecipeFormState(
    title: String = "",
    description: String = "",
    servings: String = "",
    prep: String = "",
    cook: String = "",
    ingredients: List<String> = listOf(""),
    steps: List<String> = listOf(""),
    categories: List<String> = emptyList(),
) {
    var title by mutableStateOf(title)
    var description by mutableStateOf(description)
    var servings by mutableStateOf(servings)
    var prep by mutableStateOf(prep)
    var cook by mutableStateOf(cook)
    var ingredients by mutableStateOf(ingredients)
    var steps by mutableStateOf(steps)
    var categories by mutableStateOf(categories)

    val isValid: Boolean
        get() = title.isNotBlank() && ingredients.any { it.isNotBlank() } && steps.any { it.isNotBlank() }

    fun toggleCategory(name: String) {
        categories = if (name in categories) {
            categories - name
        } else {
            // Keep the cap the AI is held to, so chips stay readable.
            (categories + name).takeLast(Categories.MAX_PER_RECIPE)
        }
    }

    fun applyDraft(draft: StructuredRecipe) {
        title = draft.title.orEmpty()
        description = draft.description.orEmpty()
        servings = draft.servings?.toString() ?: ""
        prep = draft.prepTimeMinutes?.toString() ?: ""
        cook = draft.cookTimeMinutes?.toString() ?: ""
        ingredients = draft.ingredients.map { it.rawText ?: it.name }.ifEmpty { listOf("") }
        steps = draft.steps.ifEmpty { listOf("") }
        categories = Categories.canonicalise(draft.categories)
    }

    fun toStructuredRecipe(): StructuredRecipe = StructuredRecipe(
        title = title.trim(),
        description = description.trim().ifBlank { null },
        servings = servings.toIntOrNull(),
        prepTimeMinutes = prep.toIntOrNull(),
        cookTimeMinutes = cook.toIntOrNull(),
        ingredients = ingredients.map { IngredientParser.split(it) }.filter { it.name.isNotBlank() },
        steps = steps.filter { it.isNotBlank() },
        categories = Categories.canonicalise(categories),
    )

    fun categoryList(): List<String> = Categories.canonicalise(categories)
}

@Composable
fun rememberRecipeFormState(): RecipeFormState = rememberSaveable(saver = RecipeFormSaver) { RecipeFormState() }

private val RecipeFormSaver = androidx.compose.runtime.saveable.listSaver<RecipeFormState, Any>(
    save = { listOf(it.title, it.description, it.servings, it.prep, it.cook, it.ingredients, it.steps, it.categories) },
    restore = {
        @Suppress("UNCHECKED_CAST")
        RecipeFormState(
            title = it[0] as String,
            description = it[1] as String,
            servings = it[2] as String,
            prep = it[3] as String,
            cook = it[4] as String,
            ingredients = it[5] as List<String>,
            steps = it[6] as List<String>,
            categories = it[7] as List<String>,
        )
    },
)

/** The title/description block. */
@Composable
fun RecipeIdentityFields(state: RecipeFormState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ClayLabelledField(
            label = stringResource(R.string.form_title_label),
            value = state.title,
            onValueChange = { state.title = it },
            placeholder = stringResource(R.string.form_title_placeholder),
        )
        ClayLabelledField(
            label = stringResource(R.string.form_desc_label),
            value = state.description,
            onValueChange = { state.description = it },
            placeholder = stringResource(R.string.form_desc_placeholder),
            singleLine = false,
        )
    }
}

@Composable
fun IngredientsCard(state: RecipeFormState, modifier: Modifier = Modifier) {
    FormSectionCard(
        title = stringResource(R.string.form_ingredients_title),
        onAdd = { state.ingredients = state.ingredients + "" },
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.ingredients.forEachIndexed { index, line ->
                EditableLineRow(
                    value = line,
                    onValueChange = { new -> state.ingredients = state.ingredients.toMutableList().also { it[index] = new } },
                    onDelete = {
                        state.ingredients = state.ingredients.filterIndexed { i, _ -> i != index }.ifEmpty { listOf("") }
                    },
                    placeholder = stringResource(R.string.form_ingredients_placeholder),
                )
            }
        }
    }
}

@Composable
fun InstructionsCard(state: RecipeFormState, modifier: Modifier = Modifier) {
    FormSectionCard(
        title = stringResource(R.string.form_instructions_title),
        onAdd = { state.steps = state.steps + "" },
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.steps.forEachIndexed { index, line ->
                EditableLineRow(
                    value = line,
                    onValueChange = { new -> state.steps = state.steps.toMutableList().also { it[index] = new } },
                    onDelete = {
                        state.steps = state.steps.filterIndexed { i, _ -> i != index }.ifEmpty { listOf("") }
                    },
                    placeholder = stringResource(R.string.form_step_placeholder),
                    singleLine = false,
                    leading = { StepNumberPod(index + 1) },
                )
            }
        }
    }
}

/** Prep time, cook time, servings and the fixed category chips. */
@Composable
fun RecipeMetaFields(state: RecipeFormState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ClayLabelledField(
                label = stringResource(R.string.form_prep_time),
                value = state.prep,
                onValueChange = { state.prep = it },
                placeholder = "30",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            ClayLabelledField(
                label = stringResource(R.string.form_cook_time),
                value = state.cook,
                onValueChange = { state.cook = it },
                placeholder = "20",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            ClayLabelledField(
                label = stringResource(R.string.form_servings),
                value = state.servings,
                onValueChange = { state.servings = it },
                placeholder = "4",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        CategoryPicker(
            selected = state.categories,
            onToggle = state::toggleCategory,
        )
    }
}

/**
 * Fixed-vocabulary category chips. Free text let "Vegetarian"/"veggie"/"Veg"
 * each become their own filter chip in the library, so the list is closed and
 * the AI's suggestion is validated against it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    selected: List<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.form_categories),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${selected.size}/${Categories.MAX_PER_RECIPE}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Categories.ALL.forEach { category ->
                val isSelected = category in selected
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer
                        )
                        .clayBevel(
                            PillShape,
                            light = Color(0x99FFFFFF),
                            dark = if (isSelected) Color(0x33006E20) else Color(0x14000000),
                        )
                        .clickable(role = Role.Checkbox) { onToggle(category) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        stringResource(Categories.displayNameRes(category)),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
