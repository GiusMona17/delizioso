package com.delizioso.app.data.import

import com.delizioso.app.data.search.TheMealDbClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class TheMealDbImporterTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun `detects a themealdb recipe url and its id`() {
        assertEquals(Platform.MEALDB, PlatformDetector.detect("https://www.themealdb.com/meal/52771"))
        assertEquals("52771", PlatformDetector.mealDbId("https://www.themealdb.com/meal/52771"))
        assertEquals("52771", PlatformDetector.mealDbId("themealdb.com/meal/52771/spicy-penne"))
        assertNull(PlatformDetector.mealDbId("https://example.com/meal/52771"))
    }

    /** A blog URL must keep going to the blog importer. */
    @Test
    fun `an ordinary link is still a blog`() {
        assertEquals(Platform.BLOG, PlatformDetector.detect("https://ricette.giallozafferano.it/x.html"))
    }

    @Test
    fun `fetching returns a structured recipe, not raw text`() = runTest {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"meals":[{"idMeal":"52771","strMeal":"Penne","strInstructions":"Boil.",
                   "strIngredient1":"penne","strMeasure1":"1 pound"}]}"""
            )
        )
        val importer = TheMealDbImporter(TheMealDbClient(baseUrl = server.url("/").toString()))
        val raw = importer.fetch("https://www.themealdb.com/meal/52771")

        assertEquals(Platform.MEALDB, importer.platform)
        assertEquals("https://www.themealdb.com/meal/52771", raw.url)
        assertEquals("TheMealDB", raw.author)
        val recipe = (raw.content as ImportContent.Structured).recipe
        assertEquals("Penne", recipe.title)
        assertEquals(listOf("1 pound penne"), recipe.ingredients.map { it.rawText })
        assertEquals(listOf("Boil."), recipe.steps)
    }

    @Test
    fun `an unknown id fails with a message the screen can show`() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"meals":null}"""))
        val importer = TheMealDbImporter(TheMealDbClient(baseUrl = server.url("/").toString()))
        assertThrows(ImportException::class.java) {
            kotlinx.coroutines.runBlocking { importer.fetch("https://www.themealdb.com/meal/999999") }
        }
    }
}
