package com.delizioso.app.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.delizioso.app.PlainTestApplication
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Robolectric, not plain JVM: the client uses org.json, which is only a throwing stub in android.jar. */
@RunWith(AndroidJUnit4::class)
@Config(application = PlainTestApplication::class)
class SnapsyncClientTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var client: SnapsyncClient

    @Before
    fun start() {
        server.start()
        client = SnapsyncClient(okhttp3.OkHttpClient(), server.url("/").toString(), "secret", "delizioso")
    }

    @After
    fun stop() = server.shutdown()

    @Test
    fun `head maps 404 to null and 200 to the etag, with auth`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setHeader("ETag", "\"1-abc\""))
        assertNull(client.head())
        assertEquals("\"1-abc\"", client.head())
        val request = server.takeRequest()
        assertEquals("HEAD", request.method)
        assertEquals("/v1/delizioso/snapshot", request.path)
        assertEquals("Bearer secret", request.getHeader("Authorization"))
    }

    @Test
    fun `first push sends If-None-Match, later pushes send If-Match, body intact`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setHeader("ETag", "\"1-a\""))
        server.enqueue(MockResponse().setResponseCode(201).setHeader("ETag", "\"2-b\""))
        assertEquals(PushResult.Created("\"1-a\""), client.push("""{"x":1}""".toByteArray(), null))
        assertEquals(PushResult.Created("\"2-b\""), client.push("{}".toByteArray(), "\"1-a\""))
        val first = server.takeRequest()
        assertEquals("*", first.getHeader("If-None-Match"))
        assertEquals("""{"x":1}""", first.body.readUtf8())
        assertEquals("\"1-a\"", server.takeRequest().getHeader("If-Match"))
    }

    @Test
    fun `412 is Stale and 422 is MissingBlobs`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(412))
        server.enqueue(MockResponse().setResponseCode(422))
        assertEquals(PushResult.Stale, client.push("{}".toByteArray(), "\"1-a\""))
        assertEquals(PushResult.MissingBlobs, client.push("{}".toByteArray(), "\"1-a\""))
    }

    @Test
    fun `missing asks only when there is something to ask`() = runBlocking {
        assertEquals(emptySet<String>(), client.missing(emptyList()))
        assertEquals(0, server.requestCount)
        server.enqueue(MockResponse().setBody("""{"missing":["b"]}"""))
        assertEquals(setOf("b"), client.missing(listOf("a", "b")))
        assertEquals("""{"hashes":["a","b"]}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun `blob upload streams the file`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))
        val file = tmp.newFile().apply { writeText("photo bytes") }
        client.uploadBlob("abc", file)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/v1/delizioso/blobs/abc", request.path)
        assertEquals("photo bytes", request.body.readUtf8())
    }

    @Test
    fun `a corrupt blob download throws and leaves no file`() {
        server.enqueue(MockResponse().setBody("tampered"))
        val target = File(tmp.root, "blob")
        assertThrows(SnapsyncException::class.java) {
            runBlocking { client.downloadBlob(sha256Of("real".toByteArray()), target) }
        }
        assertFalse(target.exists())
    }

    @Test
    fun `server errors become SnapsyncException`() {
        server.enqueue(MockResponse().setResponseCode(500))
        assertThrows(SnapsyncException::class.java) { runBlocking { client.head() } }
    }
}
