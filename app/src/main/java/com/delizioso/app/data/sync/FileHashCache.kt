package com.delizioso.app.data.sync

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

val SHA256_RE = Regex("[0-9a-f]{64}")

fun sha256Of(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

fun sha256Of(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHex()
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/**
 * sha256 of files, remembered by (path, size, mtime), so syncing on every app stop doesn't
 * re-read every photo. Not thread-safe: SyncEngine serialises all use.
 * ponytail: a rewrite that keeps size and mtime identical goes unnoticed; hash by content if that ever bites.
 */
class FileHashCache(private val store: File) {
    private val known: MutableMap<String, String> = load()
    private val used = mutableMapOf<String, String>()

    fun sha256(file: File): String {
        val key = "${file.absolutePath}|${file.length()}|${file.lastModified()}"
        val sha = known[key] ?: sha256Of(file)
        used[key] = sha
        return sha
    }

    /** Persists only entries used since the last save, so deleted files drop out. */
    fun save() {
        store.parentFile?.mkdirs()
        store.writeText(JSONObject(used.toMap()).toString())
        known.clear()
        known.putAll(used)
        used.clear()
    }

    private fun load(): MutableMap<String, String> =
        runCatching { JSONObject(store.readText()).toStringMap().toMutableMap() }
            .getOrElse { mutableMapOf() }
}
