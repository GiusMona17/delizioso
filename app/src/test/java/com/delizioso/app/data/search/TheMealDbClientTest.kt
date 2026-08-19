package com.delizioso.app.data.search

import com.delizioso.app.data.import.ImportException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TheMealDbClientTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun client() = TheMealDbClient(baseUrl = server.url("/").toString())

    private fun respond(body: String) = server.enqueue(
        MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    )

    @Test
    fun `searching by name returns the meals and asks the right endpoint`() = runTest {
        respond("""{"meals":[{"idMeal":"1","strMeal":"Penne"}]}""")
        val meals = client().searchByName("penne arrabiata")
        assertEquals(1, meals.size)
        assertEquals("1", MealDbMapper.mealId(meals.first()))
        val request = server.takeRequest()
        assertEquals("/search.php", request.path?.substringBefore("?"))
        assertTrue(request.path!!.contains("s=penne%20arrabiata") || request.path!!.contains("s=penne+arrabiata"))
    }

    /** TheMealDB answers a miss with {"meals":null}, not an empty array. */
    @Test
    fun `no results is an empty list, not an error`() = runTest {
        respond("""{"meals":null}""")
        assertTrue(client().searchByName("nothing").isEmpty())
    }

    @Test
    fun `lookup returns the single meal`() = runTest {
        respond("""{"meals":[{"idMeal":"52771","strMeal":"Penne"}]}""")
        assertEquals("52771", MealDbMapper.mealId(client().lookup("52771")!!))
    }

    @Test
    fun `lookup of an unknown id is null`() = runTest {
        respond("""{"meals":null}""")
        assertNull(client().lookup("999999"))
    }

    @Test
    fun `an http error becomes an ImportException the UI can show`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        try {
            client().searchByName("x")
            fail("Expected ImportException")
        } catch (e: ImportException) {
            // Expected
        }
    }

    @Test
    fun `malformed json becomes an ImportException rather than a crash`() = runTest {
        respond("not json at all")
        try {
            client().searchByName("x")
            fail("Expected ImportException")
        } catch (e: ImportException) {
            // Expected
        }
    }

    @Test
    fun `ingredient names come back sorted and without blanks`() = runTest {
        respond("""{"meals":[{"strIngredient":"Salmon"},{"strIngredient":" "},{"strIngredient":"Beef"}]}""")
        assertEquals(listOf("Beef", "Salmon"), client().ingredientNames())
    }

    /** TheMealDB wants underscores where the ingredient name has spaces. */
    @Test
    fun `filtering by ingredient sends underscores and maps the tile fields`() = runTest {
        respond("""{"meals":[{"idMeal":"7","strMeal":"Chicken Pie","strMealThumb":"https://x/y.jpg"}]}""")
        val results = client().mealsWithIngredient("chicken breast")
        assertEquals(listOf(TheMealDbClient.SearchResult("7", "Chicken Pie", "https://x/y.jpg")), results)
        val path = server.takeRequest().path!!
        assertEquals("/filter.php", path.substringBefore("?"))
        assertTrue(path.contains("i=chicken_breast"))
    }

    @Test
    fun `intersecting keeps only meals present for every ingredient`() {
        val a = TheMealDbClient.SearchResult("1", "A", null)
        val b = TheMealDbClient.SearchResult("2", "B", null)
        val c = TheMealDbClient.SearchResult("3", "C", null)
        assertEquals(
            listOf(b),
            TheMealDbClient.intersect(listOf(listOf(a, b), listOf(b, c))),
        )
    }

    @Test
    fun `intersecting one list returns it unchanged, and no lists returns nothing`() {
        val a = TheMealDbClient.SearchResult("1", "A", null)
        assertEquals(listOf(a), TheMealDbClient.intersect(listOf(listOf(a))))
        assertTrue(TheMealDbClient.intersect(emptyList()).isEmpty())
    }

    /** Three ingredients often match nothing in a ~300 recipe catalogue. */
    @Test
    fun `an empty intersection is empty, not the first list`() {
        val a = TheMealDbClient.SearchResult("1", "A", null)
        val b = TheMealDbClient.SearchResult("2", "B", null)
        assertTrue(TheMealDbClient.intersect(listOf(listOf(a), listOf(b))).isEmpty())
    }
}
