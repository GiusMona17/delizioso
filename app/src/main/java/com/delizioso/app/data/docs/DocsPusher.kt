package com.delizioso.app.data.docs

import com.delizioso.app.data.sync.PushResult
import com.delizioso.app.data.sync.SnapshotStore
import com.delizioso.app.data.sync.SnapsyncException
import com.delizioso.app.data.sync.sha256Of
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sends the documents to the Pi as a snapshot of a second snapsync app: every file is a blob named by its hash (a file
 * the Pi already has is not sent again) and the snapshot maps each path to its hash. The server needs nothing new.
 */
class DocsPusher(
    private val store: SnapshotStore,
    private val workDir: File,
) {
    /** Number of blobs that had to be uploaded. */
    suspend fun push(files: Map<String, String>): Int {
        val bytes = files.mapValues { it.value.toByteArray(Charsets.UTF_8) }
        val shaByPath = bytes.mapValues { sha256Of(it.value) }
        val byBlob = bytes.entries.associate { shaByPath.getValue(it.key) to it.value }
        val body = envelope(shaByPath).toByteArray(Charsets.UTF_8)

        var uploaded = 0
        repeat(MAX_ATTEMPTS) {
            for (sha in store.missing(byBlob.keys)) {
                val file = File(workDir, sha).also { it.parentFile?.mkdirs() }
                try {
                    file.writeBytes(byBlob.getValue(sha))
                    store.uploadBlob(sha, file)
                    uploaded++
                } finally {
                    file.delete()
                }
            }
            when (store.push(body, store.head())) {
                is PushResult.Created -> return uploaded
                PushResult.Stale, PushResult.MissingBlobs -> Unit // the Pi moved on or lost a blob: look again
            }
        }
        throw SnapsyncException("il Pi non ha accettato i documenti, riprova")
    }

    companion object {
        const val APP = "delizioso-docs"
        private const val MAX_ATTEMPTS = 3

        /** The snapshot body, in the same shape as the main snapshot: the server reads only the `blobs` list. */
        fun envelope(shaByPath: Map<String, String>): String {
            val paths = JSONObject()
            shaByPath.toSortedMap().forEach { (path, sha) -> paths.put(path, sha) }
            return JSONObject()
                .put("format", "delizioso-docs/1")
                .put("schema", 1)
                .put("blobs", JSONArray(shaByPath.values.toSortedSet().toList()))
                .put("data", JSONObject().put("files", paths))
                .toString()
        }

        /**
         * What identifies the content of a delivery, to skip one that would change nothing. The manifest is left out
         * because it carries the time of generation.
         */
        fun contentHash(files: Map<String, String>): String =
            sha256Of(
                files.filterKeys { it != "manifest.json" }.toSortedMap().entries
                    .joinToString("\n") { "${it.key}=${sha256Of(it.value.toByteArray(Charsets.UTF_8))}" }
                    .toByteArray(Charsets.UTF_8),
            )
    }
}
