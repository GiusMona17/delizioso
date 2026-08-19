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
import com.delizioso.app.data.ai.RecipeRefiner
import com.delizioso.app.data.ai.RefineState
import com.delizioso.app.data.import.StructuredRecipe
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.data.toStructuredRecipe
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayTopBar
import com.delizioso.app.ui.components.RefineRecipeCard
import com.delizioso.app.ui.screens.create.IngredientsCard
import com.delizioso.app.ui.screens.create.InstructionsCard
import com.delizioso.app.ui.screens.create.RecipeIdentityFields
import com.delizioso.app.ui.screens.create.RecipeMetaFields
import com.delizioso.app.ui.screens.create.rememberRecipeFormState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.delizioso.app.R
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.delizioso.app.data.export.RecipePrompt
import com.delizioso.app.data.import.RecipeJsonImporter
import com.delizioso.app.ui.components.ClayTextField
import com.delizioso.app.ui.theme.clayCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class EditRecipeViewModel(
    private val repository: RecipeRepository,
    private val refiner: RecipeRefiner,
    private val recipeId: Long,
) : ViewModel() {

    val details: StateFlow<RecipeWithDetails?> =
        repository.byId(recipeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The caption this recipe was imported from, when one was kept.
     *
     * It is what the assistant is asked to reorganise, and it lives here rather
     * than on the import screen because that is where the recipe already exists:
     * re-importing as a new recipe would lose the source link and the photo.
     */
    val caption: StateFlow<String?> = details
        .map { it?.source?.rawText?.takeIf { text -> text.isNotBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The same convert-and-translate pass the import preview offers. A recipe in
     * cups is no less annoying once it is saved, and one typed by hand can want
     * the treatment too.
     */
    val refine: StateFlow<RefineState> = refiner.state

    fun convertAndTranslate(recipe: StructuredRecipe, onRefined: (StructuredRecipe) -> Unit) {
        refiner.clearError()
        refiner.refine(viewModelScope, recipe, onRefined)
    }

    /**
     * Fills the form from JSON an assistant returned. Deliberately fills the
     * form rather than saving: the user sees what arrived and can fix it before
     * anything is written, and the photo and source stay untouched either way.
     */
    fun applyJson(text: String): Boolean {
        val recipe = RecipeJsonImporter.parse(text) ?: return false
        pendingJson.value = recipe
        return true
    }

    /** Set when [applyJson] succeeded, so the screen can adopt it once. */
    val pendingJson = MutableStateFlow<StructuredRecipe?>(null)

    fun clearPendingJson() { pendingJson.value = null }

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
                EditRecipeViewModel(
                    repository = app.container.recipeRepository,
                    refiner = RecipeRefiner(app.container.recipeTranslator),
                    recipeId = recipeId,
                )
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
    val refineState by viewModel.refine.collectAsStateWithLifecycle()
    val caption by viewModel.caption.collectAsStateWithLifecycle()
    val pendingJson by viewModel.pendingJson.collectAsStateWithLifecycle()
    var showJsonPaste by remember { mutableStateOf(false) }

    // Adopting the parsed recipe here, rather than inside the view model, keeps
    // the form the single owner of what is on screen.
    LaunchedEffect(pendingJson) {
        pendingJson?.let {
            form.applyDraft(it)
            viewModel.clearPendingJson()
        }
    }

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

    if (showJsonPaste) {
        JsonPasteDialog(
            onDismiss = { showJsonPaste = false },
            onApply = { text -> viewModel.applyJson(text) },
        )
    }

    Column(Modifier.fillMaxSize()) {
        ClayTopBar(
            title = stringResource(R.string.edit_title),
            onMenu = onBack,
            menuIcon = Icons.AutoMirrored.Filled.ArrowBack,
            menuDescription = stringResource(R.string.topbar_back),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (caption != null) {
                item {
                    AiRoundTripCard(
                        caption = caption!!,
                        onPasteJson = { showJsonPaste = true },
                    )
                }
            }
            item {
                val draft = form.toStructuredRecipe()
                RefineRecipeCard(
                    draft = draft,
                    state = refineState,
                    enabled = form.isValid,
                    onRefine = { viewModel.convertAndTranslate(draft, form::applyDraft) },
                )
            }
            item { RecipeIdentityFields(form) }
            item { IngredientsCard(form) }
            item { InstructionsCard(form) }
            item { RecipeMetaFields(form) }
            item { Spacer(Modifier.height(8.dp)) }
        }
        ClayButton(
            text = stringResource(R.string.edit_save),
            icon = Icons.Filled.Save,
            enabled = form.isValid,
            onClick = { viewModel.save(form.toStructuredRecipe(), form.categoryList(), onSaved) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

/**
 * Hand this recipe's original caption to an outside assistant, and take the
 * result back into *this* recipe.
 *
 * The import screen can do the same round trip, but it creates a new recipe —
 * which loses the source link and the imported photo. Here the recipe already
 * exists and only its fields are replaced.
 */
@Composable
private fun AiRoundTripCard(caption: String, onPasteJson: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val language = java.util.Locale.getDefault().getDisplayLanguage(java.util.Locale.ENGLISH)
    val prompt = RecipePrompt.forCaption(caption, language)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLow, cornerRadius = 24.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.edit_ai_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(R.string.edit_ai_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ClayButton(
                text = stringResource(R.string.edit_ai_copy),
                icon = Icons.Filled.AutoAwesome,
                onClick = { clipboard.setText(AnnotatedString(prompt)) },
                container = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            ClayButton(
                text = stringResource(R.string.edit_ai_send),
                icon = Icons.Filled.IosShare,
                onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, prompt)
                    }
                    context.startActivity(
                        android.content.Intent.createChooser(send, context.getString(R.string.edit_ai_send))
                    )
                },
                container = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }
        ClayButton(
            text = stringResource(R.string.edit_ai_paste),
            icon = Icons.Filled.ContentPaste,
            onClick = onPasteJson,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Where the assistant's answer comes back in. */
@Composable
private fun JsonPasteDialog(onDismiss: () -> Unit, onApply: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_ai_paste_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        if (failed) R.string.edit_ai_paste_failed else R.string.edit_ai_paste_body
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (failed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ClayTextField(
                    value = text,
                    onValueChange = { text = it; failed = false },
                    placeholder = stringResource(R.string.edit_ai_paste_placeholder),
                    singleLine = false,
                    minLines = 4,
                    cornerRadius = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                ClayButton(
                    text = stringResource(R.string.action_paste),
                    icon = Icons.Filled.ContentPaste,
                    onClick = { clipboard.getText()?.text?.let { text = it; failed = false } },
                    container = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { if (onApply(text)) onDismiss() else failed = true },
            ) { Text(stringResource(R.string.edit_ai_paste_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
