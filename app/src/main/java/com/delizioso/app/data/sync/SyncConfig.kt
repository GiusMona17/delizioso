package com.delizioso.app.data.sync

/**
 * What one app syncs. Tables not listed, and every DataStore preference, stay on the device.
 */
data class SyncConfig(
    /** Path segment on the server: /v1/<appName>/… */
    val appName: String,
    /** Synced tables, parents before children: restore inserts in this order. */
    val tables: List<String>,
    /** Table -> columns holding absolute paths of files under filesDir that travel with the row. */
    val fileColumns: Map<String, Set<String>>,
    /** Tables deliberately not synced. With [tables] this must cover the whole schema (tested). */
    val unsyncedTables: Set<String>,
    /** DataStore keys that are user data (a name, a profile photo), synced with the tables. */
    val prefKeys: Set<String> = emptySet(),
    /** The subset of [prefKeys] whose values are absolute paths of files under filesDir. */
    val filePrefKeys: Set<String> = emptySet(),
)
