package com.delizioso.app.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
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
import com.delizioso.app.data.ai.GemmaEngine
import com.delizioso.app.data.local.UserPreferences
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayLabelledField
import com.delizioso.app.ui.components.ClayTopBar
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.Primary
import com.delizioso.app.ui.theme.clayCard
import com.delizioso.app.ui.theme.clayBevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.delizioso.app.R

class ProfileViewModel(
    private val preferences: UserPreferences,
    repository: RecipeRepository,
    private val gemma: GemmaEngine,
) : ViewModel() {

    /** Bytes installed, 0 when absent; -1 while copying. */
    private val _gemmaBytes = MutableStateFlow(gemma.installedSizeBytes())
    val gemmaBytes: StateFlow<Long> = _gemmaBytes.asStateFlow()

    fun installGemma(uri: android.net.Uri) {
        viewModelScope.launch {
            _gemmaBytes.value = -1L
            _gemmaBytes.value = runCatching { gemma.install(uri) }.getOrDefault(0L)
        }
    }

    fun removeGemma() {
        viewModelScope.launch {
            gemma.uninstall()
            _gemmaBytes.value = 0L
        }
    }

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
                ProfileViewModel(app.container.preferences, app.container.recipeRepository, app.container.gemmaEngine)
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
        ClayTopBar(title = stringResource(R.string.profile_title))
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
                            .clayBevel(PillShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(40.dp))
                    }
                    Text(stringResource(R.string.profile_heading), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        if (recipeCount == 1) {
                            stringResource(R.string.profile_recipes_one)
                        } else {
                            stringResource(R.string.profile_recipes_many, recipeCount)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsCard(title = stringResource(R.string.profile_ai_section)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Primary, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(stringResource(R.string.profile_ai_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                stringResource(R.string.profile_ai_desc),
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
                SettingsCard(title = stringResource(R.string.profile_servings_section)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            stringResource(R.string.profile_servings_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        RoundStepper(Icons.Filled.Remove, stringResource(R.string.profile_servings_fewer)) { viewModel.setDefaultServings(servings - 1) }
                        Text("$servings", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                        RoundStepper(Icons.Filled.Add, stringResource(R.string.profile_servings_more)) { viewModel.setDefaultServings(servings + 1) }
                    }
                }
            }

            item {
                val gemmaBytes by viewModel.gemmaBytes.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val modelPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri -> uri?.let(viewModel::installGemma) }

                SettingsCard(title = stringResource(R.string.gemma_card_title)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.gemma_card_body),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            when {
                                gemmaBytes < 0L -> stringResource(R.string.gemma_installing)
                                gemmaBytes > 0L -> stringResource(
                                    R.string.gemma_installed,
                                    android.text.format.Formatter.formatShortFileSize(context, gemmaBytes),
                                )
                                else -> stringResource(R.string.gemma_none)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (gemmaBytes > 0L) Primary else MaterialTheme.colorScheme.onSurface,
                        )
                        if (gemmaBytes == 0L) {
                            Text(
                                stringResource(R.string.gemma_hint),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ClayButton(
                                text = stringResource(
                                    if (gemmaBytes > 0L) R.string.gemma_replace else R.string.gemma_pick
                                ),
                                icon = Icons.Filled.AutoAwesome,
                                enabled = gemmaBytes >= 0L,
                                onClick = { modelPicker.launch(arrayOf("*/*")) },
                                container = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                            if (gemmaBytes > 0L) {
                                ClayButton(
                                    text = stringResource(R.string.gemma_remove),
                                    onClick = viewModel::removeGemma,
                                    container = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsCard(title = stringResource(R.string.profile_yt_section)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.profile_yt_desc),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ClayLabelledField(
                            label = stringResource(R.string.profile_api_key_label),
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
            .clayBevel(PillShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Primary, modifier = Modifier.size(18.dp))
    }
}
