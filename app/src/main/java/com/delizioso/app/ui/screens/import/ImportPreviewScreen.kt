package com.delizioso.app.ui.screens.import

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayChip
import com.delizioso.app.ui.components.ClayTopBar
import com.delizioso.app.ui.components.PhotoPickerArea
import com.delizioso.app.ui.screens.create.IngredientsCard
import com.delizioso.app.ui.screens.create.InstructionsCard
import com.delizioso.app.ui.screens.create.RecipeIdentityFields
import com.delizioso.app.ui.screens.create.RecipeMetaFields
import com.delizioso.app.ui.screens.create.rememberRecipeFormState
import kotlinx.coroutines.launch

/** Edit-before-save preview for an imported recipe. */
@Composable
fun ImportPreviewScreen(
    viewModel: ImportViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Hold on to the imported recipe: saving resets the view model to Idle, and
    // this screen must keep rendering until the caller has navigated away.
    var ready by remember { mutableStateOf<ImportUiState.Ready?>(null) }
    val pickedPhoto by viewModel.pickedPhoto.collectAsStateWithLifecycle()
    val form = rememberRecipeFormState()
    val scope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::onPhotoPicked)
    }

    LaunchedEffect(state) {
        (state as? ImportUiState.Ready)?.let {
            ready = it
            form.applyDraft(it.recipe)
        }
    }

    val current = ready
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Nothing to review yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        ClayTopBar(
            title = "Review & Save",
            onMenu = onBack,
            menuIcon = Icons.AutoMirrored.Filled.ArrowBack,
            menuDescription = "Back",
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                PhotoPickerArea(
                    imageUri = pickedPhoto ?: current.raw.thumbnailUrl ?: current.recipe.imageUrl,
                    onPick = { photoPicker.launch("image/*") },
                )
            }
            item {
                ClayChip(
                    text = "From ${current.raw.author ?: current.raw.platform.lowercase()}",
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            item { RecipeIdentityFields(form) }
            item { IngredientsCard(form) }
            item { InstructionsCard(form) }
            item { RecipeMetaFields(form) }
            item { Spacer(Modifier.height(8.dp)) }
        }
        ClayButton(
            text = "Save Recipe",
            icon = Icons.Filled.Save,
            enabled = form.isValid,
            onClick = {
                scope.launch {
                    val id = viewModel.save(form.toStructuredRecipe(), current.raw, form.tagList())
                    onSaved(id)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}
