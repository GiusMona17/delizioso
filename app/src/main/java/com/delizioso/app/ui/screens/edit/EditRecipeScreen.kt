package com.delizioso.app.ui.screens.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.delizioso.app.DeliziosoApplication
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.data.toStructuredRecipe
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayTopBar
import com.delizioso.app.ui.screens.create.IngredientsCard
import com.delizioso.app.ui.screens.create.InstructionsCard
import com.delizioso.app.ui.screens.create.RecipeIdentityFields
import com.delizioso.app.ui.screens.create.RecipeMetaFields
import com.delizioso.app.ui.screens.create.rememberRecipeFormState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditRecipeViewModel(
    private val repository: RecipeRepository,
    private val recipeId: Long,
) : ViewModel() {

    val details: StateFlow<RecipeWithDetails?> =
        repository.byId(recipeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(
        recipe: com.delizioso.app.data.import.StructuredRecipe,
        categories: List<String>,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            repository.update(recipeId, recipe, categories)
            onSaved()
        }
    }

    companion object {
        fun factory(recipeId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                EditRecipeViewModel(app.container.recipeRepository, recipeId)
            }
        }
    }
}

/** Edit a saved recipe, reusing the create/import form. */
@Composable
fun EditRecipeScreen(
    recipeId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditRecipeViewModel = viewModel(
        key = "edit-$recipeId",
        factory = EditRecipeViewModel.factory(recipeId),
    ),
) {
    val details by viewModel.details.collectAsStateWithLifecycle()
    val form = rememberRecipeFormState()

    // Fill the form once the recipe has loaded; keyed on the id so the user's
    // in-progress edits are never overwritten by a later emission.
    LaunchedEffect(details?.recipe?.id) {
        details?.let { form.applyDraft(it.toStructuredRecipe()) }
    }

    if (details == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        ClayTopBar(
            title = "Edit Recipe",
            onMenu = onBack,
            menuIcon = Icons.AutoMirrored.Filled.ArrowBack,
            menuDescription = "Back",
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.weight(1f),
        ) {
            item { RecipeIdentityFields(form) }
            item { IngredientsCard(form) }
            item { InstructionsCard(form) }
            item { RecipeMetaFields(form) }
            item { Spacer(Modifier.height(8.dp)) }
        }
        ClayButton(
            text = "Save Changes",
            icon = Icons.Filled.Save,
            enabled = form.isValid,
            onClick = { viewModel.save(form.toStructuredRecipe(), form.categoryList(), onSaved) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}
