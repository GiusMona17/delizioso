package com.delizioso.app.ui.screens.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.delizioso.app.data.import.IngredientParser
import com.delizioso.app.data.import.StructuredRecipe
import com.delizioso.app.ui.components.ClayLabelledField
import com.delizioso.app.ui.components.EditableLineRow
import com.delizioso.app.ui.components.FormSectionCard
import com.delizioso.app.ui.components.StepNumberPod

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
    tags: String = "",
) {
    var title by mutableStateOf(title)
    var description by mutableStateOf(description)
    var servings by mutableStateOf(servings)
    var prep by mutableStateOf(prep)
    var cook by mutableStateOf(cook)
    var ingredients by mutableStateOf(ingredients)
    var steps by mutableStateOf(steps)
    var tags by mutableStateOf(tags)

    val isValid: Boolean
        get() = title.isNotBlank() && ingredients.any { it.isNotBlank() } && steps.any { it.isNotBlank() }

    fun applyDraft(draft: StructuredRecipe) {
        title = draft.title.orEmpty()
        description = draft.description.orEmpty()
        servings = draft.servings?.toString() ?: ""
        prep = draft.prepTimeMinutes?.toString() ?: ""
        cook = draft.cookTimeMinutes?.toString() ?: ""
        ingredients = draft.ingredients.map { it.rawText ?: it.name }.ifEmpty { listOf("") }
        steps = draft.steps.ifEmpty { listOf("") }
    }

    fun toStructuredRecipe(): StructuredRecipe = StructuredRecipe(
        title = title.trim(),
        description = description.trim().ifBlank { null },
        servings = servings.toIntOrNull(),
        prepTimeMinutes = prep.toIntOrNull(),
        cookTimeMinutes = cook.toIntOrNull(),
        ingredients = ingredients.map { IngredientParser.split(it) }.filter { it.name.isNotBlank() },
        steps = steps.filter { it.isNotBlank() },
    )

    fun tagList(): List<String> =
        tags.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct()
}

@Composable
fun rememberRecipeFormState(): RecipeFormState = rememberSaveable(saver = RecipeFormSaver) { RecipeFormState() }

private val RecipeFormSaver = androidx.compose.runtime.saveable.listSaver<RecipeFormState, Any>(
    save = { listOf(it.title, it.description, it.servings, it.prep, it.cook, it.ingredients, it.steps, it.tags) },
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
            tags = it[7] as String,
        )
    },
)

/** The title/description block. */
@Composable
fun RecipeIdentityFields(state: RecipeFormState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ClayLabelledField(
            label = "Recipe Title",
            value = state.title,
            onValueChange = { state.title = it },
            placeholder = "e.g. Grandma's Apple Pie",
        )
        ClayLabelledField(
            label = "Description",
            value = state.description,
            onValueChange = { state.description = it },
            placeholder = "A brief description of this dish…",
            singleLine = false,
        )
    }
}

@Composable
fun IngredientsCard(state: RecipeFormState, modifier: Modifier = Modifier) {
    FormSectionCard(
        title = "Ingredients",
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
                    placeholder = "e.g. 2 cups flour",
                )
            }
        }
    }
}

@Composable
fun InstructionsCard(state: RecipeFormState, modifier: Modifier = Modifier) {
    FormSectionCard(
        title = "Instructions",
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
                    placeholder = "Describe this step…",
                    singleLine = false,
                    leading = { StepNumberPod(index + 1) },
                )
            }
        }
    }
}

/** Prep time, cook time, servings and free-text tags. */
@Composable
fun RecipeMetaFields(state: RecipeFormState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ClayLabelledField(
                label = "Prep Time",
                value = state.prep,
                onValueChange = { state.prep = it },
                placeholder = "30",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            ClayLabelledField(
                label = "Cook Time",
                value = state.cook,
                onValueChange = { state.cook = it },
                placeholder = "20",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            ClayLabelledField(
                label = "Servings",
                value = state.servings,
                onValueChange = { state.servings = it },
                placeholder = "4",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        ClayLabelledField(
            label = "Tags",
            value = state.tags,
            onValueChange = { state.tags = it },
            placeholder = "Vegetarian, Dinner, Quick",
        )
    }
}
