package com.delizioso.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.delizioso.app.data.local.UserPreferences
import com.delizioso.app.ui.components.ClayLabelledField
import com.delizioso.app.ui.components.ClayTopBar
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.Primary
import com.delizioso.app.ui.theme.clayCard
import com.delizioso.app.ui.theme.clayInner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val preferences: UserPreferences,
    repository: RecipeRepository,
) : ViewModel() {

    val aiConsent: StateFlow<Boolean> =
        preferences.aiConsentGiven.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val defaultServings: StateFlow<Int> =
        preferences.defaultServings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 2)

    val youTubeApiKey: StateFlow<String> =
        preferences.youTubeApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val recipeCount: StateFlow<Int> =
        repository.count().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setAiConsent(value: Boolean) = viewModelScope.launch { preferences.setAiConsent(value) }

    fun setDefaultServings(value: Int) =
        viewModelScope.launch { preferences.setDefaultServings(value.coerceIn(1, 20)) }

    fun setYouTubeApiKey(value: String) = viewModelScope.launch { preferences.setYouTubeApiKey(value.trim()) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                ProfileViewModel(app.container.preferences, app.container.recipeRepository)
            }
        }
    }
}

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val aiConsent by viewModel.aiConsent.collectAsStateWithLifecycle()
    val servings by viewModel.defaultServings.collectAsStateWithLifecycle()
    val storedKey by viewModel.youTubeApiKey.collectAsStateWithLifecycle()
    val recipeCount by viewModel.recipeCount.collectAsStateWithLifecycle()

    var apiKey by rememberSaveable { mutableStateOf("") }
    var apiKeyLoaded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(storedKey) {
        if (!apiKeyLoaded) {
            apiKey = storedKey
            apiKeyLoaded = true
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ClayTopBar(title = "Profile")
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLow, cornerRadius = 28.dp)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clayInner(PillShape, cornerRadius = null),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(40.dp))
                    }
                    Text("Your kitchen", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        if (recipeCount == 1) "1 recipe saved on this device" else "$recipeCount recipes saved on this device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsCard(title = "On-device AI") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Primary, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text("Gemini Nano", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "Structures imported captions and estimates macros. Runs entirely on this phone.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = aiConsent,
                            onCheckedChange = viewModel::setAiConsent,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = Primary,
                            ),
                        )
                    }
                }
            }

            item {
                SettingsCard(title = "Default servings") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            "Used when planning a meal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        RoundStepper(Icons.Filled.Remove, "Fewer servings") { viewModel.setDefaultServings(servings - 1) }
                        Text("$servings", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                        RoundStepper(Icons.Filled.Add, "More servings") { viewModel.setDefaultServings(servings + 1) }
                    }
                }
            }

            item {
                SettingsCard(title = "YouTube Data API key") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Needed to read recipe descriptions from YouTube videos. Stored only on this device.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ClayLabelledField(
                            label = "API key",
                            value = apiKey,
                            onValueChange = {
                                apiKey = it
                                viewModel.setYouTubeApiKey(it)
                            },
                            placeholder = "AIza…",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLow, cornerRadius = 28.dp)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        content()
    }
}

@Composable
private fun RoundStepper(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clayInner(PillShape, cornerRadius = null)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Primary, modifier = Modifier.size(18.dp))
    }
}
