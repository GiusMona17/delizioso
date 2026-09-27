package com.delizioso.app.data.sync

import java.io.File
import java.io.IOException

/** The Pi side of sync. [SnapsyncClient] talks HTTP to snapsync; tests use an in-memory fake. */
interface SnapshotStore {
    /** The current ETag, or null when nothing was ever pushed for this app. */
    suspend fun head(): String?

    /** The current ETag and snapshot bytes. */
    suspend fun download(): Pair<String, ByteArray>

    /** Which of [hashes] the Pi doesn't have yet. */
    suspend fun missing(hashes: Collection<String>): Set<String>

    suspend fun uploadBlob(sha: String, file: File)

    /** Writes the blob to [target], then verifies its sha256 (a mismatch deletes it and throws). */
    suspend fun downloadBlob(sha: String, target: File)

    /** [baseEtag] null creates the first snapshot; otherwise the Pi must still be at [baseEtag]. */
    suspend fun push(body: ByteArray, baseEtag: String?): PushResult
}

sealed interface PushResult {
    data class Created(val etag: String) : PushResult

    /** The Pi moved on since [baseEtag]: decide again. */
    data object Stale : PushResult

    /** A referenced blob vanished (GC race): upload again. */
    data object MissingBlobs : PushResult
}

class SnapsyncException(message: String) : IOException(message)
