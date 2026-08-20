package com.delizioso.app.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delizioso.app.R
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayChip
import com.delizioso.app.ui.components.ClayEmptyState
import com.delizioso.app.ui.components.ClaySegmentedTabs
import com.delizioso.app.ui.components.ClayTextField
import com.delizioso.app.ui.components.ClayTopBar
import com.delizioso.app.ui.components.RecipeImage
import com.delizioso.app.ui.screens.import.ImportViewModel
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.clayCard

private const val TAB_NAME = 0

/** Suggestions shown while typing an ingredient; the catalogue holds 992 names. */
private const val MAX_SUGGESTIONS = 8

/**
 * TheMealDB, searched by name or by ingredients.
 *
 * A chosen result is handed to [ImportViewModel] — the same instance the preview
 * reads — and this screen then opens the preview itself, because the Import
 * screen's own effect is no longer composed once this one is on top.
 */
@Composable
fun OnlineSearchScreen(
    importViewModel: ImportViewModel,
    onBack: () -> Unit,
    onPreview: () -> Unit,
    viewModel: OnlineSearchViewModel = viewModel(factory = OnlineSearchViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ingredientNames by viewModel.ingredientNames.collectAsStateWithLifecycle()
    val chosen by viewModel.chosenIngredients.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(TAB_NAME) }
    var nameQuery by rememberSaveable { mutableStateOf("") }
    var ingredientQuery by rememberSaveable { mutableStateOf("") }

    val onRetry: () -> Unit = { viewModel.retry() }

    Column(Modifier.fillMaxSize()) {
        ClayTopBar(
            title = stringResource(R.string.search_title),
            onMenu = onBack,
            menuIcon = Icons.AutoMirrored.Filled.ArrowBack,
            menuDescription = stringResource(R.string.topbar_back),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ClaySegmentedTabs(
                options = listOf(
                    stringResource(R.string.search_by_name),
                    stringResource(R.string.search_by_ingredient),
                ),
                selectedIndex = tab,
                onSelect = { tab = it },
            )
            if (tab == TAB_NAME) {
                ClayTextField(
                    value = nameQuery,
                    onValueChange = { nameQuery = it },
                    placeholder = stringResource(R.string.search_name_placeholder),
                    leadingIcon = Icons.Filled.Search,
                    cornerRadius = 24.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                ClayButton(
                    text = stringResource(R.string.search_action),
                    icon = Icons.Filled.Search,
                    onClick = { viewModel.searchByName(nameQuery) },
                    enabled = nameQuery.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                IngredientPicker(
                    query = ingredientQuery,
                    onQueryChange = { ingredientQuery = it },
                    names = ingredientNames,
                    chosen = chosen,
                    onAdd = {
                        viewModel.addIngredient(it)
                        ingredientQuery = ""
                    },
                    onRemove = viewModel::removeIngredient,
                )
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (val s = state) {
                is SearchUiState.Idle -> Unit
                is SearchUiState.Loading -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp),
                )
                is SearchUiState.Results -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(s.results, key = { it.id }) { result ->
                        SearchResultTile(
                            title = result.title,
                            thumbnailUrl = result.thumbnailUrl,
                            onClick = {
                                viewModel.openResult(result.id) { recipe, raw ->
                                    importViewModel.importSearchResult(recipe, raw)
                                    onPreview()
                                }
                            },
                        )
                    }
                }
                is SearchUiState.Empty -> ClayEmptyState(
                    icon = Icons.Filled.SearchOff,
                    title = stringResource(R.string.library_no_matches),
                    subtitle = if (s.ingredients.isEmpty()) {
                        stringResource(R.string.search_empty_name)
                    } else {
                        stringResource(R.string.search_empty_ingredients, s.ingredients.joinToString(", "))
                    },
                )
                is SearchUiState.Failed -> FailureCard(message = s.message, onRetry = onRetry)
            }
        }
    }
}

/** Free text, filtered suggestions, and the ingredients already chosen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IngredientPicker(
    query: String,
    onQueryChange: (String) -> Unit,
    names: List<String>,
    chosen: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val suggestions = if (query.isBlank()) {
        emptyList()
    } else {
        names.filter { it.contains(query, ignoreCase = true) && it !in chosen }.take(MAX_SUGGESTIONS)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ClayTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.search_ingredient_placeholder),
            leadingIcon = Icons.Filled.Search,
            cornerRadius = 24.dp,
            modifier = Modifier.fillMaxWidth(),
        )
        if (names.isEmpty()) {
            // No suggestions to offer, so the typed name is searched as it stands.
            Text(
                stringResource(R.string.search_no_ingredient_list),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ClayButton(
                text = stringResource(R.string.search_action),
                icon = Icons.Filled.Search,
                onClick = { onAdd(query.trim()) },
                enabled = query.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (suggestions.isNotEmpty() || chosen.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (suggestions.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        suggestions.forEach { name ->
                            ClayChip(
                                text = name,
                                icon = Icons.Filled.Add,
                                modifier = Modifier.clip(PillShape).clickable { onAdd(name) },
                            )
                        }
                    }
                }
                if (chosen.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        chosen.forEach { name ->
                            ClayChip(
                                text = name,
                                icon = Icons.Filled.Close,
                                container = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.clip(PillShape).clickable { onRemove(name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One search hit.
 *
 * Deliberately not `ClayRecipeTile`: that one binds to a stored recipe, and a
 * search result has no database row until the user saves it.
 */
@Composable
fun SearchResultTile(
    title: String,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLow, cornerRadius = 24.dp)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            RecipeImage(thumbnailUrl, placeholderIconSize = 28.dp, modifier = Modifier.fillMaxSize())
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Same shape as the Import screen's error card, with the search's own wording. */
@Composable
private fun FailureCard(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clayCard(container = MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.import_error_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            // A lookup that came back empty has no message of its own — say what
            // happened rather than trailing a colon after nothing.
            if (message.isNotBlank()) {
                stringResource(R.string.search_failed, message)
            } else {
                stringResource(R.string.search_failed_unknown)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        ClayButton(
            text = stringResource(R.string.detail_retry),
            onClick = onRetry,
            container = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
