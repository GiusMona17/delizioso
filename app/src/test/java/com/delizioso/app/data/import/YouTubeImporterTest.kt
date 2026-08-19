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

class YouTubeImporterTest {

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
    fun `extracts description and title from YouTube Data API response`() = runTest {
        val responseBody = """
            {
                "items": [
                    {
                        "snippet": {
                            "title": "Perfect Carbonara",
                            "description": "Ingredients:\n- 200g Guanciale\n- 4 Egg Yolks\n- Pecorino Romano\n\nSteps:\n1. Crisp the guanciale",
                            "channelTitle": "Italian Cooking",
                            "thumbnails": {
                                "high": { "url": "https://img.youtube.com/vi/123/hqdefault.jpg" }
                            }
                        }
                    }
                ]
            }
        """.trimIndent()

        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(responseBody))
        val importer = YouTubeImporter(
            apiKeyProvider = { "fake-key" },
            client = ImportHttp.client,
            json = json,
            apiBaseUrl = server.url("/videos").toString(),
        )

        val result = importer.fetch("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals(Platform.YOUTUBE, importer.platform)
        assertEquals("Italian Cooking", result.author)
        assertEquals("https://img.youtube.com/vi/123/hqdefault.jpg", result.thumbnailUrl)

        val content = result.content as ImportContent.RawText
        assertEquals("Perfect Carbonara", content.title)
        assertEquals(
            "Ingredients:\n- 200g Guanciale\n- 4 Egg Yolks\n- Pecorino Romano\n\nSteps:\n1. Crisp the guanciale",
            content.text,
        )

        val req = server.takeRequest()
        assertEquals("/videos", req.path?.substringBefore("?"))
    }

    @Test
    fun `throws when API key is not configured`() = runTest {
        val importer = YouTubeImporter(
            apiKeyProvider = { "" },
            client = ImportHttp.client,
            json = json,
            apiBaseUrl = server.url("/videos").toString(),
        )

        val e = assertThrows(ImportException::class.java) {
            kotlinx.coroutines.runBlocking { importer.fetch("https://www.youtube.com/watch?v=dQw4w9WgXcQ") }
        }
        assertEquals(false, e.retryable)
    }

    @Test
    fun `throws when video description is empty`() = runTest {
        val responseBody = """
            {
                "items": [
                    {
                        "snippet": {
                            "title": "No Description Video",
                            "description": "   ",
                            "channelTitle": "Channel"
                        }
                    }
                ]
            }
        """.trimIndent()

        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(responseBody))
        val importer = YouTubeImporter(
            apiKeyProvider = { "fake-key" },
            client = ImportHttp.client,
            json = json,
            apiBaseUrl = server.url("/videos").toString(),
        )

        val e = assertThrows(ImportException::class.java) {
            kotlinx.coroutines.runBlocking { importer.fetch("https://www.youtube.com/watch?v=dQw4w9WgXcQ") }
        }
        assertEquals(false, e.retryable)
    }

    @Test
    fun `throws on http error from API`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"message":"Quota exceeded"}}"""))
        val importer = YouTubeImporter(
            apiKeyProvider = { "fake-key" },
            client = ImportHttp.client,
            json = json,
            apiBaseUrl = server.url("/videos").toString(),
        )

        val e = assertThrows(ImportException::class.java) {
            kotlinx.coroutines.runBlocking { importer.fetch("https://www.youtube.com/watch?v=dQw4w9WgXcQ") }
        }
        assertEquals(true, e.retryable)
    }
}
