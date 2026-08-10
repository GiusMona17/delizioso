package com.delizioso.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userDataStore by preferencesDataStore(name = "user_prefs")

/** User preferences persisted in DataStore (lightweight, no Room needed). */
class UserPreferences(private val context: Context) {

    private object Keys {
        val AI_CONSENT = booleanPreferencesKey("ai_consent_given")
        val FIRST_RUN_COMPLETE = booleanPreferencesKey("first_run_complete")
        val DEFAULT_SERVINGS = intPreferencesKey("default_servings")
        val YOUTUBE_API_KEY = stringPreferencesKey("youtube_api_key")
        val GROCERY_CHECKED = stringSetPreferencesKey("grocery_checked")
        val GROCERY_CUSTOM = stringSetPreferencesKey("grocery_custom_items")
    }

    /** Whether the user accepted the on-device AI (Gemini Nano) terms. */
    val aiConsentGiven: Flow<Boolean> =
        context.userDataStore.data.map { it[Keys.AI_CONSENT] ?: false }

    val firstRunComplete: Flow<Boolean> =
        context.userDataStore.data.map { it[Keys.FIRST_RUN_COMPLETE] ?: false }

    /** Default servings used when scaling/planning. */
    val defaultServings: Flow<Int> =
        context.userDataStore.data.map { it[Keys.DEFAULT_SERVINGS] ?: 2 }

    /** YouTube Data API v3 key (personal-use; may be left blank until configured). */
    val youTubeApiKey: Flow<String> =
        context.userDataStore.data.map { it[Keys.YOUTUBE_API_KEY] ?: "" }

    suspend fun setAiConsent(given: Boolean) {
        context.userDataStore.edit { it[Keys.AI_CONSENT] = given }
    }

    suspend fun markFirstRunComplete() {
        context.userDataStore.edit { it[Keys.FIRST_RUN_COMPLETE] = true }
    }

    suspend fun setDefaultServings(servings: Int) {
        context.userDataStore.edit { it[Keys.DEFAULT_SERVINGS] = servings }
    }

    suspend fun setYouTubeApiKey(key: String) {
        context.userDataStore.edit { it[Keys.YOUTUBE_API_KEY] = key }
    }

    // ---- Grocery list state (survives navigation and restarts) ----

    /** Grocery lines the user has ticked off. */
    val groceryChecked: Flow<Set<String>> =
        context.userDataStore.data.map { it[Keys.GROCERY_CHECKED] ?: emptySet() }

    /** Extra lines the user typed in by hand. */
    val groceryCustomItems: Flow<Set<String>> =
        context.userDataStore.data.map { it[Keys.GROCERY_CUSTOM] ?: emptySet() }

    suspend fun toggleGroceryChecked(line: String) {
        context.userDataStore.edit { prefs ->
            val current = prefs[Keys.GROCERY_CHECKED] ?: emptySet()
            prefs[Keys.GROCERY_CHECKED] = if (line in current) current - line else current + line
        }
    }

    suspend fun clearGroceryChecked() {
        context.userDataStore.edit { it[Keys.GROCERY_CHECKED] = emptySet() }
    }

    suspend fun addGroceryCustomItem(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        context.userDataStore.edit { prefs ->
            prefs[Keys.GROCERY_CUSTOM] = (prefs[Keys.GROCERY_CUSTOM] ?: emptySet()) + trimmed
        }
    }

    suspend fun removeGroceryCustomItem(line: String) {
        context.userDataStore.edit { prefs ->
            prefs[Keys.GROCERY_CUSTOM] = (prefs[Keys.GROCERY_CUSTOM] ?: emptySet()) - line
            prefs[Keys.GROCERY_CHECKED] = (prefs[Keys.GROCERY_CHECKED] ?: emptySet()) - line
        }
    }
}
