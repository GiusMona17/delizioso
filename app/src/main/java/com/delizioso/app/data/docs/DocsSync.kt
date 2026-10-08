package com.delizioso.app.data.docs

import com.delizioso.app.data.sync.SnapshotStore
import com.delizioso.app.data.sync.SyncSettings
import com.delizioso.app.data.sync.SyncSettingsRepository
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface DocsOutcome {
    /** The switch in Profile is off. */
    data object Disabled : DocsOutcome

    /** No server address or token yet: the documents use the same connection as the main sync. */
    data object NotConfigured : DocsOutcome

    /** A delivery was attempted less than a minute ago. */
    data object Throttled : DocsOutcome

    /** Nothing changed since the last delivery. */
    data object UpToDate : DocsOutcome
    data class Pushed(val files: Int, val uploaded: Int) : DocsOutcome
    data class Failed(val message: String) : DocsOutcome
}

/**
 * Builds the documents and delivers them to the Pi after a sync, when the owner switched them on. One way: nothing is
 * ever read back. It never throws, so a problem with the documents cannot fail the main sync that calls it.
 */
class DocsSync(
    private val source: DeliziosoDocsSource,
    private val connection: SyncSettingsRepository,
    private val settings: DocsSettingsRepository,
    private val storeFor: (SyncSettings) -> SnapshotStore,
    private val workDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var lastAttemptMillis = Long.MIN_VALUE / 2

    /** [force] skips the one-minute pause and the "nothing changed" check (the "send now" button). */
    suspend fun run(force: Boolean = false): DocsOutcome = mutex.withLock {
        val docs = settings.current()
        if (!docs.enabled) return@withLock DocsOutcome.Disabled
        val server = connection.current()
        if (!server.isConfigured) return@withLock DocsOutcome.NotConfigured
        val now = clock()
        if (!force && now - lastAttemptMillis < MIN_INTERVAL_MS) return@withLock DocsOutcome.Throttled
        lastAttemptMillis = now
        try {
            val input = source.load()
            val files = withContext(Dispatchers.Default) { DeliziosoDocsBuilder.build(input) }
            // agenda.json carries the generation time too, so the hash is taken from a build with a fixed one.
            val hash = DocsPusher.contentHash(DeliziosoDocsBuilder.build(input.copy(generatedAt = Instant.EPOCH)))
            if (!force && hash == docs.lastHash) return@withLock DocsOutcome.UpToDate
            val uploaded = DocsPusher(storeFor(server), workDir).push(files)
            settings.recordPushed(hash, clock())
            DocsOutcome.Pushed(files.size, uploaded)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            settings.recordError(message)
            DocsOutcome.Failed(message)
        }
    }

    companion object {
        const val MIN_INTERVAL_MS = 60_000L
    }
}
