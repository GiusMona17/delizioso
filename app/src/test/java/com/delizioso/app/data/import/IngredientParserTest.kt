package com.delizioso.app.data.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IngredientParserTest {

    @Test
    fun `splits quantity unit and name`() {
        val ing = IngredientParser.split("2 cups all-purpose flour")
        assertEquals("2", ing.quantity)
        assertEquals("cups", ing.unit)
        assertEquals("all-purpose flour", ing.name)
        assertEquals("2 cups all-purpose flour", ing.rawText)
    }

    @Test
    fun `splits fraction quantities`() {
        val ing = IngredientParser.split("1/2 tsp salt")
        assertEquals("1/2", ing.quantity)
        assertEquals("tsp", ing.unit)
        assertEquals("salt", ing.name)
    }

    @Test
    fun `no unit leaves name only`() {
        val ing = IngredientParser.split("2 eggs")
        assertEquals("2", ing.quantity)
        assertNull(ing.unit)
        assertEquals("eggs", ing.name)
    }

    @Test
    fun `plain line falls back to raw text`() {
        val ing = IngredientParser.split("a pinch of love")
        assertNull(ing.quantity)
        assertNull(ing.unit)
        assertEquals("a pinch of love", ing.name)
    }
}
