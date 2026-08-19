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

    @Test
    fun `strips leading italian and english prepositions from name`() {
        val ing1 = IngredientParser.split("300 g di farina 00")
        assertEquals("300", ing1.quantity)
        assertEquals("g", ing1.unit)
        assertEquals("farina 00", ing1.name)

        val ing2 = IngredientParser.split("2 spicchi d'aglio")
        assertEquals("2", ing2.quantity)
        assertEquals("spicchi", ing2.unit)
        assertEquals("aglio", ing2.name)

        val ing3 = IngredientParser.split("1 cucchiaio d’olio extravergine")
        assertEquals("1", ing3.quantity)
        assertEquals("cucchiaio", ing3.unit)
        assertEquals("olio extravergine", ing3.name)

        val ing4 = IngredientParser.split("2 cups of flour")
        assertEquals("2", ing4.quantity)
        assertEquals("cups", ing4.unit)
        assertEquals("flour", ing4.name)

        val ing5 = IngredientParser.split("1 pizzico di sale")
        assertEquals("1", ing5.quantity)
        assertEquals("pizzico", ing5.unit)
        assertEquals("sale", ing5.name)
    }

    @Test
    fun `does not strip words starting with d that are part of the name`() {
        val ing1 = IngredientParser.split("1 sprig fresh dill")
        assertEquals("1", ing1.quantity)
        assertEquals("dill", ing1.name.substringAfterLast(" "))

        val ing2 = IngredientParser.split("100 g dates")
        assertEquals("100", ing2.quantity)
        assertEquals("g", ing2.unit)
        assertEquals("dates", ing2.name)
    }
}
