package com.delizioso.app.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream

sealed interface SyncOutcome {
    data object NotConfigured : SyncOutcome
    data object UpToDate : SyncOutcome

    /** The Pi has nothing for this app and neither does this device (e.g. restoring on a new phone too early). */
    data object NothingOnPi : SyncOutcome
    data object Pushed : SyncOutcome
    data object Pulled : SyncOutcome
    data object Conflict : SyncOutcome
    data object UpdateRequired : SyncOutcome
    data class Failed(val message: String) : SyncOutcome
}

/**
 * Runs the spec's decision table (§2) against the Pi. Every entry point takes the same
 * mutex, so a worker run, a UI tap and a manual import never overlap.
 */
class SyncEngine(
    private val codec: TableSnapshot,
    private val settings: SyncSettingsRepository,
    private val storeFor: (SyncSettings) -> SnapshotStore,
    private val workDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    suspend fun sync(): SyncOutcome = guarded { store ->
        if (settings.current().conflict) return@guarded SyncOutcome.Conflict
        repeat(MAX_ATTEMPTS) {
            val current = settings.current()
            val server = store.head()
            val local = codec.export()
            val changed = local.hash != current.lastPushedHash
            when (decide(server, current.baseEtag, changed, codec.isEmpty())) {
                SyncAction.NOTHING -> {
                    settings.recordChecked(clock())
                    return@guarded if (server == null) SyncOutcome.NothingOnPi else SyncOutcome.UpToDate
                }
                SyncAction.PUSH -> if (push(store, local, server)) return@guarded SyncOutcome.Pushed
                SyncAction.PULL -> return@guarded pull(store)
                SyncAction.CONFLICT -> {
                    settings.setConflict(true)
                    return@guarded SyncOutcome.Conflict
                }
            }
        }
        SyncOutcome.Failed("il Pi è cambiato durante la sincronizzazione, riprova")
    }

    /** Conflict resolution: this device's data wins; the Pi keeps its old version in history. */
    suspend fun resolveKeepLocal(): SyncOutcome = guarded { store ->
        val server = store.head()
        if (server != null && !understands(JSONObject(store.download().second.decodeToString()))) {
            return@guarded updateRequired()
        }
        if (push(store, codec.export(), server)) SyncOutcome.Pushed
        else SyncOutcome.Failed("il Pi è cambiato di nuovo, riprova")
    }

    /** Conflict resolution: the Pi's data wins; this device's data is kept in pre-restore.zip. */
    suspend fun resolveUsePi(): SyncOutcome = guarded { store ->
        workDir.mkdirs()
        File(workDir, PRE_RESTORE).outputStream().use { SnapshotArchive.write(codec.export(), it) }
        pull(store)
    }

    /** Manual backup: the same snapshot as a single zip. */
    suspend fun exportArchive(output: OutputStream) {
        mutex.withLock { withContext(Dispatchers.IO) { SnapshotArchive.write(codec.export(), output) } }
    }

    /** Manual restore: a full replace, like a pull. The next sync pushes it (or reports a conflict). */
    suspend fun importArchive(input: InputStream): SyncOutcome = mutex.withLock {
        withContext(Dispatchers.IO) {
            val staging = File(workDir, "import").apply { deleteRecursively() }
            try {
                val (envelope, staged) = SnapshotArchive.read(input, staging)
                if (!understands(envelope)) return@withContext SyncOutcome.UpdateRequired
                codec.restore(envelope, staged)
                SyncOutcome.Pulled
            } finally {
                staging.deleteRecursively()
            }
        }
    }

    /** True when the Pi accepted it; false when it moved on meanwhile (the caller decides again). */
    private suspend fun push(store: SnapshotStore, local: LocalSnapshot, base: String?): Boolean {
        for (sha in store.missing(local.filesBySha.keys)) store.uploadBlob(sha, local.filesBySha.getValue(sha))
        return when (val result = store.push(local.bytes, base)) {
            is PushResult.Created -> {
                settings.recordSynced(result.etag, local.hash, clock())
                true
            }
            PushResult.Stale, PushResult.MissingBlobs -> false
        }
    }

    private suspend fun pull(store: SnapshotStore): SyncOutcome {
        val (etag, bytes) = store.download()
        val envelope = JSONObject(bytes.decodeToString())
        if (!understands(envelope)) return updateRequired()
        val staging = File(workDir, "staging").apply { deleteRecursively(); mkdirs() }
        try {
            val local = codec.export()
            val staged = envelope.getJSONArray("blobs").toStringList().associateWith { sha ->
                File(staging, sha).also { target ->
                    val existing = local.filesBySha[sha]
                    if (existing != null) existing.copyTo(target, overwrite = true) else store.downloadBlob(sha, target)
                }
            }
            codec.restore(envelope, staged)
        } finally {
            staging.deleteRecursively()
        }
        settings.recordSynced(etag, codec.export().hash, clock())
        return SyncOutcome.Pulled
    }

    private fun understands(envelope: JSONObject): Boolean =
        envelope.optInt("format", Int.MAX_VALUE) <= TableSnapshot.FORMAT &&
            envelope.optInt("schema", Int.MAX_VALUE) <= codec.schemaVersion

    private suspend fun updateRequired(): SyncOutcome {
        settings.recordError("Aggiorna l'app: il Pi ha dati di una versione più recente")
        return SyncOutcome.UpdateRequired
    }

    private suspend fun guarded(block: suspend (SnapshotStore) -> SyncOutcome): SyncOutcome = mutex.withLock {
        val current = settings.current()
        if (!current.isConfigured) return@withLock SyncOutcome.NotConfigured
        try {
            withContext(Dispatchers.IO) { block(storeFor(current)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            settings.recordError(message)
            SyncOutcome.Failed(message)
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val PRE_RESTORE = "pre-restore.zip"
    }
}
