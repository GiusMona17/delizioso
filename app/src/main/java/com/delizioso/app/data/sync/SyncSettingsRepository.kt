package com.delizioso.app.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class SyncSettings(
    val serverUrl: String = DEFAULT_SERVER_URL,
    val token: String = "",
    /** The Pi version this device last pushed or pulled. */
    val baseEtag: String? = null,
    /** sha256 of the local snapshot at that moment: a different hash now means local changes. */
    val lastPushedHash: String? = null,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
    /** Both sides changed; sync is paused until the user picks one. */
    val conflict: Boolean = false,
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank() && token.isNotBlank()

    companion object {
        const val DEFAULT_SERVER_URL = "https://raspberrypi4.tail947589.ts.net:8700"
    }
}

/** Sync state lives on the device only: it is never part of a snapshot. */
class SyncSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<SyncSettings> = dataStore.data.map { p ->
        SyncSettings(
            serverUrl = p[SERVER_URL] ?: SyncSettings.DEFAULT_SERVER_URL,
            token = p[TOKEN] ?: "",
            baseEtag = p[BASE_ETAG],
            lastPushedHash = p[LAST_PUSHED_HASH],
            lastSyncAt = p[LAST_SYNC_AT],
            lastError = p[LAST_ERROR],
            conflict = p[CONFLICT] ?: false,
        )
    }

    suspend fun current(): SyncSettings = settings.first()

    suspend fun setServer(url: String, token: String) {
        dataStore.edit {
            it[SERVER_URL] = url.trim()
            it[TOKEN] = token.trim()
        }
    }

    suspend fun recordSynced(etag: String, hash: String, at: Long) {
        dataStore.edit {
            it[BASE_ETAG] = etag
            it[LAST_PUSHED_HASH] = hash
            it[LAST_SYNC_AT] = at
            it[CONFLICT] = false
            it.remove(LAST_ERROR)
        }
    }

    suspend fun recordChecked(at: Long) {
        dataStore.edit {
            it[LAST_SYNC_AT] = at
            it.remove(LAST_ERROR)
        }
    }

    suspend fun recordError(message: String) {
        dataStore.edit { it[LAST_ERROR] = message }
    }

    suspend fun setConflict(conflict: Boolean) {
        dataStore.edit { it[CONFLICT] = conflict }
    }

    private companion object {
        val SERVER_URL = stringPreferencesKey("snapsync_server_url")
        val TOKEN = stringPreferencesKey("snapsync_token")
        val BASE_ETAG = stringPreferencesKey("snapsync_base_etag")
        val LAST_PUSHED_HASH = stringPreferencesKey("snapsync_last_pushed_hash")
        val LAST_SYNC_AT = longPreferencesKey("snapsync_last_sync_at")
        val LAST_ERROR = stringPreferencesKey("snapsync_last_error")
        val CONFLICT = booleanPreferencesKey("snapsync_conflict")
    }
}