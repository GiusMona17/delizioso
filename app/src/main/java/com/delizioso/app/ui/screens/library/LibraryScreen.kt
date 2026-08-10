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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.delizioso.app.ui.components.ClayEmptyState
import com.delizioso.app.ui.components.ClayFilterChip
import com.delizioso.app.ui.components.ClayRecipeCard
import com.delizioso.app.ui.components.ClayTextField
import com.delizioso.app.ui.components.ClayTopBar
import com.delizioso.app.ui.theme.Primary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "All" plus every tag in the library, plus the pinned Favourites filter. */
private const val FILTER_ALL = "All"
private const val FILTER_FAVOURITES = "Favourites"

class LibraryViewModel(private val repository: RecipeRepository) : ViewModel() {

    val recipes: StateFlow<List<RecipeWithDetails>> =
        repository.allWithDetails.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleFavorite(id: Long, current: Boolean) {
        viewModelScope.launch { repository.setFavorite(id, !current) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                LibraryViewModel(app.container.recipeRepository)
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
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }

    val filters = buildList {
        add(FILTER_ALL)
        if (recipes.any { it.recipe.isFavorite }) add(FILTER_FAVOURITES)
        addAll(recipes.flatMap { d -> d.tags.map { it.name } }.distinct().sorted())
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
            menuDescription = "Create a recipe",
            onProfile = onProfileClick,
        )
        LazyColumn(
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ClayTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search recipes, ingredients…",
                    leadingIcon = Icons.Filled.Search,
                    cornerRadius = 24.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    trailing = {
                        Icon(
                            Icons.Filled.Tune,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.padding(end = 10.dp).size(22.dp),
                        )
                    },
                )
            }
            if (recipes.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(filters, key = { it }) { name ->
                            ClayFilterChip(text = name, selected = activeFilter == name, onClick = { filter = name })
                        }
                    }
                }
            }
            if (visible.isEmpty()) {
                item {
                    ClayEmptyState(
                        icon = Icons.Filled.Restaurant,
                        title = if (recipes.isEmpty()) "No recipes yet" else "No matches",
                        subtitle = if (recipes.isEmpty()) {
                            "Import from Instagram, TikTok or a blog — or create one by hand."
                        } else {
                            "Nothing here matches your search and filters."
                        },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            } else {
                items(visible, key = { it.recipe.id }) { details ->
                    ClayRecipeCard(
                        details = details,
                        onClick = { onRecipeClick(details.recipe.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(details.recipe.id, details.recipe.isFavorite) },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
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
