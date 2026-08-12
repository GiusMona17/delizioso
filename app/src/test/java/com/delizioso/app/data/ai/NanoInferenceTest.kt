package com.delizioso.app.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NanoInferenceTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `complete json is returned unchanged`() {
        val raw = """{"title":"Soup","steps":["Boil"]}"""
        assertEquals(raw, NanoInference.repairTruncatedJson(raw))
    }

    /** The real answer the Pixel produced: cut off mid-way through the last step. */
    @Test
    fun `salvages a recipe truncated inside the steps array`() {
        val raw = """
            {"title":"One Pot Rice Cooker Japanese Gyudon","servings":2,
             "ingredients":["10 oz beef","2/3 cups jasmine rice","2 tbsp soy sauce"],
             "steps":["1. Thinly slice the green onions.","2. Wash your rice with water.","3. Once your rice cooker is done, in a bowl add half of the gyudon, one boiled egg, and top
        """.trimIndent()

        val repaired = NanoInference.repairTruncatedJson(raw)
        assertNotNull(repaired)
        val obj = json.parseToJsonElement(repaired!!) as JsonObject

        assertEquals("One Pot Rice Cooker Japanese Gyudon", (obj["title"] as JsonPrimitive).content)
        // Every ingredient the model did emit survives.
        assertEquals(3, (obj["ingredients"] as JsonArray).size)
        // The half-written step is dropped, the complete ones are kept.
        assertEquals(2, (obj["steps"] as JsonArray).size)
    }

    @Test
    fun `salvages truncation between members`() {
        val raw = """{"title":"Pesto","description":"Fresh","ingredients":["basil","pine nuts"],"""
        val repaired = NanoInference.repairTruncatedJson(raw)
        val obj = json.parseToJsonElement(repaired!!) as JsonObject
        assertEquals("Pesto", (obj["title"] as JsonPrimitive).content)
        assertEquals(2, (obj["ingredients"] as JsonArray).size)
    }

    @Test
    fun `a brace inside a string does not confuse the scanner`() {
        val raw = """{"title":"Curly {} braces","ingredients":["a \"quoted\" thing","b"],"""
        val obj = json.parseToJsonElement(NanoInference.repairTruncatedJson(raw)!!) as JsonObject
        assertEquals("Curly {} braces", (obj["title"] as JsonPrimitive).content)
        assertEquals(2, (obj["ingredients"] as JsonArray).size)
    }

    @Test
    fun `nothing salvageable yields null`() {
        assertNull(NanoInference.repairTruncatedJson("I could not find a recipe."))
        // Cut off before any member completed — no partial recipe worth keeping.
        assertNull(NanoInference.repairTruncatedJson("""{"title":"Half a tit"""))
    }
}
