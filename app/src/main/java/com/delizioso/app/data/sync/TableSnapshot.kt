package com.delizioso.app.data.sync

import android.database.Cursor
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** One export of the local data: the envelope the server stores, plus the files it references. */
class LocalSnapshot(
    val envelope: JSONObject,
    /** sha256 -> a local file with that content. */
    val filesBySha: Map<String, File>,
    /** Paths, relative to filesDir, of every file this snapshot references. */
    val relativePaths: Set<String>,
) {
    val bytes: ByteArray by lazy { envelope.toString().toByteArray(Charsets.UTF_8) }
    val hash: String by lazy { sha256Of(bytes) }
}

/**
 * Dumps and restores [SyncConfig.tables] with plain SQL, so every column and primary key is
 * included without per-entity code. Blocking: call from a background dispatcher.
 */
class TableSnapshot(
    private val database: RoomDatabase,
    private val config: SyncConfig,
    private val filesDir: File,
    private val hashCache: FileHashCache,
    private val prefsStore: DataStore<Preferences>? = null,
) {
    init {
        require(prefsStore != null || config.prefKeys.isEmpty()) { "config syncs preferences but no DataStore was given" }
    }

    private val sqlite: SupportSQLiteDatabase get() = database.openHelper.writableDatabase

    /** The Room schema version: a snapshot from a newer schema can't be restored here. */
    val schemaVersion: Int get() = sqlite.version

    fun isEmpty(): Boolean = config.tables.all { table ->
        sqlite.query("SELECT EXISTS(SELECT 1 FROM `$table`)").use { it.moveToFirst(); it.getInt(0) == 0 }
    }

    fun export(): LocalSnapshot {
        val files = sortedMapOf<String, String>()
        val filesBySha = mutableMapOf<String, File>()
        val tables = JSONObject()
        for (table in config.tables) {
            val fileColumns = config.fileColumns[table].orEmpty()
            sqlite.query("SELECT * FROM `$table` ORDER BY rowid").use { cursor ->
                val columns = cursor.columnNames.toList()
                val rows = JSONArray()
                while (cursor.moveToNext()) {
                    val row = JSONArray()
                    columns.forEachIndexed { i, column ->
                        val value = readValue(cursor, i, table, column)
                        row.put(if (column in fileColumns && value is String) exportPath(value, files, filesBySha) else value)
                    }
                    rows.put(row)
                }
                tables.put(table, JSONObject().put("columns", JSONArray(columns)).put("rows", rows))
            }
        }
        val prefs = exportPrefs(files, filesBySha)
        hashCache.save()
        val envelope = JSONObject()
            .put("format", FORMAT)
            .put("schema", schemaVersion)
            .put("blobs", JSONArray(files.values.toSortedSet()))
            .put("data", JSONObject().put("tables", tables).put("files", JSONObject(files.toMap())).put("prefs", prefs))
        return LocalSnapshot(envelope, filesBySha, files.keys.toSet())
    }

    /**
     * Replaces every synced table and synced file with [envelope]'s. [staged] maps every sha256
     * in the envelope to a verified local copy. Files are placed before the database changes,
     * so a failure never leaves rows pointing at files that aren't there.
     */
    fun restore(envelope: JSONObject, staged: Map<String, File>) {
        val data = envelope.getJSONObject("data")
        val files = data.getJSONObject("files").toStringMap()
        val placements = files.map { (relative, sha) ->
            (staged[sha] ?: error("blob $sha for $relative was not staged")) to safeTarget(relative)
        }
        val before = export().relativePaths

        for ((source, target) in placements) {
            target.parentFile?.mkdirs()
            val partial = File(target.path + ".snapsync-part")
            source.copyTo(partial, overwrite = true)
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        database.runInTransaction(Runnable {
            val db = sqlite
            db.execSQL("PRAGMA defer_foreign_keys = ON")
            config.tables.asReversed().forEach { db.execSQL("DELETE FROM `$it`") }
            val tables = data.getJSONObject("tables")
            for (table in config.tables) {
                if (tables.has(table)) insertRows(db, table, tables.getJSONObject(table), files)
            }
        })
        (before - files.keys).forEach { File(filesDir, it).delete() }
        restorePrefs(data.optJSONObject("prefs") ?: JSONObject(), files)
    }

    private fun readValue(cursor: Cursor, i: Int, table: String, column: String): Any =
        when (cursor.getType(i)) {
            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
            Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
            else -> error("$table.$column holds a BLOB; TableSnapshot doesn't sync BLOB columns")
        }

    /** An existing file under filesDir becomes its relative path (and is recorded); anything else is kept as-is. */
    private fun exportPath(value: String, files: MutableMap<String, String>, filesBySha: MutableMap<String, File>): String {
        val file = File(value)
        val root = filesDir.canonicalFile
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return value
        if (!canonical.path.startsWith(root.path + File.separator) || !canonical.isFile) return value
        val relative = canonical.relativeTo(root).invariantSeparatorsPath
        val sha = hashCache.sha256(canonical)
        files[relative] = sha
        filesBySha[sha] = canonical
        return relative
    }

    private fun safeTarget(relative: String): File {
        val root = filesDir.canonicalFile
        val target = File(root, relative).canonicalFile
        require(target.path.startsWith(root.path + File.separator)) { "unsafe path in snapshot: $relative" }
        return target
    }

    private fun insertRows(db: SupportSQLiteDatabase, table: String, snapshot: JSONObject, files: Map<String, String>) {
        val local = db.query("PRAGMA table_info(`$table`)").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(1)) }
        }
        val columns = snapshot.getJSONArray("columns").toStringList()
        val kept = columns.indices.filter { columns[it] in local } // an older snapshot may lack new columns
        if (kept.isEmpty()) return
        val fileColumns = config.fileColumns[table].orEmpty()
        val statement = db.compileStatement(
            "INSERT INTO `$table` (${kept.joinToString { "`${columns[it]}`" }}) VALUES (${kept.joinToString { "?" }})"
        )
        val rows = snapshot.getJSONArray("rows")
        for (r in 0 until rows.length()) {
            val row = rows.getJSONArray(r)
            kept.forEachIndexed { slot, i ->
                val value = row.get(i)
                val bound = if (columns[i] in fileColumns && value is String && value in files) {
                    File(filesDir, value).absolutePath
                } else {
                    value
                }
                bind(statement, slot + 1, bound)
            }
            statement.executeInsert()
            statement.clearBindings()
        }
    }

    private fun bind(statement: SupportSQLiteStatement, index: Int, value: Any) {
        when (value) {
            JSONObject.NULL -> statement.bindNull(index)
            is Int -> statement.bindLong(index, value.toLong())
            is Long -> statement.bindLong(index, value)
            is Number -> statement.bindDouble(index, value.toDouble())
            is String -> statement.bindString(index, value)
            else -> error("unsupported value in snapshot: $value")
        }
    }

    /** Blocking is fine: TableSnapshot is only ever called from a background dispatcher. */
    private fun exportPrefs(files: MutableMap<String, String>, filesBySha: MutableMap<String, File>): JSONObject {
        val result = JSONObject()
        val store = prefsStore ?: return result
        val current = runBlocking { store.data.first() }.asMap()
        for ((key, value) in current.entries.sortedBy { it.key.name }) {
            val name = key.name
            if (name !in config.prefKeys) continue
            result.put(name, when (value) {
                is String -> JSONObject().put("s", if (name in config.filePrefKeys) exportPath(value, files, filesBySha) else value)
                is Boolean -> JSONObject().put("b", value)
                is Int -> JSONObject().put("i", value)
                is Long -> JSONObject().put("l", value)
                is Float -> JSONObject().put("f", value.toDouble())
                is Double -> JSONObject().put("d", value)
                is Set<*> -> JSONObject().put("ss", JSONArray(value.map { it as String }.sorted()))
                else -> error("preference $name has an unsupported type")
            })
        }
        return result
    }

    private fun restorePrefs(saved: JSONObject, files: Map<String, String>) {
        val store = prefsStore ?: return
        runBlocking {
            store.edit { prefs ->
                prefs.asMap().keys.filter { it.name in config.prefKeys }.forEach {
                    @Suppress("UNCHECKED_CAST")
                    prefs.remove(it as Preferences.Key<Any>)
                }
                for (name in saved.keys()) {
                    if (name !in config.prefKeys) continue
                    val v = saved.getJSONObject(name)
                    when {
                        v.has("s") -> prefs[stringPreferencesKey(name)] = v.getString("s").let {
                            if (name in config.filePrefKeys && it in files) File(filesDir, it).absolutePath else it
                        }
                        v.has("b") -> prefs[booleanPreferencesKey(name)] = v.getBoolean("b")
                        v.has("i") -> prefs[intPreferencesKey(name)] = v.getInt("i")
                        v.has("l") -> prefs[longPreferencesKey(name)] = v.getLong("l")
                        v.has("f") -> prefs[floatPreferencesKey(name)] = v.getDouble("f").toFloat()
                        v.has("d") -> prefs[doublePreferencesKey(name)] = v.getDouble("d")
                        v.has("ss") -> prefs[stringSetPreferencesKey(name)] = v.getJSONArray("ss").toStringList().toSet()
                    }
                }
            }
        }
    }

    companion object {
        /** Bumped whenever the envelope's shape changes. */
        const val FORMAT = 1
    }
}
