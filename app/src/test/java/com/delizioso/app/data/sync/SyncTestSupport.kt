package com.delizioso.app.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.delizioso.app.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

fun newDeliziosoDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

fun newPrefs(file: File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { file },
    )

/**
 * One row with a value in every column of every synced table. A foreign-key column takes
 * the value its parent row got (parents are seeded first, in config order); every file
 * column points at a real file under [filesDir]. Schema-driven: no entity knowledge needed.
 */
fun seedEveryColumn(db: RoomDatabase, config: SyncConfig, filesDir: File, marker: String) {
    val sqlite = db.openHelper.writableDatabase
    val seeded = mutableMapOf<Pair<String, String>, Any>()
    for (table in config.tables) {
        val columns = sqlite.query("PRAGMA table_info(`$table`)").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(1) to c.getString(2)) }
        }
        // foreign_key_list columns: id, seq, table, from, to, …
        val parents = sqlite.query("PRAGMA foreign_key_list(`$table`)").use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(3), c.getString(2) to c.getString(4)) }
        }
        val values = columns.map { (name, type) ->
            val parent = parents[name]
            when {
                parent != null -> seeded.getValue(parent)
                name in config.fileColumns[table].orEmpty() ->
                    File(filesDir, "$table/$name-$marker.jpg").apply {
                        parentFile!!.mkdirs()
                        writeText("bytes of $table.$name $marker")
                    }.absolutePath
                type.equals("INTEGER", ignoreCase = true) -> 1L
                type.equals("REAL", ignoreCase = true) -> 1.5
                else -> "$marker-$name"
            }
        }
        sqlite.execSQL(
            "INSERT INTO `$table` (${columns.joinToString { "`${it.first}`" }}) " +
                "VALUES (${columns.joinToString { "?" }})",
            values.toTypedArray(),
        )
        columns.zip(values).forEach { (column, value) -> seeded[table to column.first] = value }
    }
}
