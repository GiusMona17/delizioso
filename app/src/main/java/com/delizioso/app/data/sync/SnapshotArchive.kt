package com.delizioso.app.data.sync

import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A snapshot as one file — snapshot.json plus blobs/<sha256> — for manual backups and pre-restore copies. */
object SnapshotArchive {
    private const val MANIFEST = "snapshot.json"
    private const val BLOB_DIR = "blobs/"

    fun write(snapshot: LocalSnapshot, output: OutputStream) {
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(snapshot.bytes)
            zip.closeEntry()
            for ((sha, file) in snapshot.filesBySha) {
                zip.putNextEntry(ZipEntry(BLOB_DIR + sha))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Extracts every blob into [stagingDir], verifying it against its name. */
    fun read(input: InputStream, stagingDir: File): Pair<JSONObject, Map<String, File>> {
        stagingDir.mkdirs()
        var envelope: JSONObject? = null
        val staged = mutableMapOf<String, File>()
        ZipInputStream(input.buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                when {
                    entry.name == MANIFEST -> envelope = JSONObject(zip.readBytes().decodeToString())
                    entry.name.startsWith(BLOB_DIR) && !entry.isDirectory -> {
                        val sha = entry.name.removePrefix(BLOB_DIR)
                        require(SHA256_RE.matches(sha)) { "not a snapshot archive: ${entry.name}" }
                        val target = File(stagingDir, sha)
                        target.outputStream().use { zip.copyTo(it) }
                        require(sha256Of(target) == sha) { "the backup file is damaged ($sha)" }
                        staged[sha] = target
                    }
                }
            }
        }
        return (envelope ?: throw IllegalArgumentException("not a snapshot archive")) to staged
    }
}
