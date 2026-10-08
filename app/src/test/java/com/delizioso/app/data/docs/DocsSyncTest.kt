package com.delizioso.app.data.docs

import com.delizioso.app.PlainTestApplication
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.delizioso.app.data.sync.DeliziosoSyncConfig
import com.delizioso.app.data.sync.FakeSnapshotStore
import com.delizioso.app.data.sync.PushResult
import com.delizioso.app.data.sync.SnapshotStore
import com.delizioso.app.data.sync.SnapsyncException
import com.delizioso.app.data.sync.SyncSettingsRepository
import com.delizioso.app.data.sync.newPrefs
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = PlainTestApplication::class)
class DocsSyncTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val pi = FakeSnapshotStore()
    private var input = DeliziosoDocsInput(
        LocalDate.of(2026, 10, 8), Instant.parse("2026-10-08T10:00:00Z"), "Europe/Rome",
        emptyList(), emptyList(), emptyList(),
    )
    private var loads = 0
    private var now = 1_000_000L
    private lateinit var connection: SyncSettingsRepository
    private lateinit var settings: DocsSettingsRepository

    @Before
    fun setUp() {
        connection = SyncSettingsRepository(newPrefs(File(tmp.root, "sync.preferences_pb")))
        settings = DocsSettingsRepository(newPrefs(File(tmp.root, "docs.preferences_pb")))
        runBlocking { connection.setServer("https://pi", "token") }
    }

    private fun docs(store: SnapshotStore = pi) = DocsSync(
        source = { loads++; input },
        connection = connection,
        settings = settings,
        storeFor = { store },
        workDir = File(tmp.root, "docs-sync"),
        clock = { now },
    )

    private fun enable() = runBlocking { settings.setEnabled(true) }

    @Test
    fun `nothing happens while the switch is off`() = runBlocking {
        assertEquals(DocsOutcome.Disabled, docs().run())
        assertEquals(0, loads)
        assertNull(pi.body)
    }

    @Test
    fun `nothing happens without a server and a token`() = runBlocking {
        enable()
        connection.setServer("", "")

        assertEquals(DocsOutcome.NotConfigured, docs().run())
    }

    @Test
    fun `the first run delivers the three documents`() = runBlocking {
        enable()

        val outcome = docs().run()

        assertTrue(outcome is DocsOutcome.Pushed)
        assertEquals(3, (outcome as DocsOutcome.Pushed).files)
        assertNotNull(pi.body)
        assertNull(settings.current().lastError)
    }

    @Test
    fun `a second run with the same content sends nothing, even with a new generation time`() = runBlocking {
        enable()
        val sync = docs()
        sync.run()
        now += DocsSync.MIN_INTERVAL_MS + 1
        input = input.copy(generatedAt = Instant.parse("2026-10-08T11:00:00Z"))

        assertEquals(DocsOutcome.UpToDate, sync.run())
    }

    @Test
    fun `a changed day is sent again`() = runBlocking {
        enable()
        val sync = docs()
        sync.run()
        now += DocsSync.MIN_INTERVAL_MS + 1
        input = input.copy(today = LocalDate.of(2026, 10, 9))

        assertTrue(sync.run() is DocsOutcome.Pushed)
    }

    @Test
    fun `two runs less than a minute apart are throttled, force skips the pause`() = runBlocking {
        enable()
        val sync = docs()
        sync.run()
        now += 1_000

        assertEquals(DocsOutcome.Throttled, sync.run())
        assertTrue(sync.run(force = true) is DocsOutcome.Pushed)
    }

    @Test
    fun `a failure is recorded and returned, never thrown`() = runBlocking {
        enable()
        val broken = object : SnapshotStore by pi {
            override suspend fun push(body: ByteArray, baseEtag: String?): PushResult = throw SnapsyncException("pi spento")
        }

        val outcome = docs(broken).run()

        assertEquals(DocsOutcome.Failed("pi spento"), outcome)
        assertEquals("pi spento", settings.current().lastError)
    }

    @Test
    fun `the documents settings are not part of the synced preferences`() {
        assertTrue(DeliziosoSyncConfig.prefKeys.none { it.startsWith("docs_") })
    }
}
