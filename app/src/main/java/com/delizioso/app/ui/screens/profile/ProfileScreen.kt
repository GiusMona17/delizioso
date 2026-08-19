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
import com.delizioso.app.data.backup.BackupManager
import com.delizioso.app.data.local.ThemeMode
import com.delizioso.app.data.local.UserPreferences
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayLabelledField
import com.delizioso.app.ui.components.ClaySegmentedTabs
import com.delizioso.app.ui.components.ClayTopBar
import com.delizioso.app.ui.theme.PillShape
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

/** Outcome of a library backup or restore, reported rather than assumed. */
sealed interface BackupState {
    data object Idle : BackupState
    data object Working : BackupState
    data class Exported(val recipes: Int) : BackupState
    data class Restored(val added: Int, val skipped: Int) : BackupState
    data class Failed(val message: String) : BackupState
}

class ProfileViewModel(
    private val preferences: UserPreferences,
    repository: RecipeRepository,
    private val gemma: GemmaEngine,
    private val backupManager: BackupManager,
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

    val themeMode: StateFlow<ThemeMode> =
        preferences.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { preferences.setThemeMode(mode) }

    val recipeCount: StateFlow<Int> =
        repository.count().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Progress and outcome of the last backup or restore. */
    private val _backup = MutableStateFlow<BackupState>(BackupState.Idle)
    val backup: StateFlow<BackupState> = _backup.asStateFlow()

    fun suggestedBackupName(): String = backupManager.suggestedFileName()

    fun exportLibrary(destination: android.net.Uri) {
        if (_backup.value is BackupState.Working) return
        viewModelScope.launch {
            _backup.value = BackupState.Working
            _backup.value = runCatching { backupManager.exportTo(destination) }
                .fold(
                    onSuccess = { BackupState.Exported(it) },
                    onFailure = { BackupState.Failed(it.message.orEmpty()) },
                )
        }
    }

    fun importLibrary(source: android.net.Uri) {
        if (_backup.value is BackupState.Working) return
        viewModelScope.launch {
            _backup.value = BackupState.Working
            _backup.value = runCatching { backupManager.importFrom(source) }
                .fold(
                    onSuccess = { BackupState.Restored(it.added, it.skipped) },
                    onFailure = { BackupState.Failed(it.message.orEmpty()) },
                )
        }
    }

    fun setAiConsent(value: Boolean) = viewModelScope.launch { preferences.setAiConsent(value) }

    fun setDefaultServings(value: Int) =
        viewModelScope.launch { preferences.setDefaultServings(value.coerceIn(1, 20)) }

    fun setYouTubeApiKey(value: String) = viewModelScope.launch { preferences.setYouTubeApiKey(value.trim()) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                ProfileViewModel(
                    preferences = app.container.preferences,
                    repository = app.container.recipeRepository,
                    gemma = app.container.gemmaEngine,
                    backupManager = app.container.backupManager,
                )
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
    val backupState by viewModel.backup.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

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
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
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
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
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
                            color = if (gemmaBytes > 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
                SettingsCard(title = stringResource(R.string.theme_section)) {
                    // Three states rather than a switch: "follow the phone" is a
                    // real choice, not the absence of one.
                    val modes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
                    ClaySegmentedTabs(
                        options = listOf(
                            stringResource(R.string.theme_system),
                            stringResource(R.string.theme_light),
                            stringResource(R.string.theme_dark),
                        ),
                        selectedIndex = modes.indexOf(themeMode).coerceAtLeast(0),
                        onSelect = { viewModel.setThemeMode(modes[it]) },
                    )
                }
            }

            item {
                SettingsCard(title = stringResource(R.string.backup_section)) {
                    BackupControls(
                        state = backupState,
                        suggestedName = viewModel::suggestedBackupName,
                        onExport = viewModel::exportLibrary,
                        onImport = viewModel::importLibrary,
                    )
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

/**
 * Save the whole library to a file, and put it back.
 *
 * Everything lives on this phone, so a reset or a lost handset takes the
 * collection with it unless there is a copy somewhere else. The system picker
 * chooses where the file goes, which keeps the app out of the user's storage and
 * lets the copy land in Drive, a USB stick or wherever they already trust.
 */
@Composable
private fun BackupControls(
    state: BackupState,
    suggestedName: () -> String,
    onExport: (android.net.Uri) -> Unit,
    onImport: (android.net.Uri) -> Unit,
) {
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(onExport) }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onImport) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.backup_desc),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state) {
            is BackupState.Working -> StatusLine(stringResource(R.string.backup_working))
            is BackupState.Exported -> StatusLine(stringResource(R.string.backup_exported, state.recipes))
            is BackupState.Restored -> StatusLine(
                stringResource(R.string.backup_restored, state.added, state.skipped)
            )
            is BackupState.Failed -> StatusLine(
                stringResource(R.string.backup_failed, state.message),
                color = MaterialTheme.colorScheme.error,
            )
            BackupState.Idle -> Unit
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ClayButton(
                text = stringResource(R.string.backup_export),
                enabled = state !is BackupState.Working,
                onClick = { exportPicker.launch(suggestedName()) },
                modifier = Modifier.weight(1f),
            )
            ClayButton(
                text = stringResource(R.string.backup_import),
                enabled = state !is BackupState.Working,
                // Some file providers hand zips out as octet-stream, so accept both.
                onClick = { importPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                container = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatusLine(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
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
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}
