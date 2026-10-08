package com.delizioso.app.data.docs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class DocsSettings(
    /** The switch in Profile: off until the owner turns it on. */
    val enabled: Boolean = false,
    /** What the last delivery contained (see [DocsPusher.contentHash]); the same content is not sent again. */
    val lastHash: String? = null,
    val lastPushedAt: Long? = null,
    val lastError: String? = null,
)

/** Device only, like the rest of the sync state: none of these keys is part of a snapshot. */
class DocsSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<DocsSettings> = dataStore.data.map { p ->
        DocsSettings(
            enabled = p[ENABLED] ?: false,
            lastHash = p[LAST_HASH],
            lastPushedAt = p[LAST_PUSHED_AT],
            lastError = p[LAST_ERROR],
        )
    }

    suspend fun current(): DocsSettings = settings.first()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED] = enabled }
    }

    suspend fun recordPushed(hash: String, at: Long) {
        dataStore.edit {
            it[LAST_HASH] = hash
            it[LAST_PUSHED_AT] = at
            it.remove(LAST_ERROR)
        }
    }

    suspend fun recordError(message: String) {
        dataStore.edit { it[LAST_ERROR] = message }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("docs_enabled")
        val LAST_HASH = stringPreferencesKey("docs_last_hash")
        val LAST_PUSHED_AT = longPreferencesKey("docs_last_pushed_at")
        val LAST_ERROR = stringPreferencesKey("docs_last_error")
    }
}
