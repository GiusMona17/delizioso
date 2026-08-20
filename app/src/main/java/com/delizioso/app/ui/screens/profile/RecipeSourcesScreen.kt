package com.delizioso.app.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.delizioso.app.R
import com.delizioso.app.data.import.RecipeSource
import com.delizioso.app.data.import.RecipeSourceCategory
import com.delizioso.app.data.import.displayNameRes
import com.delizioso.app.data.local.UserPreferences
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClaySectionHeader
import com.delizioso.app.ui.components.ClayTopBar
import com.delizioso.app.ui.theme.clayCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecipeSourcesViewModel(private val preferences: UserPreferences) : ViewModel() {

    val enabledSources: StateFlow<Set<RecipeSource>> =
        preferences.enabledSources.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            RecipeSource.defaultActiveSources(),
        )

    fun toggleSource(source: RecipeSource, enabled: Boolean) {
        viewModelScope.launch {
            preferences.setSourceEnabled(source, enabled)
        }
    }

    fun setAll(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAllSourcesEnabled(enabled)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                RecipeSourcesViewModel(
                    preferences = app.container.preferences,
                )
            }
        }
    }
}

@Composable
fun RecipeSourcesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeSourcesViewModel = viewModel(factory = RecipeSourcesViewModel.Factory),
) {
    val enabledSources by viewModel.enabledSources.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        ClayTopBar(
            title = stringResource(R.string.sources_title),
            onMenu = onBack,
            menuIcon = Icons.AutoMirrored.Filled.ArrowBack,
            menuDescription = stringResource(R.string.topbar_back),
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ClayButton(
                        text = stringResource(R.string.sources_enable_all),
                        onClick = { viewModel.setAll(true) },
                        modifier = Modifier.weight(1f),
                        container = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    ClayButton(
                        text = stringResource(R.string.sources_disable_all),
                        onClick = { viewModel.setAll(false) },
                        modifier = Modifier.weight(1f),
                        container = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            RecipeSourceCategory.values().forEach { category ->
                val sources = RecipeSource.values().filter { it.category == category }
                if (sources.isNotEmpty()) {
                    item {
                        ClaySectionHeader(
                            title = stringResource(category.displayNameRes),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(sources, key = { it.id }) { source ->
                        RecipeSourceRow(
                            source = source,
                            enabled = enabledSources.contains(source),
                            onToggle = { enabled -> viewModel.toggleSource(source, enabled) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeSourceRow(
    source: RecipeSource,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLow, cornerRadius = 24.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(source.displayNameRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val subtitle = if (source.domains.isNotEmpty()) {
                source.domains.joinToString(", ")
            } else {
                stringResource(R.string.sources_cat_generic)
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}
