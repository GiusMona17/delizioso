package com.delizioso.app.data.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QuickMealJsonParserTest {

    @Test
    fun `parse valid json object`() {
        val json = """{"name":"Latte e 6 biscotti","caloriesKcal":380,"proteinG":14,"fatG":12,"carbsG":54}"""
        val result = QuickMealJsonParser.parse(json)
        assertNotNull(result)
        assertEquals("Latte e 6 biscotti", result?.name)
        assertEquals(380.0, result?.caloriesKcal)
        assertEquals(14.0, result?.proteinG)
        assertEquals(12.0, result?.fatG)
        assertEquals(54.0, result?.carbsG)
    }

    @Test
    fun `parse markdown fenced json with numeric strings`() {
        val text = """
            Here is your estimate:
            ```json
            {
               "name": "Frozen Cod with Olive Oil",
               "caloriesKcal": "250.5",
               "proteinG": "38",
               "fatG": "10",
               "carbsG": "2"
            }
            ```
        """.trimIndent()
        val result = QuickMealJsonParser.parse(text)
        assertNotNull(result)
        assertEquals("Frozen Cod with Olive Oil", result?.name)
        assertEquals(250.5, result?.caloriesKcal)
        assertEquals(38.0, result?.proteinG)
        assertEquals(10.0, result?.fatG)
        assertEquals(2.0, result?.carbsG)
    }

    @Test
    fun `parse invalid text returns null`() {
        assertNull(QuickMealJsonParser.parse("I don't know what to calculate"))
    }
}
