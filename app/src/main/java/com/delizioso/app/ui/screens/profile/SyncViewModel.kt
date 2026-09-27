package com.delizioso.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.delizioso.app.DeliziosoApplication
import com.delizioso.app.data.sync.SyncEngine
import com.delizioso.app.data.sync.SyncOutcome
import com.delizioso.app.data.sync.SyncSettings
import com.delizioso.app.data.sync.SyncSettingsRepository
import java.io.OutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SyncViewModel(
    private val engine: SyncEngine,
    private val settings: SyncSettingsRepository,
) : ViewModel() {

    val state: StateFlow<SyncSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncSettings())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _outcome = MutableStateFlow<SyncOutcome?>(null)
    val outcome: StateFlow<SyncOutcome?> = _outcome.asStateFlow()

    fun save(url: String, token: String) = run {
        settings.setServer(url, token)
        engine.sync()
    }

    fun syncNow() = run { engine.sync() }

    fun keepThisDevice() = run { engine.resolveKeepLocal() }

    fun usePiVersion() = run { engine.resolveUsePi() }

    fun importArchive(bytes: ByteArray) = run { engine.importArchive(bytes.inputStream()) }

    suspend fun exportArchive(output: OutputStream) = engine.exportArchive(output)

    fun dismissOutcome() {
        _outcome.value = null
    }

    private fun run(action: suspend () -> SyncOutcome) {
        viewModelScope.launch {
            _busy.value = true
            _outcome.value = action()
            _busy.value = false
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DeliziosoApplication
                SyncViewModel(app.container.syncEngine, app.container.syncSettings)
            }
        }
    }
}
