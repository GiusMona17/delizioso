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

    /**
     * Lines added outside the meal planner — typed by hand, or copied off a recipe.
     * Stored as "linesource" so the shopping list can still group by recipe
     * without needing a table of its own.
     */
    val groceryCustomItems: Flow<List<CustomGroceryLine>> =
        context.userDataStore.data.map { prefs ->
            (prefs[Keys.GROCERY_CUSTOM] ?: emptySet()).map(CustomGroceryLine::parse)
        }

    suspend fun toggleGroceryChecked(line: String) {
        context.userDataStore.edit { prefs ->
            val current = prefs[Keys.GROCERY_CHECKED] ?: emptySet()
            prefs[Keys.GROCERY_CHECKED] = if (line in current) current - line else current + line
        }
    }

    suspend fun clearGroceryChecked() {
        context.userDataStore.edit { it[Keys.GROCERY_CHECKED] = emptySet() }
    }

    /** [source] names the recipe the line came from; null means the user typed it. */
    suspend fun addGroceryCustomItem(line: String, source: String? = null) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        context.userDataStore.edit { prefs ->
            val current = prefs[Keys.GROCERY_CUSTOM] ?: emptySet()
            // Re-adding the same line from the same recipe shouldn't duplicate it.
            val withoutDuplicate = current.filterNot { CustomGroceryLine.parse(it).line == trimmed }.toSet()
            prefs[Keys.GROCERY_CUSTOM] = withoutDuplicate + CustomGroceryLine(trimmed, source).encode()
        }
    }

    suspend fun removeGroceryCustomItem(line: String) {
        context.userDataStore.edit { prefs ->
            prefs[Keys.GROCERY_CUSTOM] = (prefs[Keys.GROCERY_CUSTOM] ?: emptySet())
                .filterNot { CustomGroceryLine.parse(it).line == line }
                .toSet()
            prefs[Keys.GROCERY_CHECKED] = (prefs[Keys.GROCERY_CHECKED] ?: emptySet()) - line
        }
    }
}

/** A shopping-list line that didn't come from the meal planner. */
data class CustomGroceryLine(val line: String, val source: String?) {

    fun encode(): String = if (source.isNullOrBlank()) line else "$line$SEPARATOR$source"

    companion object {
        /** ASCII unit separator — will never appear in an ingredient name. */
        private const val SEPARATOR = ''

        fun parse(stored: String): CustomGroceryLine {
            val at = stored.indexOf(SEPARATOR)
            return if (at < 0) {
                CustomGroceryLine(stored, null)
            } else {
                CustomGroceryLine(stored.substring(0, at), stored.substring(at + 1).ifBlank { null })
            }
        }
    }
}
