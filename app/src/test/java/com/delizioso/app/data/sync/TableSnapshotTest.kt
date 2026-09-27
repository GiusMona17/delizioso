package com.delizioso.app.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.room.RoomDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.delizioso.app.PlainTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(application = PlainTestApplication::class)
class TableSnapshotTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class Dev(val codec: TableSnapshot, val filesDir: File, val db: RoomDatabase, val prefs: DataStore<Preferences>)

    private fun device(name: String): Dev {
        val filesDir = tmp.newFolder(name)
        val db = newDeliziosoDb()
        val prefs = newPrefs(File(tmp.root, "$name.preferences_pb"))
        val codec = TableSnapshot(db, DeliziosoSyncConfig, filesDir, FileHashCache(File(tmp.root, "$name-hashes.json")), prefs)
        return Dev(codec, filesDir, db, prefs)
    }

    private fun columnsOf(db: RoomDatabase, table: String): Set<String> =
        db.openHelper.readableDatabase.query("PRAGMA table_info(`$table`)").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(1)) }
        }

    @Test
    fun `restore on another device reproduces every row, column, recipe photo and diary photo`() {
        val a = device("a")
        seedEveryColumn(a.db, DeliziosoSyncConfig, a.filesDir, "a")
        val fromA = a.codec.export()

        val b = device("b")
        seedEveryColumn(b.db, DeliziosoSyncConfig, b.filesDir, "b")
        b.codec.restore(fromA.envelope, fromA.filesBySha)

        assertEquals(fromA.envelope.toString(), b.codec.export().envelope.toString())
        assertEquals("bytes of recipes.imageUri a", File(b.filesDir, "recipes/imageUri-a.jpg").readText())
        assertEquals("bytes of cooking_logs.photoUri a", File(b.filesDir, "cooking_logs/photoUri-a.jpg").readText())
        assertFalse(File(b.filesDir, "recipes/imageUri-b.jpg").exists())
    }

    @Test
    fun `a recipe image that is a web URL stays a URL and uploads nothing`() {
        val a = device("a")
        seedEveryColumn(a.db, DeliziosoSyncConfig, a.filesDir, "a")
        a.db.openHelper.writableDatabase.execSQL("UPDATE recipes SET imageUri = 'https://example.com/pasta.jpg'")
        val snapshot = a.codec.export()
        assertTrue(snapshot.envelope.toString().replace("\\/", "/").contains("https://example.com/pasta.jpg"))
        assertEquals(1, snapshot.filesBySha.size) // only the diary photo
    }

    @Test
    fun `restoring an empty snapshot deletes everything local`() {
        val empty = device("empty")
        val b = device("b")
        seedEveryColumn(b.db, DeliziosoSyncConfig, b.filesDir, "b")
        b.codec.restore(empty.codec.export().envelope, emptyMap())
        assertTrue(b.codec.isEmpty())
    }

    @Test
    fun `export is deterministic`() {
        val a = device("a")
        seedEveryColumn(a.db, DeliziosoSyncConfig, a.filesDir, "a")
        assertEquals(a.codec.export().hash, a.codec.export().hash)
    }

    @Test
    fun `the config covers the schema exactly`() {
        val a = device("a")
        val all = a.db.openHelper.readableDatabase
            .query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            } - setOf("android_metadata", "room_master_table", "sqlite_sequence")
        assertEquals(
            "a new table must be added to DeliziosoSyncConfig.tables or .unsyncedTables",
            all,
            DeliziosoSyncConfig.tables.toSet() + DeliziosoSyncConfig.unsyncedTables,
        )
        DeliziosoSyncConfig.fileColumns.forEach { (table, columns) ->
            assertTrue("$table lacks one of $columns", columnsOf(a.db, table).containsAll(columns))
        }
    }

    @Test
    fun `grocery list and nutrition targets travel, device settings stay`() {
        val a = device("a")
        runBlocking {
            a.prefs.edit {
                it[stringSetPreferencesKey("grocery_checked")] = setOf("2 eggs", "milk")
                it[stringSetPreferencesKey("grocery_custom_items")] = setOf("bread")
                it[intPreferencesKey("target_calories_kcal")] = 2100
                it[intPreferencesKey("default_servings")] = 3
                it[stringPreferencesKey("youtube_api_key")] = "AIza-secret"
                it[stringPreferencesKey("theme_mode")] = "DARK"
            }
        }
        val snapshot = a.codec.export()
        assertFalse("a key never leaves the phone", snapshot.envelope.toString().contains("AIza-secret"))

        val b = device("b")
        runBlocking { b.prefs.edit { it[stringPreferencesKey("theme_mode")] = "LIGHT" } }
        b.codec.restore(snapshot.envelope, snapshot.filesBySha)

        val stored = runBlocking { b.prefs.data.first() }
        assertEquals(setOf("2 eggs", "milk"), stored[stringSetPreferencesKey("grocery_checked")])
        assertEquals(setOf("bread"), stored[stringSetPreferencesKey("grocery_custom_items")])
        assertEquals(2100, stored[intPreferencesKey("target_calories_kcal")])
        assertEquals(3, stored[intPreferencesKey("default_servings")])
        assertEquals("LIGHT", stored[stringPreferencesKey("theme_mode")])
        assertEquals(null, stored[stringPreferencesKey("youtube_api_key")])
    }

    @Test
    fun `a path escaping filesDir is rejected`() {
        val a = device("a")
        seedEveryColumn(a.db, DeliziosoSyncConfig, a.filesDir, "a")
        val snapshot = a.codec.export()
        val envelope = JSONObject(snapshot.envelope.toString())
        envelope.getJSONObject("data").getJSONObject("files").put("../evil.jpg", snapshot.filesBySha.keys.first())
        val b = device("b")
        assertThrows(IllegalArgumentException::class.java) { b.codec.restore(envelope, snapshot.filesBySha) }
    }
}
