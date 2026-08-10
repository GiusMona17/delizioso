package com.delizioso.app.data.import

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class TikTokImporterTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `extracts caption from oEmbed title`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"title":"Scramble up ur name 😍 #foryou","author_name":"chef","author_unique_id":"chef_kitchen"}"""
                )
        )
        val importer = TikTokImporter(client = ImportHttp.client, json = json, oEmbedBase = server.url("/").toString().trimEnd('/'))
        val result = importer.fetch("https://www.tiktok.com/@chef/video/123")
        assertEquals(Platform.TIKTOK, importer.platform)
        assertEquals("chef_kitchen", result.author)
        val content = result.content as ImportContent.RawText
        assertEquals("Scramble up ur name 😍 #foryou", content.text)
        // Sanity: the oEmbed endpoint URL was requested.
        assertEquals("/oembed", server.takeRequest().path?.substringBefore("?"))
    }

    @Test
    fun `carries the oEmbed thumbnail through`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"title":"Udon","author_unique_id":"chef","thumbnail_url":"https://p16.tiktokcdn.com/cover.jpg"}"""
                )
        )
        val importer = TikTokImporter(client = ImportHttp.client, json = json, oEmbedBase = server.url("/").toString().trimEnd('/'))
        val result = importer.fetch("https://www.tiktok.com/@chef/video/123")
        assertEquals("https://p16.tiktokcdn.com/cover.jpg", result.thumbnailUrl)
    }

    @Test
    fun `throws when caption missing`() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"title":"  "}"""))
        val importer = TikTokImporter(client = ImportHttp.client, json = json, oEmbedBase = server.url("/").toString().trimEnd('/'))
        assertThrows(ImportException::class.java) {
            kotlinx.coroutines.runBlocking { importer.fetch("https://www.tiktok.com/@chef/video/123") }
        }
    }

    @Test
    fun `throws on http error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        val importer = TikTokImporter(client = ImportHttp.client, json = json, oEmbedBase = server.url("/").toString().trimEnd('/'))
        val e = assertThrows(ImportException::class.java) {
            kotlinx.coroutines.runBlocking { importer.fetch("https://www.tiktok.com/@chef/video/123") }
        }
        assertEquals(true, e.retryable)
    }
}
