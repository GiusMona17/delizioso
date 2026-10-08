package com.delizioso.app.data.docs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.delizioso.app.PlainTestApplication
import com.delizioso.app.data.sync.FakeSnapshotStore
import com.delizioso.app.data.sync.PushResult
import com.delizioso.app.data.sync.SnapshotStore
import com.delizioso.app.data.sync.SnapsyncException
import com.delizioso.app.data.sync.sha256Of
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = PlainTestApplication::class)
class DocsPusherTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val pi = FakeSnapshotStore()
    private val workDir get() = File(tmp.root, "docs-sync")
    private val files = mapOf("manifest.json" to "{}\n", "now.md" to "# Now\n", "agenda.json" to "{\"items\":[]}\n")

    private fun push(files: Map<String, String>) = runBlocking { DocsPusher(pi, workDir).push(files) }
    private fun sha(text: String) = sha256Of(text.toByteArray(Charsets.UTF_8))
    private fun delivered() = JSONObject(pi.body!!.decodeToString())

    @Test
    fun `every file is uploaded once, by its hash, and the snapshot maps each path to it`() {
        val uploaded = push(files)

        assertEquals(3, uploaded)
        assertEquals(files.values.map(::sha).toSet(), pi.blobs.keys)
        val mapped = delivered().getJSONObject("data").getJSONObject("files")
        assertEquals(files.mapValues { sha(it.value) }, mapped.keys().asSequence().associateWith { mapped.getString(it) })
    }

    @Test
    fun `the snapshot says what it is, in the shape the server reads`() {
        push(files)

        assertEquals("delizioso-docs/1", delivered().getString("format"))
        assertEquals(1, delivered().getInt("schema"))
        assertEquals(
            files.values.map(::sha).sorted(),
            delivered().getJSONArray("blobs").let { a -> (0 until a.length()).map { a.getString(it) } },
        )
    }

    @Test
    fun `a file the Pi already has is not uploaded again`() {
        push(files)

        assertEquals(1, push(files + ("now.md" to "# Changed\n")))
    }

    @Test
    fun `the same delivery twice uploads nothing the second time`() {
        push(files)

        assertEquals(0, push(files))
    }

    @Test
    fun `a stale base is retried with the Pi's new head`() {
        val flaky = object : SnapshotStore by pi {
            var first = true
            override suspend fun push(body: ByteArray, baseEtag: String?): PushResult {
                if (first) { first = false; return PushResult.Stale }
                return pi.push(body, baseEtag)
            }
        }

        runBlocking { DocsPusher(flaky, workDir).push(files) }

        assertTrue(pi.body != null)
    }

    @Test
    fun `a Pi that never accepts the snapshot is reported`() {
        val refusing = object : SnapshotStore by pi {
            override suspend fun push(body: ByteArray, baseEtag: String?) = PushResult.Stale
        }

        assertThrows(SnapsyncException::class.java) { runBlocking { DocsPusher(refusing, workDir).push(files) } }
    }

    @Test
    fun `no temporary file is left behind`() {
        push(files)

        assertEquals(emptyList<String>(), workDir.listFiles()?.map { it.name }.orEmpty())
    }

    @Test
    fun `the content hash ignores the manifest and the order of the files`() {
        val a = DocsPusher.contentHash(files + ("manifest.json" to "{\"generatedAt\":\"1\"}"))
        val b = DocsPusher.contentHash(files.entries.reversed().associate { it.key to it.value } + ("manifest.json" to "{\"generatedAt\":\"2\"}"))
        val c = DocsPusher.contentHash(files + ("now.md" to "# Different\n"))

        assertEquals(a, b)
        assertTrue(a != c)
    }
}
