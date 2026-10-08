package com.delizioso.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.delizioso.app.DeliziosoApplication
import com.delizioso.app.data.docs.DocsSettings
import com.delizioso.app.data.docs.DocsSettingsRepository
import com.delizioso.app.data.docs.DocsSync
import com.delizioso.app.data.sync.SyncSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DocsStatus {
    data object Off : DocsStatus
    data object NeedsServer : DocsStatus
    data class Failed(val message: String) : DocsStatus
    data class Sent(val at: Long) : DocsStatus
    data object Never : DocsStatus
}

fun docsStatus(enabled: Boolean, configured: Boolean, lastPushedAt: Long?, lastError: String?): DocsStatus = when {
    !enabled -> DocsStatus.Off
    !configured -> DocsStatus.NeedsServer
    lastError != null -> DocsStatus.Failed(lastError)
    lastPushedAt != null -> DocsStatus.Sent(lastPushedAt)
    else -> DocsStatus.Never
}

data class DocsUiState(
    val docs: DocsSettings = DocsSettings(),
    /** A server address and token are set: the documents travel with the same connection as the main sync. */
    val configured: Boolean = false,
)

class DocsViewModel(
    private val sync: DocsSync,
    private val settings: DocsSettingsRepository,
    connection: SyncSettingsRepository,
) : ViewModel() {

    val state: StateFlow<DocsUiState> = combine(settings.settings, connection.settings) { d, c ->
        DocsUiState(d, c.isConfigured)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocsUiState())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Turning it on sends the documents at once. */
    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setEnabled(enabled)
            if (enabled) send()
        }
    }

    fun sendNow() {
        viewModelScope.launch { send() }
    }

    private suspend fun send() {
        _busy.value = true
        try {
            sync.run(force = true)
        } finally {
            _busy.value = false
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                DocsViewModel(app.container.docsSync, app.container.docsSettings, app.container.syncSettings)
            }
        }
    }
}
