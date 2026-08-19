package com.delizioso.app.data.import

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BlogImporterTest {

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
    fun `extracts structured recipe from JSON-LD`() = runTest {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:site_name" content="Tasty Kitchen" />
                <script type="application/ld+json">
                {
                    "@context": "https://schema.org",
                    "@type": "Recipe",
                    "name": "Classic Tiramisu",
                    "recipeIngredient": ["500g mascarpone", "4 eggs", "savoiardi"],
                    "recipeInstructions": ["Whisk yolks", "Layer with coffee-soaked biscuits"]
                }
                </script>
            </head>
            <body>
                <main>Some main content</main>
            </body>
            </html>
        """.trimIndent()

        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(html))
        val importer = BlogImporter(client = ImportHttp.client, json = json)
        val result = importer.fetch(server.url("/recipe/1").toString())

        assertEquals(Platform.BLOG, importer.platform)
        assertEquals("Tasty Kitchen", result.author)
        val structured = result.content as ImportContent.Structured
        assertEquals("Classic Tiramisu", structured.recipe.title)
        assertEquals(3, structured.recipe.ingredients.size)
        assertEquals(2, structured.recipe.steps.size)
    }

    @Test
    fun `falls back to readability text when JSON-LD is missing`() = runTest {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Grandma's Pasta</title>
                <meta property="og:site_name" content="Family Recipes" />
            </head>
            <body>
                <header>Header content to drop</header>
                <main>
                    <h1>Grandma's Pasta</h1>
                    <p>Ingredients: 400g flour, 4 eggs.</p>
                    <p>Instructions: Knead dough and roll thin.</p>
                </main>
                <footer>Footer content to drop</footer>
            </body>
            </html>
        """.trimIndent()

        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(html))
        val importer = BlogImporter(client = ImportHttp.client, json = json)
        val result = importer.fetch(server.url("/pasta").toString())

        assertEquals(Platform.BLOG, importer.platform)
        assertEquals("Family Recipes", result.author)
        val raw = result.content as ImportContent.RawText
        assertEquals("Grandma's Pasta", raw.title)
        assertTrue(raw.text.contains("Grandma's Pasta"))
        assertTrue(raw.text.contains("Ingredients: 400g flour, 4 eggs."))
    }

    @Test
    fun `throws on non-2xx http response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val importer = BlogImporter(client = ImportHttp.client, json = json)

        val e = assertThrows(ImportException::class.java) {
            kotlinx.coroutines.runBlocking { importer.fetch(server.url("/notfound").toString()) }
        }
        assertEquals(true, e.retryable)
    }

    @Test
    fun `throws on empty page body`() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(""))
        val importer = BlogImporter(client = ImportHttp.client, json = json)

        val e = assertThrows(ImportException::class.java) {
            kotlinx.coroutines.runBlocking { importer.fetch(server.url("/empty").toString()) }
        }
        assertEquals(true, e.retryable)
    }
}
