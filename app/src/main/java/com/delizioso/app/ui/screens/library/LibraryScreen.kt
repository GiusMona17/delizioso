package com.delizioso.app.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.delizioso.app.data.RecipeRepository
import com.delizioso.app.data.local.LibraryViewMode
import com.delizioso.app.data.local.UserPreferences
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.ui.components.ClayEmptyState
import com.delizioso.app.ui.components.ClayFilterChip
import com.delizioso.app.ui.components.ClayRecipeCard
import com.delizioso.app.ui.components.ClayTextField
import com.delizioso.app.ui.components.ClayTopBar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.delizioso.app.R
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import com.delizioso.app.ui.components.ClayRecipeRow
import com.delizioso.app.ui.components.ClayRecipeTile
import com.delizioso.app.ui.theme.PillShape
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable

/** "All" plus the pinned Favourites filter, plus every category in use. */
private const val FILTER_ALL = "All"
private const val FILTER_FAVOURITES = "Favourites"

class LibraryViewModel(
    private val repository: RecipeRepository,
    private val preferences: UserPreferences,
) : ViewModel() {

    val recipes: StateFlow<List<RecipeWithDetails>> =
        repository.allWithDetails.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val viewMode: StateFlow<LibraryViewMode> =
        preferences.libraryViewMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryViewMode.CARDS)

    fun setViewMode(mode: LibraryViewMode) = viewModelScope.launch { preferences.setLibraryViewMode(mode) }

    fun toggleFavorite(id: Long, current: Boolean) {
        viewModelScope.launch { repository.setFavorite(id, !current) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                LibraryViewModel(app.container.recipeRepository, app.container.preferences)
            }
        }
    }
}

@Composable
fun LibraryScreen(
    onRecipeClick: (Long) -> Unit,
    onCreateClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }

    val filters = buildList {
        add(FILTER_ALL)
        if (recipes.any { it.recipe.isFavorite }) add(FILTER_FAVOURITES)
        // Ordered as the vocabulary declares them, not alphabetically, so the rail
        // keeps a stable, meal-order-ish shape as the library grows.
        addAll(
            recipes.flatMap { d -> d.tags.map { it.name } }
                .distinct()
                .sortedBy { Categories.ALL.indexOf(it).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
        )
    }
    // A filter can disappear when its last recipe is deleted — fall back to "All".
    val activeFilter = if (filter in filters) filter else FILTER_ALL

    val visible = recipes
        .filter { query.isBlank() || it.matches(query) }
        .filter {
            when (activeFilter) {
                FILTER_ALL -> true
                FILTER_FAVOURITES -> it.recipe.isFavorite
                else -> it.tags.any { tag -> tag.name == filter }
            }
        }

    Column(modifier = modifier.fillMaxSize()) {
        ClayTopBar(
            onMenu = onCreateClick,
            menuIcon = Icons.Filled.Add,
            menuDescription = stringResource(R.string.library_create_menu),
            onProfile = onProfileClick,
        )
        // One grid drives all three layouts: cards and list are simply a
        // single-column grid, which keeps the search field and filter rail in one
        // place instead of repeated per layout.
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (viewMode == LibraryViewMode.GRID) 2 else 1),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (viewMode == LibraryViewMode.CARDS) 20.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ClayTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.library_search),
                    leadingIcon = Icons.Filled.Search,
                    cornerRadius = 24.dp,
                    modifier = Modifier.fillMaxWidth(),
                    // The slot used to hold a decorative, dead "tune" icon; it now
                    // switches the layout, which is what a control there implies.
                    trailing = {
                        ViewModePicker(current = viewMode, onSelect = viewModel::setViewMode)
                    },
                )
            }
            if (recipes.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(filters, key = { it }) { name ->
                            ClayFilterChip(
                                text = when (name) {
                                    FILTER_ALL -> stringResource(R.string.library_filter_all)
                                    FILTER_FAVOURITES -> stringResource(R.string.library_filter_favs)
                                    else -> stringResource(Categories.displayNameRes(name))
                                },
                                selected = activeFilter == name,
                                onClick = { filter = name },
                            )
                        }
                    }
                }
            }
            if (visible.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ClayEmptyState(
                        icon = Icons.Filled.Restaurant,
                        title = if (recipes.isEmpty()) stringResource(R.string.library_no_recipes) else stringResource(R.string.library_no_matches),
                        subtitle = if (recipes.isEmpty()) {
                            stringResource(R.string.library_empty_hint)
                        } else {
                            stringResource(R.string.library_no_match_hint)
                        },
                    )
                }
            } else {
                items(visible, key = { it.recipe.id }) { details ->
                    val open = { onRecipeClick(details.recipe.id) }
                    val fav = { viewModel.toggleFavorite(details.recipe.id, details.recipe.isFavorite) }
                    when (viewMode) {
                        LibraryViewMode.CARDS ->
                            ClayRecipeCard(details = details, onClick = open, onToggleFavorite = fav)
                        LibraryViewMode.GRID ->
                            ClayRecipeTile(details = details, onClick = open, onToggleFavorite = fav)
                        LibraryViewMode.LIST ->
                            ClayRecipeRow(details = details, onClick = open, onToggleFavorite = fav)
                    }
                }
            }
        }
    }
}

/** Title, description or any ingredient name matches the query. */
private fun RecipeWithDetails.matches(query: String): Boolean {
    val q = query.trim()
    return recipe.title.contains(q, ignoreCase = true) ||
        recipe.description?.contains(q, ignoreCase = true) == true ||
        ingredients.any { it.name.contains(q, ignoreCase = true) }
}

/**
 * Switches the library between its three layouts.
 *
 * Shown as the current layout's own icon, so the control says what you are
 * looking at as well as what you can change it to.
 */
@Composable
private fun ViewModePicker(current: LibraryViewMode, onSelect: (LibraryViewMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Icon(
            imageVector = when (current) {
                LibraryViewMode.CARDS -> Icons.Filled.ViewAgenda
                LibraryViewMode.GRID -> Icons.Filled.GridView
                LibraryViewMode.LIST -> Icons.AutoMirrored.Filled.ViewList
            },
            contentDescription = stringResource(R.string.library_view_mode),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(end = 6.dp)
                .size(40.dp)
                .clip(PillShape)
                .clickable { open = true }
                .padding(9.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ViewModeItem(Icons.Filled.ViewAgenda, R.string.library_view_cards) {
                onSelect(LibraryViewMode.CARDS); open = false
            }
            ViewModeItem(Icons.Filled.GridView, R.string.library_view_grid) {
                onSelect(LibraryViewMode.GRID); open = false
            }
            ViewModeItem(Icons.AutoMirrored.Filled.ViewList, R.string.library_view_list) {
                onSelect(LibraryViewMode.LIST); open = false
            }
        }
    }
}

@Composable
private fun ViewModeItem(icon: ImageVector, label: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        text = { Text(stringResource(label)) },
        onClick = onClick,
    )
}
